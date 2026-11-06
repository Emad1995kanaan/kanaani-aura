package com.kanaani.ibo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanaani.ibo.BuildConfig
import com.kanaani.ibo.data.Channel
import com.kanaani.ibo.data.Movie
import com.kanaani.ibo.data.Show
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Xtream
import kotlinx.coroutines.launch

// ───────────────────────── Favoris ─────────────────────────

@Composable
fun FavoritesScreen(nav: Navigator, x: Xtream) {
    var tab by rememberSaveable { mutableStateOf("live") }
    val list = Store.favorites.filter { it.kind == tab }
    val first = remember { FocusRequester() }
    InitialFocus(first)
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Header(Str.favorites)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("live" to Str.live, "movie" to Str.movies, "series" to Str.series).forEachIndexed { i, (k, label) ->
                    val on = tab == k
                    Tile({ tab = k }, (if (i == 0) Modifier.focusRequester(first) else Modifier).height(38.dp), RoundedCornerShape(19.dp),
                        colors = if (on) C.live else listOf(C.card), focusColors = C.live, focusScale = 1.04f) {
                        Box(Modifier.padding(horizontal = 20.dp).height(38.dp), contentAlignment = Alignment.Center) {
                            Label(label, 14.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
            if (list.isEmpty()) { Message(Str.empty); return@Column }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (tab == "live") 210.dp else 118.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                itemsIndexed(list, key = { _, f -> f.kind + f.id }) { i, f ->
                    when (tab) {
                        "live" -> Tile({
                            val channels = list.mapIndexed { n, c -> Channel(c.id, c.name, c.image, n + 1, null) }
                            nav.go(Screen.Play(f.name, x.liveUrl(f.id), live = true, channels = channels, index = i))
                        }, Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.03f) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(f.image, null, Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
                                Spacer(Modifier.width(10.dp))
                                Label(f.name, 14.sp, weight = FontWeight.SemiBold)
                            }
                        }
                        "movie" -> PosterCard(Poster(f.id, f.name, f.image, null), Modifier) {
                            nav.go(Screen.MovieDetail(Movie(f.id, f.name, f.image, null, f.ext.ifEmpty { "mp4" }, null)))
                        }
                        else -> PosterCard(Poster(f.id, f.name, f.image, null), Modifier) {
                            nav.go(Screen.ShowDetail(Show(f.id, f.name, f.image, null, null)))
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Compte & Réglages (Aura : un seul panneau à deux onglets) ─────────────────────────

@Composable
fun AccountScreen(nav: Navigator, x: Xtream) = SettingsPanel(nav, x, 0)

@Composable
fun SettingsScreen(nav: Navigator, x: Xtream) = SettingsPanel(nav, x, 1)

private val PanelBg = Color(0xFF1B1C1F)
private val PanelBox = Color(0xFF0A0A0B)
private val CellBg = Color(0xFF111113)
private val ButtonBg = Color(0xFF232326)

private fun flag(l: Lang) = when (l) { Lang.AR -> "🇸🇦"; Lang.EN -> "🇬🇧"; Lang.HE -> "🇮🇱" }

/** Adresse de l'appareil, présentée comme une adresse MAC (aa:bb:cc:dd:ee:ff). */
private fun macLike(id: String): String {
    val hex = id.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
    return if (hex.length >= 12) hex.take(12).chunked(2).joinToString(":") else id
}

@Composable
private fun SettingsPanel(nav: Navigator, x: Xtream, startTab: Int) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var aboutTaps by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var aboutLast by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2_500); toast = null } }
    LaunchedEffect(x) { AccountCache.load(x) }
    val tabFocus = remember { FocusRequester() }
    InitialFocus(tabFocus)

    Box(Modifier.fillMaxSize().background(PanelBg).padding(horizontal = 40.dp, vertical = 36.dp)) {
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(PanelBox).padding(10.dp)) {
            // Onglets
            Column(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFF070708))) {
                listOf(Triple(Str.account, Icons.Account, 0), Triple(Str.settings, Icons.Settings, 1)).forEach { (label, icon, i) ->
                    val on = tab == i
                    Tile({ tab = i }, (if (i == tab) Modifier.focusRequester(tabFocus) else Modifier).fillMaxWidth().height(62.dp)
                        .onFocusChanged { if (it.isFocused) tab = i },
                        RoundedCornerShape(14.dp), colors = if (on) C.live else listOf(Color.Transparent), focusColors = C.live, focusScale = 1f) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, 26.dp, Color.White, stroke = 2f)
                            Spacer(Modifier.width(26.dp))
                            Label(label, 18.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (tab == 0) AccountTab(x) else {
                    PanelButton(Str.changePin) { dialog = "pin" }
                    PanelButton(Str.resetContent) { dialog = "reset" }
                    PanelButton(Str.resetCode) { dialog = "logout" }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(Str.chooseLanguage, 18.sp, weight = FontWeight.Bold)
                        Spacer(Modifier.width(14.dp))
                        Tile({ dialog = "lang" }, Modifier.weight(1f).height(56.dp), RoundedCornerShape(14.dp), colors = listOf(ButtonBg), focusColors = C.live, focusScale = 1f) {
                            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                                Label(flag(Str.lang) + "  " + Str.lang.label, 18.sp, weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                                Label("▾", 16.sp, C.text2, modifier = Modifier.align(Alignment.CenterEnd))
                            }
                        }
                    }
                    val (on, off) = Str.onOff.split('|').let { it[0] to it.getOrElse(1) { "" } }
                    PanelButton(Str.autoQuality + " : " + (if (Store.autoQuality) on else off)) { Store.chooseAutoQuality(!Store.autoQuality) }
                    PanelButton(Str.orderLive) { nav.go(Screen.Reorder("live")) }
                    PanelButton(Str.orderMovies) { nav.go(Screen.Reorder("movie")) }
                    PanelButton(Str.orderSeries) { nav.go(Screen.Reorder("series")) }
                    PanelButton(Str.layout) { dialog = "layout" }
                    PanelButton(Str.refresh) { x.clearCache(); AccountCache.value = null; toast = Str.refreshed }
                    PanelButton(Str.checkUpdates) { scope.launch { com.kanaani.ibo.data.Updater.check(manual = true) } }
                    PanelButton("${Str.about} · ${Str.version} ${BuildConfig.VERSION_NAME}") {
                        // Outil caché : 5 appuis rapides = outils d'administration (export des listes, envoi de l'ordre).
                        val t = System.currentTimeMillis()
                        aboutTaps = if (t - aboutLast < 1_500) aboutTaps + 1 else 1
                        aboutLast = t
                        if (aboutTaps >= 5) { aboutTaps = 0; dialog = "admin" }
                    }
                }
            }
        }
        toast?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) { Label(it, 15.sp, Color(0xFF8FB3FF), FontWeight.SemiBold) } }
    }
    if (dialog == "pin") PinChange { dialog = null }
    when (dialog) {
        "lang" -> ChoiceDialog(Str.chooseLanguage, Lang.entries.map { l -> ((if (l == Str.lang) "✓  " else "") + flag(l) + "  " + l.label) to { Store.chooseLanguage(l.code); dialog = null } }) { dialog = null }
        "reset" -> ChoiceDialog(Str.resetContentAsk, listOf<Pair<String, () -> Unit>>(
            Str.yes to { dialog = null; Store.resetContent(); toast = Str.resetDone },
            Str.cancel to { dialog = null },
        )) { dialog = null }
        "logout" -> ChoiceDialog(Str.logoutAsk, listOf<Pair<String, () -> Unit>>(
            Str.resetCode to { dialog = null; AccountCache.value = null; com.kanaani.ibo.player.Playback.stop(); Store.logout() },
            Str.cancel to { dialog = null },
        )) { dialog = null }
        "layout" -> {
            val L = com.kanaani.ibo.data.Layouts
            val opts: List<Pair<String, () -> Unit>> = L.list.map { r ->
                val on = (L.mode == "auto" && r.default) || L.mode == r.id.toString()
                ((if (on) "✓  " else "") + r.name + (if (r.default) " · ${Str.layoutDefaultTag}" else "")) to {
                    dialog = null; toast = Str.loading
                    scope.launch { toast = if (L.choose(r)) Str.layoutApplied else Str.playError }
                    Unit
                }
            } + (if (L.mode == "custom") listOf(("✓  " + Str.layoutMine) to { dialog = null }) else emptyList())
            ChoiceDialog(Str.layout, opts.ifEmpty { listOf(Str.ok to { dialog = null }) }) { dialog = null }
        }
        "admin" -> ChoiceDialog(Str.adminTools, listOf(
            Str.exportLists to { dialog = null; toast = Str.loading; scope.launch { toast = if (com.kanaani.ibo.data.Export.lists(ctx, x)) null else Str.playError }; Unit },
            Str.uploadLayout to { dialog = "upload" },
        )) { dialog = null }
        "upload" -> UploadLayoutDialog(onDone = { msg -> dialog = null; toast = msg }) { dialog = null }
    }
}

@Composable
private fun AccountTab(x: Xtream) {
    val a = AccountCache.value
    Label(Str.accountInfo, 28.sp, weight = FontWeight.Bold)
    InfoCell(Str.activationCode, Store.trialLogin()?.first ?: x.username)
    if (a == null) { Box(Modifier.fillMaxWidth().height(90.dp)) { Loading() } } else {
        val active = a.status?.lowercase() == "active"
        InfoCell(Str.status, if (active) Str.active else (a.status ?: "—"), if (active) Color(0xFF4ADE80) else C.error)
        val trialEnd = if (Store.trialLogin() != null) com.kanaani.ibo.data.Trial.expires.takeIf { it > 0 } else null
        val exp = trialEnd ?: a.expiresAt
        InfoCell(Str.expiryDate, exp?.let {
            val days = ((it - System.currentTimeMillis()).coerceAtLeast(0) + 86_399_999L) / 86_400_000L
            "$days ${Str.days} · ${date(it, "dd/MM/yyyy")}"
        } ?: Str.unlimited)
        InfoCell(Str.connections, "${a.activeConnections ?: 0} / ${a.maxConnections ?: "—"}")
    }
    Spacer(Modifier.height(6.dp))
    Label(Str.appInfo, 28.sp, weight = FontWeight.Bold)
    InfoCell(Str.macAddress, macLike(com.kanaani.ibo.data.Panel.deviceId()))
    InfoCell(Str.version, "${com.kanaani.ibo.Brand.name} ${BuildConfig.VERSION_NAME}")
}

/** Ligne « intitulé | valeur » en deux cases sombres, comme sur les captures. */
@Composable
private fun InfoCell(k: String, v: String, color: Color = C.text) {
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(0.38f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(CellBg).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterEnd) {
            Label(k, 18.sp, C.text, FontWeight.SemiBold)
        }
        Box(Modifier.weight(0.62f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(CellBg).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Label(v, 19.sp, color, FontWeight.Bold)
        }
    }
}

@Composable
private fun PanelButton(label: String, onClick: () -> Unit) {
    Tile(onClick, Modifier.fillMaxWidth().height(58.dp), RoundedCornerShape(16.dp), colors = listOf(ButtonBg), focusColors = C.live, focusScale = 1f) {
        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Label(label, 18.sp, weight = FontWeight.Bold) }
    }
}


/** Outil caché : envoyer l'ordre de cet appareil au panneau (identifiants d'un administrateur du panneau). */
@Composable
private fun UploadLayoutDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    InitialFocus(first)
    fun send(replace: Long?) {
        if (busy || user.isBlank() || pass.isEmpty() || (replace == null && name.isBlank())) return
        busy = true; err = null
        scope.launch {
            val e = com.kanaani.ibo.data.Layouts.upload(user.trim(), pass, name.trim(), replace)
            busy = false
            if (e == null) { com.kanaani.ibo.data.Panel.refresh(); onDone(Str.uploaded) } else err = e
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(480.dp).clip(RoundedCornerShape(20.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(20.dp))
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(Str.uploadLayout, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Field(user, { user = it }, Str.panelUser, Modifier.fillMaxWidth(), focusRequester = first)
            Field(pass, { pass = it }, Str.panelPass, Modifier.fillMaxWidth())
            Field(name, { name = it }, Str.layoutName, Modifier.fillMaxWidth())
            err?.let { Label(it, 13.sp, C.error, FontWeight.SemiBold, lines = 2) }
            Tile({ send(null) }, Modifier.fillMaxWidth().height(46.dp), RoundedCornerShape(12.dp), colors = C.live, focusScale = 1.02f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(if (busy) Str.loading else Str.newLayout, 15.sp, weight = FontWeight.Bold) }
            }
            com.kanaani.ibo.data.Layouts.list.forEach { r ->
                Tile({ send(r.id) }, Modifier.fillMaxWidth().height(42.dp), RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.02f) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) { Label("${Str.replaceLayout}: ${r.name}", 14.sp, weight = FontWeight.SemiBold) }
                }
            }
            Tile(onDismiss, Modifier.fillMaxWidth().height(42.dp), RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.cancel, 14.sp, weight = FontWeight.SemiBold) }
            }
        }
    }
}


/** Aura : changer le code parental (ancien code puis nouveau). */
@Composable
private fun PinChange(onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    if (step == 0) PinDialog({ step = 1 }, onDone)
    else Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(380.dp).clip(RoundedCornerShape(16.dp)).background(C.panel).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(Str.changePin, 20.sp, weight = FontWeight.Bold)
            Field(code, { code = it.filter(Char::isDigit).take(8) }, "0000", Modifier.fillMaxWidth(), numeric = true, startEditing = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done, onDone = { if (code.length >= 4) { com.kanaani.ibo.data.Store.setPin(code); onDone() } })
            Tile({ if (code.length >= 4) { com.kanaani.ibo.data.Store.setPin(code); onDone() } }, Modifier.fillMaxWidth().height(46.dp), RoundedCornerShape(10.dp), colors = C.live) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("OK", 16.sp, weight = FontWeight.Bold) }
            }
        }
    }
}
