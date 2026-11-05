package com.kanaani.ibo.ui

import androidx.compose.foundation.background
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
    Backdrop {
        Row(Modifier.fillMaxSize()) {
            // ── Menu ──
            Column(
                Modifier.width(250.dp).fillMaxHeight().background(Color(0xE6070B16)).padding(horizontal = 18.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) { Logo(104.dp) }
                Spacer(Modifier.height(14.dp))
                MenuItem(Str.live, Icons.Live, Modifier.focusRequester(first)) { nav.go(Screen.Live()) }
                MenuItem(Str.movies, Icons.Movies) { nav.go(Screen.Catalog(series = false)) }
                MenuItem(Str.series, Icons.Series) { nav.go(Screen.Catalog(series = true)) }
                MenuItem(Str.favorites, Icons.Heart) { nav.go(Screen.Favorites) }
                MenuItem(Str.downloads, Icons.Download) { nav.go(Screen.Downloads) }
                MenuItem(Str.account, Icons.Account) { nav.go(Screen.Account) }
                MenuItem(Str.settings, Icons.Settings) { nav.go(Screen.Settings) }
                Spacer(Modifier.weight(1f))
                Text(clock(now), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
                Label(date(now, "EEEE d MMMM"), 11.sp, C.text2)
                Label(expiryLine(AccountCache.value), 10.sp, C.text3, lines = 2)
                Label("v${BuildConfig.VERSION_NAME}", 10.sp, C.text3)
            }
            // ── Rangées d'affiches ──
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 26.dp, end = 10.dp, top = 22.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (continuing.isNotEmpty()) item { PosterRow(Str.continueWatching, continuing) { p -> p.watch?.let { resumeWatch(nav, x, scope, it) } } }
                item { PosterRow(Str.latestMovies, movies) { p -> p.movie?.let { nav.go(Screen.MovieDetail(it)) } } }
                item { PosterRow(Str.latestSeries, shows) { p -> p.show?.let { nav.go(Screen.ShowDetail(it)) } } }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(14.dp), colors = listOf(Color.Transparent), focusColors = C.live, focusScale = 1.03f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 24.dp, Color.White, stroke = 1.8f)
            Spacer(Modifier.width(16.dp))
            Label(label, 17.sp, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PosterRow(title: String, list: List<Poster>?, open: (Poster) -> Unit) {
    if (list != null && list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
        if (list == null) Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.CenterStart) { Loading() }
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
            itemsIndexed(list, key = { i, p -> p.id + "#" + i }) { _, p ->
                PosterCard(p, Modifier.width(150.dp)) { open(p) }
            }
        }
    }
}
