package com.kanaani.ibo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanaani.ibo.data.Favorite
import com.kanaani.ibo.data.MovieInfo
import com.kanaani.ibo.data.ShowInfo
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Watch
import androidx.compose.runtime.rememberCoroutineScope
import com.kanaani.ibo.data.Xtream

/** Fond de fiche : image floutée par un voile sombre, comme IBO. */
@Composable
private fun DetailBackdrop(image: String?, content: @Composable () -> Unit) {
    Backdrop {
        if (image != null) AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF0040817), Color(0x99040817), Color(0x55040817)))))
        content()
    }
}

@Composable
private fun Meta(parts: List<String?>) {
    val text = parts.filterNot { it.isNullOrBlank() }.joinToString("  ·  ")
    if (text.isNotEmpty()) Label(text, 13.sp, C.text2)
}

@Composable
private fun ActionButton(label: String, icon: String, primary: Boolean, modifier: Modifier = Modifier, fill: Boolean = false, onClick: () -> Unit) {
    Tile(onClick, modifier.height(44.dp), RoundedCornerShape(12.dp), colors = if (primary) C.live else listOf(C.card), focusColors = C.live, focusScale = 1.04f) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 18.dp, Color.White, fill = fill || icon == Icons.Play)
            Spacer(Modifier.width(10.dp))
            Label(label, 15.sp, weight = FontWeight.Bold)
        }
    }
}

/** Note sur 10 en 5 étoiles dorées. */
@Composable
fun Stars(rating: String?) {
    val r = rating?.toDoubleOrNull()?.takeIf { it > 0 } ?: return
    val full = (r / 2).coerceIn(0.0, 5.0)
    Text(buildString { repeat(5) { k -> append(if (full >= k + 0.5) "★" else "☆") } } + "  " + String.format(java.util.Locale.ROOT, "%.1f", r),
        color = Color(0xFFFFC72C), fontFamily = Sora, fontSize = 20.sp, maxLines = 1)
}

/** Durée lisible : 1:05:12 ou 12:34. */
fun clockOf(ms: Long): String {
    val t = ms / 1000; val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(java.util.Locale.ROOT, "%d:%02d", m, sec)
}

@Composable
fun MovieDetailScreen(nav: Navigator, x: Xtream, s: Screen.MovieDetail) {
    val download = rememberDownloader()
    val m = s.movie
    val info by produceState<MovieInfo?>(null, m.id) { value = x.movieInfo(m.id) }
    // Fond / résumé manquants : TMDB.
    val art by produceState<com.kanaani.ibo.data.TmdbArt.Art?>(null, info) { if (info != null && (info?.backdrop == null || info?.plot.isNullOrBlank())) value = com.kanaani.ibo.data.TmdbArt.find("movie", m.name) }
    val playFocus = remember { FocusRequester() }
    InitialFocus(playFocus)
    val fav = Store.isFavorite("movie", m.id)
    DetailBackdrop(info?.backdrop ?: art?.backdrop ?: m.poster) {
        Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.width(280.dp).height(420.dp).clip(RoundedCornerShape(16.dp)).background(C.card)) {
                com.kanaani.ibo.data.PosterImage(m.poster, "movie", m.name, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.name, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 38.sp, maxLines = 2)
                Stars((info?.rating ?: m.rating))
                Meta(listOf(info?.year, info?.duration, info?.genre, (info?.rating ?: m.rating)?.let { "★ $it" }))
                (info?.plot?.takeIf { it.isNotBlank() } ?: art?.overview)?.let { Label(it, 14.sp, C.text, FontWeight.Normal, lines = 6) }
                info?.director?.let { Label("${Str.director}: $it", 12.sp, C.text2) }
                info?.cast?.let { Label("${Str.cast}: $it", 12.sp, C.text2, lines = 2) }
                Spacer(Modifier.height(6.dp))
                val w = Watch("movie", m.id, m.name, m.poster, m.ext)
                val saved = Store.watchOf("movie", m.id)?.takeIf { it.pos > 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (saved != null) {
                        ActionButton("${Str.resume} ${clockOf(saved.pos)}", Icons.Play, true, Modifier.focusRequester(playFocus)) {
                            nav.go(Screen.Play(m.name, x.movieUrl(m), live = false, watch = w, startMs = saved.pos))
                        }
                        ActionButton(Str.fromStart, Icons.Play, false) {
                            nav.go(Screen.Play(m.name, x.movieUrl(m), live = false, watch = w))
                        }
                    } else ActionButton(Str.play, Icons.Play, true, Modifier.focusRequester(playFocus)) {
                        nav.go(Screen.Play(m.name, x.movieUrl(m), live = false, watch = w))
                    }
                    ActionButton(if (fav) Str.removeFav else Str.addFav, Icons.Star, false, fill = fav) {
                        Store.toggleFavorite(Favorite("movie", m.id, m.name, m.poster, m.ext))
                    }
                    // Téléchargement hors ligne.
                    val dl = com.kanaani.ibo.data.Downloads.get("movie:${m.id}")
                    ActionButton(dlLabel(dl), Icons.Download, false) {
                        when (dl?.state) {
                            null -> download(movieDl(x, m))
                            com.kanaani.ibo.data.DlState.Done -> if (!playDownload(nav, dl)) nav.go(Screen.Downloads)
                            com.kanaani.ibo.data.DlState.Paused, com.kanaani.ibo.data.DlState.Failed -> com.kanaani.ibo.data.Downloads.resume(dl.key)
                            else -> nav.go(Screen.Downloads)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowDetailScreen(nav: Navigator, x: Xtream, s: Screen.ShowDetail) {
    val download = rememberDownloader()
    val show = s.show
    val info by produceState<ShowInfo?>(null, show.id) { value = x.showInfo(show.id) }
    val art by produceState<com.kanaani.ibo.data.TmdbArt.Art?>(null, info) { if (info != null && (info?.backdrop == null || info?.plot.isNullOrBlank())) value = com.kanaani.ibo.data.TmdbArt.find("tv", show.name) }
    val seasons = info?.seasons.orEmpty()
    LaunchedEffect(seasons) { val cur = s.season; if (cur == null || !seasons.containsKey(cur)) s.season = seasons.keys.firstOrNull() }
    val episodes = s.season?.let { seasons[it] }.orEmpty()
    val firstEpisode = remember { FocusRequester() }
    LaunchedEffect(info) { if (info != null) InitialFocusOnce(firstEpisode) }
    val fav = Store.isFavorite("series", show.id)
    val saved = Store.watchOf("series", show.id)
    val scope = rememberCoroutineScope()

    DetailBackdrop(info?.backdrop ?: art?.backdrop ?: show.poster) {
        Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(200.dp).height(300.dp).clip(RoundedCornerShape(14.dp)).background(C.card)) {
                    com.kanaani.ibo.data.PosterImage(show.poster, "tv", show.name, Modifier.fillMaxSize())
                }
                Text(show.name, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 2)
                Stars(info?.rating ?: show.rating)
                Meta(listOf(info?.year, info?.genre, (info?.rating ?: show.rating)?.let { "★ $it" }))
                (info?.plot?.takeIf { it.isNotBlank() } ?: art?.overview)?.let { Label(it, 12.sp, C.text2, FontWeight.Normal, lines = 5) }
                if (saved != null && saved.episodeId.isNotEmpty()) {
                    ActionButton("${Str.resume}  S${saved.season} E${saved.episode}", Icons.Play, true) {
                        resumeWatch(nav, x, scope, saved)
                    }
                }
                ActionButton(if (fav) Str.removeFav else Str.addFav, Icons.Star, false, fill = fav) {
                    Store.toggleFavorite(Favorite("series", show.id, show.name, show.poster))
                }
                if (episodes.isNotEmpty()) ActionButton(Str.downloadSeason + (s.season?.let { " $it" } ?: ""), Icons.Download, false) {
                    episodes.forEach { e -> if (com.kanaani.ibo.data.Downloads.get("episode:${e.id}") == null) download(episodeDl(x, show, e)) }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (info == null) { Loading(); return@Column }
                if (seasons.isEmpty()) { Message(Str.noEpisodes); return@Column }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons.keys.toList(), key = { it }) { n ->
                        val on = s.season == n
                        Tile({ s.season = n }, Modifier.height(36.dp), RoundedCornerShape(18.dp),
                            colors = if (on) C.live else listOf(C.card), focusColors = C.live, focusScale = 1.04f) {
                            Box(Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Label("${Str.season} $n", 13.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    itemsIndexed(episodes, key = { i, e -> e.id + "#" + i }) { i, e ->
                        val here = saved?.takeIf { it.episodeId == e.id }
                        val dl = com.kanaani.ibo.data.Downloads.get("episode:${e.id}")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tile({
                            val w = Watch("series", show.id, show.name, show.poster, e.ext, e.id, e.season, e.number)
                            nav.go(Screen.Play("${show.name} · ${Str.episode} ${e.number}", x.episodeUrl(e), live = false, episodes = episodes, index = i,
                                watch = w, startMs = here?.pos ?: 0))
                        },
                            (if (i == 0) Modifier.focusRequester(firstEpisode) else Modifier).weight(1f).height(58.dp),
                            RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.02f) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(80.dp).height(45.dp).clip(RoundedCornerShape(8.dp)).background(C.cardHi), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Play, 16.dp, Color.White, fill = true)
                                    if (e.image != null) AsyncImage(e.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Label("${Str.episode} ${e.number}", 11.sp, C.text2)
                                    Label(e.title, 14.sp, weight = FontWeight.SemiBold)
                                    if (here != null && here.progress > 0f) Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.cardHi)) {
                                        Box(Modifier.fillMaxWidth(here.progress).fillMaxHeight().background(Brush.horizontalGradient(C.live)))
                                    }
                                }
                                e.duration?.let { Label(it, 11.sp, C.text3) }
                            }
                        }
                        // Télécharger l'épisode (ou état du téléchargement).
                        Tile({
                            when (dl?.state) {
                                null -> download(episodeDl(x, show, e))
                                com.kanaani.ibo.data.DlState.Done -> if (!playDownload(nav, dl)) nav.go(Screen.Downloads)
                                com.kanaani.ibo.data.DlState.Paused, com.kanaani.ibo.data.DlState.Failed -> com.kanaani.ibo.data.Downloads.resume(dl.key)
                                else -> nav.go(Screen.Downloads)
                            }
                        }, Modifier.size(58.dp), RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.04f) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                when (dl?.state) {
                                    null -> Icon(Icons.Download, 22.dp, Color.White)
                                    com.kanaani.ibo.data.DlState.Done -> Label("✓", 20.sp, weight = FontWeight.Bold)
                                    com.kanaani.ibo.data.DlState.Running -> Label("${(dl.progress * 100).toInt()}%", 12.sp, weight = FontWeight.Bold)
                                    com.kanaani.ibo.data.DlState.Failed -> Label("!", 20.sp, C.error, FontWeight.Bold)
                                    else -> Label("⏸", 16.sp)
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
