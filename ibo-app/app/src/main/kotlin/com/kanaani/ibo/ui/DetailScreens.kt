package com.kanaani.ibo.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Text(com.kanaani.ibo.data.Promo.clean(m.name), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.sp, maxLines = 2)
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
                    info?.trailer?.takeIf { it.isNotBlank() }?.let { t ->
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        ActionButton(Str.trailer, Icons.Play, false) {
                            val url = if (t.startsWith("http")) t else "https://www.youtube.com/watch?v=$t"
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
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

    var seasonPick by remember { mutableStateOf(false) }
    DetailBackdrop(info?.backdrop ?: art?.backdrop ?: show.poster) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(com.kanaani.ibo.data.Promo.clean(show.name), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 46.sp, maxLines = 1)
            (info?.plot?.takeIf { it.isNotBlank() } ?: art?.overview)?.let { Label(it, 15.sp, C.text, FontWeight.Normal, lines = 2, modifier = Modifier.fillMaxWidth(0.75f)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Meta(listOf(info?.genre, info?.year))
                Stars(info?.rating ?: show.rating)
            }
            info?.cast?.let { Label("${Str.cast}: $it", 13.sp, C.text2) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (seasons.isNotEmpty()) ActionButton("▾  ${Str.season} ${s.season ?: ""}", Icons.Series, false) { seasonPick = true }
                if (saved != null && saved.episodeId.isNotEmpty()) ActionButton("${Str.resume}  S${saved.season} E${saved.episode}", Icons.Play, true) { resumeWatch(nav, x, scope, saved) }
                ActionButton(if (fav) Str.removeFav else Str.addFav, Icons.Star, false, fill = fav) { Store.toggleFavorite(Favorite("series", show.id, show.name, show.poster)) }
                if (episodes.isNotEmpty()) ActionButton(Str.downloadSeason, Icons.Download, false) {
                    episodes.forEach { e -> if (com.kanaani.ibo.data.Downloads.get("episode:${e.id}") == null) download(episodeDl(x, show, e)) }
                }
            }
            when {
                info == null -> Loading()
                seasons.isEmpty() -> Message(Str.noEpisodes)
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    itemsIndexed(episodes, key = { i, e -> e.id + "#" + i }) { i, e ->
                        val here = saved?.takeIf { it.episodeId == e.id }
                        Tile({
                            val w = Watch("series", show.id, show.name, show.poster, e.ext, e.id, e.season, e.number)
                            nav.go(Screen.Play("${show.name} · S${e.season}.E${e.number}", x.episodeUrl(e), live = false, episodes = episodes, index = i,
                                watch = w, startMs = here?.pos ?: 0))
                        }, (if (i == 0) Modifier.focusRequester(firstEpisode) else Modifier).width(280.dp).height(158.dp), RoundedCornerShape(12.dp),
                            colors = listOf(C.card), focusScale = 1.05f) {
                            AsyncImage(e.image ?: info?.backdrop ?: art?.backdrop ?: show.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xD9000000)))))
                            Column(Modifier.align(androidx.compose.ui.AbsoluteAlignment.BottomLeft).padding(12.dp)) {
                                Text("#${e.number}", color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                                Label(e.title, 14.sp, Color.White, FontWeight.SemiBold)
                            }
                            if (here != null && here.progress > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                                Box(Modifier.fillMaxWidth(here.progress).fillMaxHeight().background(Brush.horizontalGradient(C.live)))
                            }
                        }
                    }
                }
            }
        }
        if (seasonPick) ChoiceDialog(Str.season, seasons.keys.map { n -> "${if (n == s.season) "✓  " else ""}${Str.season} $n" to { s.season = n; seasonPick = false } }) { seasonPick = false }
    }
}
