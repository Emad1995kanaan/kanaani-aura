package com.kanaani.ibo.ui

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
        if (s.category == null) cats.firstOrNull()?.let { s.category = it.id; s.focusIndex = 0 }
    }
    LaunchedEffect(channels) {
        val list = shown ?: return@LaunchedEffect
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
            nav.go(Screen.Play(c.name, x.liveUrl(c.id), live = true, channels = list, index = index))
        } else {
            s.playing = c
            Playback.play(ctx, x.liveUrl(c.id))
        }
    }

    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Header(Str.live)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1) Catégories
                Column(Modifier.width(200.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(C.panel).padding(8.dp)) {
                    val cats = categories
                    if (cats == null) Loading() else LazyColumn(state = s.catState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Recherche dans les catégories, en tête de liste.
                        item(key = "catsearch") {
                            SearchBox(s.catSearching, { s.catSearching = it }, s.catFilter, { s.catFilter = it }, Modifier.fillMaxWidth(), label = Str.searchCats)
                        }
                        val fixed = listOf(Category(Screen.FAVORITES, "★ " + Str.favorites), Category(Screen.ALL, Str.all))
                        val cq = s.catFilter.trim()
                        val all = (fixed + cats).let { l -> if (cq.isEmpty()) l else l.filter { it.name.contains(cq, ignoreCase = true) } }
                        val focusAt = all.indexOfFirst { it.id == s.category }.takeIf { it >= 0 } ?: 0
                        itemsIndexed(all, key = { i, c -> c.id + "#" + i }) { i, c ->
                            val selected = s.category == c.id
                            val m = if (i == focusAt) Modifier.focusRequester(catFocus) else Modifier
                            Tile({ if (s.category != c.id) { s.category = c.id; s.focusIndex = 0; s.filter = ""; s.searching = false } },
                                m.fillMaxWidth().height(38.dp), RoundedCornerShape(10.dp),
                                colors = listOf(if (selected) C.cardHi else Color.Transparent), focusColors = C.live, focusScale = 1.02f) {
                                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                                    Label(c.name, 13.sp, if (selected) C.text else C.text2, if (selected) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                // 2) Chaînes
                Column(Modifier.width(290.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(C.panel).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchBox(s.searching, { s.searching = it }, s.filter, { s.filter = it }, Modifier.fillMaxWidth())
                    val list = shown
                    when {
                        list == null -> Loading()
                        list.isEmpty() -> Message(Str.empty)
                        else -> LazyColumn(state = s.listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                            itemsIndexed(list, key = { i, c -> c.id + "#" + i }) { i, c ->
                                val m = if (i == s.focusIndex.coerceIn(0, list.lastIndex)) Modifier.focusRequester(chFocus) else Modifier
                                ChannelRow(c, i, s.playing?.id == c.id, m.onFocusChanged { if (it.isFocused) s.focusIndex = i }) { select(c, list, i) }
                            }
                        }
                    }
                }
                // 3) Aperçu
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Cadre de l'aperçu : la vidéo est posée dessus par LiveVideoHost (même surface qu'en plein écran).
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                        .onGloballyPositioned { LiveVideo.previewBounds = it.boundsInRoot() }, contentAlignment = Alignment.Center) {
                        if (s.playing == null || Playback.current == null) Label(Str.chooseChannel, 14.sp, C.text2)
                    }
                    val p = s.playing
                    if (p != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(p.logo, null, Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(C.card), contentScale = ContentScale.Fit)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Label(p.name, 18.sp, weight = FontWeight.Bold)
                                Label(Str.previewHint, 12.sp, C.text2)
                            }
                        }
                        val fav = Store.isFavorite("live", p.id)
                        Tile({ Store.toggleFavorite(Favorite("live", p.id, p.name, p.logo)) }, Modifier.fillMaxWidth().height(40.dp),
                            RoundedCornerShape(10.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.02f) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Star, 18.dp, if (fav) Color(0xFFFFC94D) else Color.White, fill = fav)
                                Spacer(Modifier.width(10.dp))
                                Label(if (fav) Str.removeFav else Str.addFav, 14.sp, weight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(c: Channel, index: Int, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxWidth().height(46.dp), RoundedCornerShape(10.dp),
        colors = listOf(if (playing) C.cardHi else C.card), focusColors = C.live, focusScale = 1.02f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("${if (c.number > 0) c.number else index + 1}", 12.sp, C.text3, modifier = Modifier.width(34.dp))
            AsyncImage(c.logo, null, Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(10.dp))
            Label(c.name, 14.sp, if (playing) Color(0xFF8FB3FF) else C.text, if (playing) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
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
