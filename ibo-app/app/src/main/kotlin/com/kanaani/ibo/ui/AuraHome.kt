package com.kanaani.ibo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanaani.ibo.BuildConfig
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Xtream

/**
 * Accueil « Aura » : menu à gauche (logo, Direct, Films, Séries, Favoris, Téléchargements, Compte, Réglages)
 * et, à droite, des rangées d'affiches : reprendre la lecture, derniers films, dernières séries.
 */
@Composable
fun AuraHome(nav: Navigator, x: Xtream, now: Long) {
    val first = remember { FocusRequester() }
    InitialFocus(first)
    val scope = rememberCoroutineScope()
    var reload by remember { mutableStateOf(0) }
    val movies by produceState<List<Poster>?>(null, x, x.catalogVersion, reload) {
        value = runCatching { x.latestMovies(24).map { Poster(it.id, it.name, it.poster, it.rating, movie = it) } }.getOrDefault(emptyList())
    }
    val shows by produceState<List<Poster>?>(null, x, x.catalogVersion, reload) {
        value = runCatching { x.latestShows(24).map { Poster(it.id, it.name, it.poster, it.rating, show = it) } }.getOrDefault(emptyList())
    }
    val continuing = remember(Store.watching) {
        Store.watching.take(20).map { w ->
            val sub = if (w.kind == "series" && w.episode > 0) "${w.name} · S${w.season} E${w.episode}" else w.name
            Poster(w.id, sub, w.image, null, watch = w)
        }
    }
    var toast by remember { mutableStateOf<String?>(null) }
    var askRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2000); toast = null } }
    Backdrop {
        Row(Modifier.fillMaxSize()) {
            // ── Menu (à plat, comme une liste) ──
            Column(
                Modifier.width(260.dp).fillMaxHeight().background(Color(0xFF0B0B0D))
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 22.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Logo(112.dp) }
                Spacer(Modifier.height(18.dp))
                MenuItem(Str.live, Icons.Live, Modifier.focusRequester(first)) { nav.go(Screen.Live()) }
                MenuItem(Str.movies, Icons.Movies) { nav.go(Screen.Catalog(series = false)) }
                MenuItem(Str.series, Icons.Series) { nav.go(Screen.Catalog(series = true)) }
                MenuItem(Str.favorites, Icons.Heart) { nav.go(Screen.Favorites) }
                MenuItem(Str.downloads, Icons.Download) { nav.go(Screen.Downloads) }
                MenuItem(Str.refresh, Icons.Refresh) { askRefresh = true }
                MenuItem(Str.account, Icons.Account) { nav.go(Screen.Account) }
                MenuItem(Str.settings, Icons.Settings) { nav.go(Screen.Settings) }
                Spacer(Modifier.height(24.dp))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(clock(now), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, maxLines = 1)
                    Label(date(now, "EEEE d MMMM"), 12.sp, C.text2)
                    Spacer(Modifier.height(6.dp))
                    Label(expiryLine(AccountCache.value), 11.sp, C.text3, lines = 2)
                    Label("v${BuildConfig.VERSION_NAME}", 10.sp, C.text3)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x1AFFFFFF)))
            // ── Rangées d'affiches ──
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 28.dp, end = 12.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (continuing.isNotEmpty()) item { PosterRow(Str.continueWatching, continuing, {}) { p -> p.watch?.let { resumeWatch(nav, x, scope, it) } } }
                item { PosterRow(Str.latestMoviesUpdates, movies, {}) { p -> p.movie?.let { nav.go(Screen.MovieDetail(it)) } } }
                item { PosterRow(Str.latestSeries, shows, {}) { p -> p.show?.let { nav.go(Screen.ShowDetail(it)) } } }
            }
        }
        if (askRefresh) ChoiceDialog(Str.refreshAsk, listOf(Str.yes to { askRefresh = false; x.clearCache(); AccountCache.value = null; reload++; toast = Str.refreshed }, Str.cancel to { askRefresh = false })) { askRefresh = false }
        toast?.let { Box(Modifier.align(Alignment.BottomCenter).padding(24.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xE6222226)).padding(horizontal = 18.dp, vertical = 10.dp)) { Label(it, 14.sp) } }
    }
}

@Composable
private fun MenuItem(label: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(0.dp), colors = listOf(Color.Transparent), focusColors = listOf(Color(0x1FFFFFFF)), focusScale = 1f) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 24.dp, if (f) Color.White else C.text2, stroke = 1.8f)
            Spacer(Modifier.width(18.dp))
            Label(label, 18.sp, if (f) Color.White else C.text2, if (f) FontWeight.Bold else FontWeight.Medium)
        }
        if (f) Box(Modifier.align(androidx.compose.ui.AbsoluteAlignment.CenterLeft).width(4.dp).fillMaxHeight().background(C.accent))
    }
}

@Composable
private fun PosterRow(title: String, list: List<Poster>?, onFocus: (Poster) -> Unit, open: (Poster) -> Unit) {
    if (list != null && list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
        if (list == null) Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.CenterStart) { Loading() }
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
            itemsIndexed(list, key = { i, p -> p.id + "#" + i }) { _, p ->
                PosterCard(p, Modifier.width(170.dp).onFocusChanged { if (it.hasFocus) onFocus(p) }) { open(p) }
            }
        }
    }
}
