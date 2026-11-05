package com.kanaani.ibo.ui

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kanaani.ibo.data.Xtream
import com.kanaani.ibo.player.Playback
import kotlin.math.roundToInt

/**
 * Vidéo du Direct : UNE seule surface pour l'aperçu et le plein écran. Elle reste au même endroit de l'arbre et ne fait
 * que changer de taille : le décodeur ne change jamais de surface (sinon, sur certains appareils, image figée + son).
 */
object LiveVideo {
    /** Cadre de l'aperçu dans l'écran Direct (coordonnées racine, px). */
    var previewBounds by mutableStateOf<Rect?>(null)
    var view by mutableStateOf<PlayerView?>(null)
}

/** Posé au-dessus des écrans : aperçu (écran Direct) ou plein écran avec ses commandes (Direct en plein écran). */
@Composable
fun LiveVideoHost(nav: Navigator, x: Xtream, top: Screen) {
    val full = top is Screen.Play && top.live
    val preview = top is Screen.Live && top.playing != null && Playback.current != null
    if (!full && !preview) return
    val density = LocalDensity.current
    val b = LiveVideo.previewBounds
    // Coin haut-GAUCHE absolu (pas « début » : en arabe / hébreu le début est à droite).
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
        val frame = if (full) Modifier.fillMaxSize()
        else if (b == null) Modifier.size(0.dp)
        else with(density) {
            Modifier.absoluteOffset { IntOffset(b.left.roundToInt(), b.top.roundToInt()) }.size(b.width.toDp(), b.height.toDp())
        }
        Box(frame.background(Color.Black), contentAlignment = Alignment.Center) {
            PlayerSurface(controls = false, onView = { LiveVideo.view = it })
            // Logo de l'appli en filigrane (coin bas-gauche, semi-transparent) pendant le direct.
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.kanaani.ibo.R.drawable.watermark), contentDescription = null,
                modifier = Modifier.align(androidx.compose.ui.AbsoluteAlignment.BottomLeft)
                    .absolutePadding(left = if (full) 32.dp else 10.dp, bottom = if (full) 28.dp else 8.dp)
                    .height(if (full) 54.dp else 22.dp)
                    .graphicsLayer { alpha = 0.4f },
            )
            if (!full && Playback.error) Label(Str.playError, 13.sp, C.error, lines = 2, modifier = Modifier.padding(16.dp))
        }
        // Aperçu : toujours l'image entière ; le format choisi s'applique en plein écran.
        LaunchedEffect(LiveVideo.view, full) { if (!full) LiveVideo.view?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT }
        if (full) PlayerScreen(nav, x, top as Screen.Play, hosted = true)
    }
}
