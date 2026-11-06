package com.kanaani.ibo.ui

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.kanaani.ibo.data.Category
import com.kanaani.ibo.data.Channel
import com.kanaani.ibo.data.Favorite
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Xtream
import com.kanaani.ibo.player.Playback

@Composable
fun LiveScreen(nav: Navigator, x: Xtream, s: Screen.Live) {
    val ctx = LocalContext.current
    val serverCategories by produceState<List<Category>?>(null, x) { value = x.liveCategories() }
    // Ordre choisi dans Réglages › Ordre des chaînes.
    val categories = remember(serverCategories, Store.categoryOrders, Store.categoryNames, Store.hiddenCategories, Store.language, com.kanaani.ibo.data.Translations.he) { serverCategories?.let { Store.arrange("live", it) } }
    var reload by remember { mutableIntStateOf(0) }
    val favs = Store.favorites
    // Les favoris ne relancent le chargement que si on est DANS la catégorie Favoris.
    val favKey: Any = if (s.category == Screen.FAVORITES) favs else Unit
    val channels by produceState<List<Channel>?>(null, s.category, reload, favKey, categories) {
        value = null
        value = when (val c = s.category) {
            null -> emptyList()
            Screen.FAVORITES -> favs.filter { it.kind == "live" }.mapIndexed { i, f -> Channel(f.id, f.name, f.image, i + 1, null) }
            Screen.ALL -> x.channels(null)
            else -> {
                val list = x.channels(c)
                // Catégorie israélienne : sport puis info, chaque chaîne de la qualité normale à la 4K.
                // Nom d'origine du serveur (pas celui choisi par l'utilisateur) pour reconnaître la catégorie.
                val name = serverCategories?.firstOrNull { it.id == c }?.name
                // Ordre des chaînes préparé dans le panneau, sinon tri automatique des chaînes israéliennes.
                val mine = com.kanaani.ibo.data.Layouts.channelOrder(c)
                when {
                    mine != null -> { val rank = mine.withIndex().associate { (i, v) -> v to i }; list.withIndex().sortedBy { rank[it.value.id] ?: (mine.size + it.index) }.map { it.value } }
                    com.kanaani.ibo.data.ChannelOrder.isIsraeli(name) -> com.kanaani.ibo.data.ChannelOrder.sortIsraeli(list)
                    else -> list
                }
            }
        }
    }
    val shown = remember(channels, s.filter) {
        val q = s.filter.trim()
        channels?.let { list -> if (q.isEmpty()) list else list.filter { it.name.contains(q, ignoreCase = true) } }
    }

    val chFocus = remember { FocusRequester() }
    val catFocus = remember { FocusRequester() }
    // Nouvelle catégorie chargée : le focus passe sur la liste des chaînes (pas pendant la saisie du filtre).
    // Ouverture : premier vrai bouquet sélectionné (ses chaînes s'affichent), focus sur lui.
    LaunchedEffect(categories) {
        val cats = categories ?: return@LaunchedEffect
        if (s.category == null) {
            // Rouvre sur la dernière chaîne regardée.
            val last = Store.lastLive()
            if (last != null && (last.first == Screen.ALL || last.first == Screen.FAVORITES || cats.any { it.id == last.first }) && !Store.isLocked("live", last.first)) {
                s.category = last.first; s.resumeChannel = last.second; s.focusIndex = 0
            } else cats.firstOrNull { !Store.isLocked("live", it.id) }?.let { s.category = it.id; s.focusIndex = 0 }
        }
    }
    LaunchedEffect(channels) {
        val list = shown ?: return@LaunchedEffect
        s.resumeChannel?.let { id ->
            s.resumeChannel = null
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) { s.focusIndex = i; s.playing = list[i]; Playback.play(ctx, x.liveUrl(list[i].id)) }
        }
        if (!s.opened) {
            if (s.category == null) return@LaunchedEffect // on attend le premier bouquet
            s.opened = true
            InitialFocusOnce(catFocus)
            return@LaunchedEffect
        }
        if (list.isEmpty()) { InitialFocusOnce(catFocus); return@LaunchedEffect }
        val i = s.focusIndex.coerceIn(0, list.lastIndex)
        runCatching { s.listState.scrollToItem(i) }
        InitialFocusOnce(chFocus)
    }

    fun select(c: Channel, list: List<Channel>, index: Int) {
        s.focusIndex = index
        if (s.playing?.id == c.id && Playback.current != null && !Playback.error) {
            s.listsOpen = false
        } else {
            s.playing = c
            s.category?.let { Store.rememberLive(it, c.id) }
            Playback.play(ctx, x.liveUrl(c.id))
        }
    }

    // Aura : la chaîne joue en plein écran DERRIÈRE ; catégories et chaînes numérotées par-dessus, semi-transparentes.
    val playingNow = s.playing != null && Playback.current != null
    var optionsFor by remember { mutableStateOf<Category?>(null) }
    var moving by remember { mutableStateOf<Category?>(null) }
    var pinFor by remember { mutableStateOf<String?>(null) }
    val showFocus = remember { FocusRequester() }
    // Retour : cache d'abord les listes (si une chaîne joue), puis quitte l'écran.
    androidx.activity.compose.BackHandler(enabled = s.listsOpen && playingNow) { s.listsOpen = false }
    LaunchedEffect(s.listsOpen) { if (s.listsOpen) runCatching { chFocus.requestFocus() } else runCatching { showFocus.requestFocus() } }
    if (!playingNow) s.listsOpen = true
    Box(Modifier.fillMaxSize().then(if (playingNow) Modifier else Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.bgTop, C.bgBottom))))
        .onGloballyPositioned { LiveVideo.previewBounds = it.boundsInRoot() }) {
        // Listes cachées : tout l'écran est un bouton (appui / OK) qui les rouvre.
        if (!s.listsOpen) {
            Box(Modifier.fillMaxSize().focusRequester(showFocus).focusable()
                .onKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp && (e.key == androidx.compose.ui.input.key.Key.DirectionCenter || e.key == androidx.compose.ui.input.key.Key.Enter || e.key == androidx.compose.ui.input.key.Key.DirectionUp || e.key == androidx.compose.ui.input.key.Key.DirectionDown || e.key == androidx.compose.ui.input.key.Key.DirectionLeft || e.key == androidx.compose.ui.input.key.Key.DirectionRight)) { s.listsOpen = true; true } else false }
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { s.listsOpen = true })
            return@Box
        }
        // Appui sur la vidéo (hors listes) : cache les listes.
        if (playingNow) Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { s.listsOpen = false })
        Row(Modifier.fillMaxSize().padding(start = 18.dp, top = 12.dp, bottom = 12.dp)) {
            // 1) Catégories (01, 02…)
            Column(Modifier.width(300.dp).fillMaxHeight().background(Color(0xE6000000)).padding(vertical = 6.dp)) {
                val cats = categories
                if (cats == null) Loading() else LazyColumn(state = s.catState) {
                    item(key = "catsearch") {
                        SearchBox(s.catSearching, { s.catSearching = it }, s.catFilter, { s.catFilter = it }, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), label = Str.searchCats)
                    }
                    val fixed = listOf(Category(Screen.FAVORITES, Str.favorites), Category(Screen.ALL, Str.all))
                    val cq = s.catFilter.trim()
                    val all = (fixed + cats).let { l -> if (cq.isEmpty()) l else l.filter { it.name.contains(cq, ignoreCase = true) } }
                    val focusAt = all.indexOfFirst { it.id == s.category }.takeIf { it >= 0 } ?: 0
                    itemsIndexed(all, key = { i, c -> c.id + "#" + i }) { i, c ->
                        val selected = s.category == c.id
                        val m = if (i == focusAt) Modifier.focusRequester(catFocus) else Modifier
                        val open = { s.category = c.id; s.focusIndex = 0; s.filter = ""; s.searching = false }
                        Tile({ if (s.category != c.id) { if (Store.isLocked("live", c.id)) pinFor = c.id else open() } },
                            m.fillMaxWidth().height(48.dp), RoundedCornerShape(0.dp),
                            colors = listOf(if (selected) Color(0x552F6BFF) else Color.Transparent), focusColors = C.live, focusScale = 1f,
                            onLongClick = if (c.id == Screen.ALL || c.id == Screen.FAVORITES) null else ({ optionsFor = c })) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(String.format(java.util.Locale.ROOT, "%02d", i + 1), color = C.text2, fontFamily = Sora, fontSize = 17.sp, modifier = Modifier.width(38.dp))
                                Label((if (Store.isLocked("live", c.id)) "🔒 " else "") + (if (c.id == Screen.FAVORITES) "★ " + c.name else c.name), 16.sp, C.text, if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Box(Modifier.width(2.dp).fillMaxHeight().background(Color(0x22FFFFFF)))
            // 2) Chaînes (001, 002…)
            Column(Modifier.width(380.dp).fillMaxHeight().background(Color(0xD9000000)).padding(vertical = 6.dp)) {
                if (s.searching) SearchBox(true, { s.searching = it }, s.filter, { s.filter = it }, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
                val list = shown
                when {
                    list == null -> Loading()
                    list.isEmpty() -> Message(Str.empty)
                    else -> LazyColumn(state = s.listState, contentPadding = PaddingValues(bottom = 8.dp)) {
                        itemsIndexed(list, key = { i, c -> c.id + "#" + i }) { i, c ->
                            val m = if (i == s.focusIndex.coerceIn(0, list.lastIndex)) Modifier.focusRequester(chFocus) else Modifier
                            ChannelRow(c, i, s.playing?.id == c.id, m.onFocusChanged { if (it.isFocused) s.focusIndex = i }) { select(c, list, i) }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // 3) Barre d'outils (comme la télécommande : recherche, favori, plein écran)
            Column(Modifier.padding(end = 10.dp).align(Alignment.CenterVertically).clip(RoundedCornerShape(12.dp)).background(Color(0xB3000000)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolButton(Icons.Search) { s.searching = !s.searching; if (!s.searching) s.filter = "" }
                val p = s.playing
                if (p != null) {
                    val fav = Store.isFavorite("live", p.id)
                    ToolButton(Icons.Star, if (fav) Color(0xFFFFC94D) else Color.White, fill = fav) { Store.toggleFavorite(Favorite("live", p.id, p.name, p.logo)) }
                    ToolButton(Icons.Live) { shown?.let { l -> val i = l.indexOfFirst { it.id == p.id }.coerceAtLeast(0); nav.go(Screen.Play(p.name, x.liveUrl(p.id), live = true, channels = l, index = i)) } }
                }
            }
        }
        optionsFor?.let { c ->
            ChoiceDialog(Str.catOptions + " · " + c.name, listOf(
                (if (Store.isLocked("live", c.id)) Str.unlock else Str.lock) to { optionsFor = null; if (Store.isLocked("live", c.id)) pinFor = "unlock:" + c.id else Store.toggleLock("live", c.id) },
                Str.moveCat to { optionsFor = null; moving = c },
                Str.cancel to { optionsFor = null },
            )) { optionsFor = null }
        }
        moving?.let { c ->
            val ids = (categories ?: emptyList()).map { it.id }
            fun move(d: Int) {
                val l = ids.toMutableList(); val i = l.indexOf(c.id); val j = i + d
                if (i >= 0 && j in l.indices) { l.removeAt(i); l.add(j, c.id); Store.setCategoryOrder("live", l) }
            }
            ChoiceDialog(Str.moveCat + " · " + c.name, listOf(Str.moveUp to { move(-1) }, Str.moveDown to { move(1) }, "OK" to { moving = null })) { moving = null }
        }
        pinFor?.let { id ->
            PinDialog({
                pinFor = null
                if (id.startsWith("unlock:")) Store.toggleLock("live", id.removePrefix("unlock:"))
                else { s.category = id; s.focusIndex = 0; s.filter = ""; s.searching = false }
            }) { pinFor = null }
        }
        if (!playingNow && s.playing == null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 120.dp)) { Label(Str.chooseChannel, 16.sp, C.text2) }
        if (Playback.error && s.playing != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 120.dp)) { Label(Str.playError, 14.sp, C.error, lines = 2) }
    }
}

@Composable
private fun ToolButton(icon: String, tint: Color = Color.White, fill: Boolean = false, onClick: () -> Unit) {
    Tile(onClick, Modifier.size(52.dp), RoundedCornerShape(10.dp), colors = listOf(Color.Transparent), focusColors = C.live, focusScale = 1.06f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, 26.dp, tint, fill = fill) }
    }
}

@Composable
private fun ChannelRow(c: Channel, index: Int, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxWidth().height(54.dp), RoundedCornerShape(0.dp),
        colors = listOf(if (playing) Color(0x552F6BFF) else Color.Transparent), focusColors = C.live, focusScale = 1f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(String.format(java.util.Locale.ROOT, "%03d", if (c.number > 0) c.number else index + 1), color = C.text2, fontFamily = Sora, fontSize = 16.sp, modifier = Modifier.width(46.dp))
            AsyncImage(c.logo, null, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(12.dp))
            Label(c.name, 16.sp, if (playing) Color(0xFF8FB3FF) else C.text, if (playing) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
        }
    }
}

/** Surface vidéo du lecteur partagé. [controls] : barre de contrôle Media3 (plein écran VOD). */
@Composable
fun PlayerSurface(controls: Boolean, modifier: Modifier = Modifier, onView: (PlayerView) -> Unit = {}) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = controls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                keepScreenOn = true
                if (!controls) {
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
                player = Playback.player(c)
                // Plein écran VOD : la barre de contrôle reçoit la télécommande tout de suite.
                if (controls) post { requestFocus(); showController() }
                onView(this)
            }
        },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxSize(),
    )
}

/** Demande de focus avec quelques tentatives (la liste peut ne pas être encore composée). */
suspend fun InitialFocusOnce(r: FocusRequester) {
    repeat(10) {
        kotlinx.coroutines.delay(50)
        if (runCatching { r.requestFocus() }.getOrDefault(false)) return
    }
}
