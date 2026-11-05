package com.kanaani.ibo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanaani.ibo.Brand
import com.kanaani.ibo.BuildConfig
import com.kanaani.ibo.data.Ads
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Xtream
import kotlinx.coroutines.delay

// ───────────── Thèmes d'accueil : 0 = classique, 1 = panneau publicitaire ─────────────

/**
 * Diaporama du panneau : publicités et/ou derniers films (image qui avance lentement, titre et note), une image toutes
 * les [Ads.interval] secondes. Purement décoratif : jamais focalisable ni cliquable.
 */
@Composable
fun AdCarousel(slides: List<com.kanaani.ibo.data.Slide>, modifier: Modifier, corner: Dp = 18.dp) {
    var index by remember(slides) { mutableIntStateOf(0) }
    LaunchedEffect(slides, Ads.interval) {
        if (slides.size < 2) return@LaunchedEffect
        while (true) { delay(Ads.interval * 1000L); index = (index + 1) % slides.size }
    }
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(C.cardHi, C.panel)))) {
        if (slides.isEmpty()) {
            // Rien à montrer (pas encore chargé) : logo de la marque.
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Logo(72.dp)
                Spacer(Modifier.width(14.dp))
                Text(Brand.name, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            }
        } else {
            Crossfade(slides[index.coerceIn(0, slides.lastIndex)], animationSpec = tween(800), label = "ad") { s ->
                Box(Modifier.fillMaxSize()) {
                    if (s.title == null) AsyncImage(s.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else {
                        // Film : l'image avance doucement (zoom + glissement) pendant qu'elle est affichée.
                        val move = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(s) { move.animateTo(1f, tween(Ads.interval * 1000 + 1500, easing = androidx.compose.animation.core.LinearEasing)) }
                        AsyncImage(s.image, null, Modifier.fillMaxSize().graphicsLayer {
                            val k = 1.04f + 0.10f * move.value
                            scaleX = k; scaleY = k
                            translationX = (move.value - 0.5f) * size.width * 0.05f
                        }, contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6000000))))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 26.dp)) {
                            Text(s.title, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, maxLines = 2)
                            if (s.rating != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5C518)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("★ " + s.rating, color = Color.Black, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Label(Str.latestMovies, 13.sp, C.text2, FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (slides.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slides.indices.forEach { i ->
                    Box(Modifier.height(6.dp).width(if (i == index) 20.dp else 6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i == index) Color.White else Color(0x88FFFFFF)))
                }
            }
        }
    }
}

/** Publicités puis films : une publicité, 3 films, une publicité… (films seuls s'il n'y a pas de publicité). */
fun billboardSlides(ads: List<String>, movies: List<com.kanaani.ibo.data.Slide>, every: Int = 3): List<com.kanaani.ibo.data.Slide> {
    val a = ads.map { com.kanaani.ibo.data.Slide(it) }
    if (a.isEmpty()) return movies
    if (movies.isEmpty()) return a
    val out = mutableListOf<com.kanaani.ibo.data.Slide>()
    var m = 0
    for (ad in a) { out += ad; repeat(every) { if (m < movies.size) out += movies[m++] } }
    while (m < movies.size) out += movies[m++]
    return out
}

@Composable
private fun ThemeHeader(now: Long, logo: Dp = 54.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo(logo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Brand.name, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, maxLines = 1)
            Label(Str.welcome, 14.sp, C.text2, FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(clock(now), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
            Label(date(now, "EEEE d MMMM"), 11.sp, C.text2)
        }
    }
}

@Composable
private fun StackTile(label: String, icon: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = colors, focusScale = 1.04f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 40.dp, Color.White, stroke = 1.5f)
            Spacer(Modifier.width(14.dp))
            Text(label, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 1)
        }
    }
}

@Composable
private fun BottomTile(label: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxHeight(), RoundedCornerShape(12.dp), colors = C.small, focusColors = C.live) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, 22.dp, Color.White, stroke = 1.8f)
            Spacer(Modifier.width(10.dp))
            Label(label, 15.sp, weight = FontWeight.SemiBold)
        }
    }
}

/** Thème « panneau publicitaire » : Direct / Films / Séries empilés à droite, publicité 16:9 à côté, raccourcis en bas. */
@Composable
fun BillboardHome(nav: Navigator, x: Xtream, now: Long) {
    LaunchedEffect(x, Ads.movieCount) { com.kanaani.ibo.data.Promo.refresh(x, Ads.movieCount) }
    val first = remember { FocusRequester() }
    InitialFocus(first)
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
            ThemeHeader(now)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StackTile(Str.live, Icons.Live, C.live, Modifier.weight(1f).focusRequester(first)) { nav.go(Screen.Live()) }
                    StackTile(Str.movies, Icons.Movies, C.movies, Modifier.weight(1f)) { nav.go(Screen.Catalog(series = false)) }
                    StackTile(Str.series, Icons.Series, C.series, Modifier.weight(1f)) { nav.go(Screen.Catalog(series = true)) }
                }
                val slides = remember(Ads.billboard, Ads.pinned, Ads.mode, Ads.every, com.kanaani.ibo.data.Promo.movies) {
                    val movies = Ads.pinned + com.kanaani.ibo.data.Promo.movies
                    when (Ads.mode) {
                        "none" -> emptyList()
                        "ads" -> billboardSlides(Ads.billboard, emptyList())
                        "movies" -> movies
                        else -> billboardSlides(Ads.billboard, movies, Ads.every)
                    }
                }
                AdCarousel(slides, Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BottomTile(Str.favorites, Icons.Heart, Modifier.weight(1f)) { nav.go(Screen.Favorites) }
                BottomTile(Str.account, Icons.Account, Modifier.weight(1f)) { nav.go(Screen.Account) }
                BottomTile(Str.downloads, Icons.Download, Modifier.weight(1f)) { nav.go(Screen.Downloads) }
                BottomTile(Str.settings, Icons.Settings, Modifier.weight(1f)) { nav.go(Screen.Settings) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(expiryLine(AccountCache.value), 11.sp, C.text2, modifier = Modifier.weight(1f))
                Label("v${BuildConfig.VERSION_NAME}", 10.sp, C.text3)
            }
        }
    }
}
