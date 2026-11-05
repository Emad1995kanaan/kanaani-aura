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
    val movies by produceState<List<Poster>?>(null, x, x.catalogVersion) {
        value = runCatching { x.latestMovies(24).map { Poster(it.id, it.name, it.poster, it.rating, movie = it) } }.getOrDefault(emptyList())
    }
    val shows by produceState<List<Poster>?>(null, x, x.catalogVersion) {
        value = runCatching { x.latestShows(24).map { Poster(it.id, it.name, it.poster, it.rating, show = it) } }.getOrDefault(emptyList())
    }
    val continuing = remember(Store.watching) {
        Store.watching.take(20).map { w ->
            val sub = if (w.kind == "series" && w.episode > 0) "${w.name} · S${w.season} E${w.episode}" else w.name
            Poster(w.id, sub, w.image, null, watch = w)
        }
    }
    // Menu ouvert quand il a le focus, replié (icônes) quand on est sur les affiches.
    var menuOpen by remember { mutableStateOf(true) }
    val menuWidth by androidx.compose.animation.core.animateDpAsState(if (menuOpen) 250.dp else 84.dp, label = "menu")
    // Affiche sélectionnée : grand fond + titre / note / résumé en haut.
    var hero by remember { mutableStateOf<Poster?>(null) }
    val heroArt by produceState<com.kanaani.ibo.data.TmdbArt.Art?>(null, hero) {
        val h = hero ?: return@produceState
        value = null
        value = com.kanaani.ibo.data.TmdbArt.find(if (h.show != null || h.watch?.kind == "series") "tv" else "movie", h.watch?.name ?: h.name)
    }
    val firstRow = remember { FocusRequester() }
    Backdrop {
        // Fond du titre sélectionné (fondu), assombri pour garder le texte lisible.
        androidx.compose.animation.Crossfade(heroArt?.backdrop ?: hero?.image, animationSpec = androidx.compose.animation.core.tween(600), label = "hero") { img ->
            if (img != null) Box(Modifier.fillMaxSize()) {
                coil3.compose.AsyncImage(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = 0.45f)
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color(0xF2070A12), 0.55f to Color(0x99070A12), 1f to Color(0x33070A12))))
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xF2070A12))))
            }
        }
        Row(Modifier.fillMaxSize()) {
            // ── Menu ──
            Column(
                Modifier.width(menuWidth).fillMaxHeight().background(Color(0xE6070B16))
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 22.dp)
                    .onFocusChanged { if (it.hasFocus) menuOpen = true },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) { Logo(if (menuOpen) 104.dp else 52.dp) }
                Spacer(Modifier.height(14.dp))
                MenuItem(Str.live, Icons.Live, menuOpen, Modifier.focusRequester(first)) { nav.go(Screen.Live()) }
                MenuItem(Str.movies, Icons.Movies, menuOpen) { nav.go(Screen.Catalog(series = false)) }
                MenuItem(Str.series, Icons.Series, menuOpen) { nav.go(Screen.Catalog(series = true)) }
                MenuItem(Str.favorites, Icons.Heart, menuOpen) { nav.go(Screen.Favorites) }
                MenuItem(Str.downloads, Icons.Download, menuOpen) { nav.go(Screen.Downloads) }
                MenuItem(Str.account, Icons.Account, menuOpen) { nav.go(Screen.Account) }
                MenuItem(Str.settings, Icons.Settings, menuOpen) { nav.go(Screen.Settings) }
                Spacer(Modifier.height(24.dp))
                if (menuOpen) {
                    Text(clock(now), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
                    Label(date(now, "EEEE d MMMM"), 11.sp, C.text2)
                    Label(expiryLine(AccountCache.value), 10.sp, C.text3, lines = 2)
                    Label("v${BuildConfig.VERSION_NAME}", 10.sp, C.text3)
                } else Text(clock(now), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
            Column(Modifier.weight(1f).fillMaxHeight().onFocusChanged { if (it.hasFocus) menuOpen = false }) {
                // ── Titre sélectionné ──
                val h = hero
                if (h != null) Column(Modifier.fillMaxWidth().height(210.dp).padding(start = 30.dp, end = 40.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    run {
                        Text(com.kanaani.ibo.data.Promo.clean(h.watch?.name ?: h.name), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val r = h.rating?.toDoubleOrNull()?.takeIf { it > 0 }
                        if (r != null) Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("★ " + String.format(java.util.Locale.ROOT, "%.1f", r), color = Color.Black, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        heroArt?.overview?.let { Label(it, 15.sp, C.text2, lines = 3, modifier = Modifier.fillMaxWidth(0.7f)) }
                    }
                }
                // ── Rangées d'affiches ──
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 26.dp, end = 10.dp, top = if (h == null) 22.dp else 0.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (continuing.isNotEmpty()) item { PosterRow(Str.continueWatching, continuing, { hero = it }) { p -> p.watch?.let { resumeWatch(nav, x, scope, it) } } }
                    item { PosterRow(Str.latestMoviesUpdates, movies, { hero = it }) { p -> p.movie?.let { nav.go(Screen.MovieDetail(it)) } } }
                    item { PosterRow(Str.latestSeries, shows, { hero = it }) { p -> p.show?.let { nav.go(Screen.ShowDetail(it)) } } }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: String, open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(14.dp), colors = listOf(Color.Transparent), focusColors = C.live, focusScale = 1.03f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 24.dp, Color.White, stroke = 1.8f)
            if (open) {
                Spacer(Modifier.width(16.dp))
                Label(label, 17.sp, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PosterRow(title: String, list: List<Poster>?, onFocus: (Poster) -> Unit, open: (Poster) -> Unit) {
    if (list != null && list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        if (list == null) Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.CenterStart) { Loading() }
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
            itemsIndexed(list, key = { i, p -> p.id + "#" + i }) { _, p ->
                PosterCard(p, Modifier.width(140.dp).onFocusChanged { if (it.hasFocus) onFocus(p) }) { open(p) }
            }
        }
    }
}
