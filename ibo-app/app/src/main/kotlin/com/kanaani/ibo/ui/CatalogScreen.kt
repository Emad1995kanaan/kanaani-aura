package com.kanaani.ibo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanaani.ibo.data.Category
import com.kanaani.ibo.data.Episode
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Watch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.kanaani.ibo.data.Movie
import com.kanaani.ibo.data.Show
import com.kanaani.ibo.data.Xtream

/** Affiche commune aux films et aux séries. */
data class Poster(val id: String, val name: String, val image: String?, val rating: String?, val movie: Movie? = null, val show: Show? = null, val watch: Watch? = null)

/** Lance la reprise d'un film / d'un épisode depuis « Continuer à regarder ». */
fun resumeWatch(nav: Navigator, x: Xtream, scope: CoroutineScope, w: Watch) {
    if (w.kind == "movie") {
        val m = Movie(w.id, w.name, w.image, null, w.ext.ifEmpty { "mp4" }, null)
        nav.go(Screen.Play(w.name, x.movieUrl(m), live = false, watch = w, startMs = w.pos))
        return
    }
    scope.launch {
        // Liste des épisodes de la saison pour garder l'enchaînement automatique.
        val eps = runCatching { x.showInfo(w.id)?.seasons?.get(w.season) }.getOrNull().orEmpty()
        val i = eps.indexOfFirst { it.id == w.episodeId }
        val ep = eps.getOrNull(i) ?: Episode(w.episodeId, "", w.episode, w.season, w.ext.ifEmpty { "mp4" }, null, null, null)
        nav.go(Screen.Play("${w.name} · ${Str.episode} ${ep.number}", x.episodeUrl(ep), live = false,
            episodes = if (i >= 0) eps else emptyList(), index = maxOf(i, 0), watch = w.copy(ext = ep.ext), startMs = w.pos))
    }
}

@Composable
fun CatalogScreen(nav: Navigator, x: Xtream, s: Screen.Catalog) {
    val serverCategories by produceState<List<Category>?>(null, x, s.series) {
        value = if (s.series) x.showCategories() else x.movieCategories()
    }
    // Ordre choisi dans Réglages › Ordre des films / séries.
    val categories = remember(serverCategories, com.kanaani.ibo.data.Store.categoryOrders, com.kanaani.ibo.data.Store.categoryNames, com.kanaani.ibo.data.Store.hiddenCategories, com.kanaani.ibo.data.Store.language, com.kanaani.ibo.data.Translations.he) {
        serverCategories?.let { com.kanaani.ibo.data.Store.arrange(if (s.series) "series" else "movie", it) }
    }
    var reload by remember { mutableIntStateOf(0) }
    // catalogVersion : liste « Tous » rafraîchie en arrière-plan → mise à jour sans repasser par le chargement.
    var loadedFor by remember { mutableStateOf<Pair<String?, Int>?>(null) }
    val items by produceState<List<Poster>?>(null, s.category, reload, x.catalogVersion) {
        if (loadedFor != (s.category to reload)) value = null
        loadedFor = s.category to reload
        val c = s.category ?: run { value = emptyList(); return@produceState }
        val cat = if (c == Screen.ALL) null else c
        value = when {
            c == Screen.CONTINUE -> emptyList()
            c == Screen.LATEST && s.series -> x.latestShows().map { Poster(it.id, it.name, it.poster, it.rating, show = it) }
            c == Screen.LATEST -> x.latestMovies().map { Poster(it.id, it.name, it.poster, it.rating, movie = it) }
            s.series -> x.shows(cat).map { Poster(it.id, it.name, it.poster, it.rating, show = it) }
            else -> x.movies(cat).map { Poster(it.id, it.name, it.poster, it.rating, movie = it) }
        }
    }
    val kind = if (s.series) "series" else "movie"
    val continuing = remember(Store.watching, kind) {
        Store.watching.filter { it.kind == kind }.map { w ->
            val sub = if (w.kind == "series" && w.episode > 0) "${w.name} · S${w.season} E${w.episode}" else w.name
            Poster(w.id, sub, w.image, null, watch = w)
        }
    }
    val scope = rememberCoroutineScope()
    val source = if (s.category == Screen.CONTINUE) continuing else items
    val shown = remember(source, s.filter) {
        val items = source
        val q = s.filter.trim()
        items?.let { list -> if (q.isEmpty()) list else list.filter { it.name.contains(q, ignoreCase = true) } }
    }

    val catFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    // Retour depuis une fiche : le focus revient sur l'affiche ouverte ; sinon sur la première catégorie.
    LaunchedEffect(source) {
        val list = shown ?: return@LaunchedEffect
        if (s.focusIndex >= 0 && list.isNotEmpty()) {
            runCatching { s.gridState.scrollToItem(s.focusIndex.coerceIn(0, list.lastIndex)) }
            InitialFocusOnce(gridFocus)
        } else InitialFocusOnce(catFocus)
    }

    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Header(if (s.series) Str.series else Str.movies)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(300.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color(0xE6000000)).padding(vertical = 8.dp)) {
                    val cats = categories
                    when {
                        cats == null -> Loading()
                        cats.isEmpty() -> Message(Str.empty)
                        else -> LazyColumn(state = s.catState) {
                            // Recherche dans les catégories, au-dessus de « Tous ».
                            item(key = "catsearch") {
                                SearchBox(s.catSearching, { s.catSearching = it }, s.catFilter, { s.catFilter = it }, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), label = Str.searchCats)
                            }
                            // « Tous » en tête de liste.
                            val fixed = listOf(Category(Screen.ALL, Str.all), Category(Screen.CONTINUE, Str.continueWatching),
                                Category(Screen.LATEST, if (s.series) Str.latestSeries else Str.latestMovies))
                            val cq = s.catFilter.trim()
                            val all = (fixed + cats).let { l -> if (cq.isEmpty()) l else l.filter { it.name.contains(cq, ignoreCase = true) } }
                            itemsIndexed(all, key = { i, c -> c.id + "#" + i }) { i, c ->
                                val selected = s.category == c.id
                                val m = if (selected || (i == 0 && s.category == null)) Modifier.focusRequester(catFocus) else Modifier
                                Tile({ if (s.category != c.id) { s.category = c.id; s.focusIndex = -1; s.filter = ""; s.searching = false } },
                                    m.fillMaxWidth().height(48.dp), RoundedCornerShape(0.dp),
                                    colors = listOf(if (selected) Color(0x552F6BFF) else Color.Transparent), focusColors = C.live, focusScale = 1f) {
                                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Text(String.format(java.util.Locale.ROOT, "%02d", i + 1), color = C.text2, fontFamily = Sora, fontSize = 15.sp, modifier = Modifier.width(34.dp))
                                        Label(c.name, 15.sp, C.text, if (selected) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchBox(s.searching, { s.searching = it }, s.filter, { s.filter = it }, Modifier.width(320.dp))
                    val list = shown
                    when {
                        list == null -> Loading()
                        list.isEmpty() -> Message(if (items == null) Str.loading else Str.empty)
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            state = s.gridState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
                        ) {
                            itemsIndexed(list, key = { i, p -> p.id + "#" + i }) { i, p ->
                                val target = if (s.focusIndex >= 0) s.focusIndex.coerceIn(0, list.lastIndex) else 0
                                val m = if (i == target) Modifier.focusRequester(gridFocus) else Modifier
                                PosterCard(p, m.onFocusChanged { if (it.isFocused) s.focusIndex = i }) {
                                    s.focusIndex = i
                                    if (p.watch != null) resumeWatch(nav, x, scope, p.watch)
                                    else if (p.movie != null) nav.go(Screen.MovieDetail(p.movie)) else if (p.show != null) nav.go(Screen.ShowDetail(p.show))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(p: Poster, modifier: Modifier, onClick: () -> Unit) {
    // Aura : affiche seule, étoiles en haut, titre écrit en bas sur l'affiche.
    Tile(onClick, modifier.fillMaxWidth().aspectRatio(2f / 3f), RoundedCornerShape(10.dp), colors = listOf(C.card), focusScale = 1.06f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Label(p.name, 12.sp, C.text3, lines = 3, modifier = Modifier.padding(8.dp))
        }
        com.kanaani.ibo.data.PosterImage(p.image, if (p.show != null || p.watch?.kind == "series") "tv" else "movie", p.watch?.name ?: p.name, Modifier.fillMaxSize())
        // Étoiles (note sur 10 → 5 étoiles)
        val r = p.rating?.toDoubleOrNull()?.takeIf { it > 0 }
        if (r != null) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent))).padding(horizontal = 6.dp, vertical = 5.dp)) {
            val full = (r / 2).coerceIn(0.0, 5.0)
            androidx.compose.material3.Text(buildString { repeat(5) { k -> append(if (full >= k + 0.5) "★" else "☆") } },
                color = Color(0xFFFFC72C), fontSize = 14.sp, maxLines = 1)
        }
        // Titre en bas, sur l'affiche
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 8.dp)) {
            Label(p.name, 14.sp, Color.White, FontWeight.Bold, lines = 2)
        }
        val w = p.watch
        if (w != null && w.progress > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(Color(0xAA000000))) {
            Box(Modifier.fillMaxWidth(w.progress).fillMaxHeight().background(Brush.horizontalGradient(C.live)))
        }
    }
}
