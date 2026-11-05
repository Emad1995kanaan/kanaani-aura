package com.kanaani.ibo.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanaani.ibo.KeyHook
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Xtream
import com.kanaani.ibo.player.Playback
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(nav: Navigator, x: Xtream, s: Screen.Play, hosted: Boolean = false) {
    val ctx = LocalContext.current
    var index by remember { mutableIntStateOf(s.index) }
    val channel = if (s.live) s.channels.getOrNull(index) else null
    val url = channel?.let { x.liveUrl(it.id) } ?: s.url
    val title = channel?.name ?: s.title
    var showInfo by remember { mutableStateOf(true) }
    var infoTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(url) { Playback.play(ctx, url, s.startMs) }
    // ── « Continuer à regarder » : position mémorisée toutes les 5 s et en quittant. ──
    var finished by remember { mutableStateOf(false) }
    fun saveProgress() {
        val w = s.watch ?: return
        if (s.live || finished || Playback.current != url) return
        val dur = Playback.duration(); val pos = Playback.position()
        if (dur <= 0) return
        val nearEnd = pos >= dur - maxOf(dur / 20, 90_000L).coerceAtMost(dur / 4)
        when {
            nearEnd && s.episodes.getOrNull(s.index + 1) == null -> Store.removeWatch(w.kind, w.id)
            nearEnd -> s.episodes.getOrNull(s.index + 1)?.let { n -> // fini : on reprendra à l'épisode suivant
                Store.saveWatch(w.copy(episodeId = n.id, ext = n.ext, season = n.season, episode = n.number, pos = 0, dur = 0))
            }
            pos >= 15_000L || Store.watchOf(w.kind, w.id)?.episodeId == w.episodeId -> Store.saveWatch(w.copy(pos = pos, dur = dur))
        }
    }
    LaunchedEffect(url) { while (true) { delay(5_000); saveProgress() } }
    androidx.compose.runtime.DisposableEffect(url) { onDispose { saveProgress() } }
    // Bandeau d'information : visible 4 s après chaque changement.
    LaunchedEffect(infoTick, index) { showInfo = true; delay(4_000); showInfo = false }
    // ── Série : épisode suivant une minute avant la fin (compte à rebours de 10 s, annulable). ──
    val next = if (!s.live) s.episodes.getOrNull(s.index + 1) else null
    var nextCountdown by remember { mutableStateOf<Long?>(null) }
    var nextCancelled by remember { mutableStateOf(false) }
    fun goNext() {
        val n = next ?: return
        val w = s.watch?.copy(episodeId = n.id, ext = n.ext, season = n.season, episode = n.number, pos = 0, dur = 0)
        w?.let { Store.saveWatch(it) }
        finished = true
        nav.replaceTop(Screen.Play(s.title.substringBefore(" · ") + " · ${Str.episode} ${n.number}", x.episodeUrl(n), live = false, episodes = s.episodes, index = s.index + 1, watch = w))
    }
    LaunchedEffect(url) {
        if (next == null) return@LaunchedEffect
        while (true) {
            delay(500)
            val dur = Playback.duration()
            if (nextCancelled || dur < 3 * 60_000L) { nextCountdown = null; continue }
            val remaining = dur - Playback.position()
            when {
                remaining <= 60_000L -> { goNext(); return@LaunchedEffect }
                remaining <= 70_000L -> nextCountdown = ((remaining - 60_000L) / 1000L).coerceAtLeast(0) + 1
                else -> nextCountdown = null
            }
        }
    }
    // Filet de sécurité : fin atteinte (durée inconnue, épisode court).
    LaunchedEffect(Playback.ended) { if (Playback.ended && next != null && !nextCancelled) goNext() }

    // ── Format de l'image : adapter / remplir / zoom (bouton, touche jaune / Menu, pincement). ──
    var aspectFlash by remember { mutableStateOf<String?>(null) }
    var aspectTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(aspectTick) { if (aspectFlash != null) { delay(1_200); aspectFlash = null } }
    fun setAspect(mode: Int) { Store.chooseAspect(mode); aspectFlash = Str.aspectName(Store.aspect); aspectTick++ }
    fun cycleAspect() = setAspect(Store.aspect + 1)

    // ── VOD : ◀ / ▶ = −10 s / +10 s, OK = pause / lecture (avant que le lecteur ne prenne la touche). ──
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashTick) { if (seekFlash != null) { delay(800); seekFlash = null } }
    val overlayShown = nextCountdown != null
    androidx.compose.runtime.DisposableEffect(s, overlayShown) {
        if (!s.live) KeyHook.handler = handler@{ e ->
            if (overlayShown) return@handler false // les boutons « Épisode suivant » utilisent les flèches
            val down = e.action == KeyEvent.ACTION_DOWN
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (down) { Playback.seekBy(10_000); seekFlash = "+10"; flashTick++ }; true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (down) { Playback.seekBy(-10_000); seekFlash = "−10"; flashTick++ }; true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (down && e.repeatCount == 0) Playback.togglePause(); true
                }
                in ASPECT_KEYS -> { if (down && e.repeatCount == 0) cycleAspect(); true }
                else -> false
            }
        }
        onDispose { KeyHook.handler = null }
    }

    fun zap(delta: Int) {
        if (s.channels.isEmpty()) return
        index = (index + delta).mod(s.channels.size)
        infoTick++
        // L'écran Direct retrouvera la chaîne en cours dans l'aperçu.
        nav.stack.filterIsInstance<Screen.Live>().lastOrNull()?.let { it.playing = s.channels[index]; it.focusIndex = index }
    }

    val focus = remember { FocusRequester() }
    if (s.live) InitialFocus(focus)

    Box(
        Modifier
            .fillMaxSize()
            // Direct : la vidéo est dessinée dessous par LiveVideoHost (surface unique) ; ici seulement les commandes.
            .background(if (hosted) Color.Transparent else Color.Black)
            // Pincement à deux doigts : écarter = zoom (plein écran), rapprocher = image entière.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var scale = 1f
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } >= 2) scale *= ev.calculateZoom()
                        if (ev.changes.none { it.pressed }) break
                    }
                    if (scale > 1.15f && Store.aspect != 2) setAspect(2)
                    else if (scale < 0.87f && Store.aspect != 0) setAspect(0)
                }
            }
            .then(
                if (s.live) Modifier
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); true }
                            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> { infoTick++; true }
                            // ◀ ▶ (inutilisés en direct), touche jaune ou Menu : format de l'image.
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, in ASPECT_KEYS -> { cycleAspect(); infoTick++; true }
                            else -> false
                        }
                    }
                    .focusable()
                else Modifier,
            ),
    ) {
        var playerView by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
        val videoView = if (hosted) LiveVideo.view else playerView
        LaunchedEffect(videoView, Store.aspect) {
            videoView?.resizeMode = when (Store.aspect) {
                1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
        var controlsVisible by remember { mutableStateOf(true) }
        if (!hosted) PlayerSurface(controls = !s.live, onView = { v ->
            playerView = v
            v.setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { vis -> controlsVisible = vis == android.view.View.VISIBLE })
        })
        // VOD au toucher (contrôles masqués) : double appui à droite = +10 s, à gauche = −10 s ; appui simple = contrôles.
        if (!s.live && !controlsVisible) Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { o ->
                        if (o.x > size.width / 2f) { Playback.seekBy(10_000); seekFlash = "+10" } else { Playback.seekBy(-10_000); seekFlash = "−10" }
                        flashTick++
                    },
                    onTap = { playerView?.showController() },
                )
            },
        )
        // Direct au toucher : un appui sur l'image affiche le bandeau (et les boutons ▲ ▼).
        if (s.live) Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { infoTick++ })

        if (Playback.error) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(Str.playError, 16.sp, C.text, FontWeight.SemiBold)
                Tile({ Playback.retry(ctx) }, Modifier.width(200.dp).height(42.dp), RoundedCornerShape(12.dp), colors = C.live) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.retry, 15.sp, weight = FontWeight.Bold) }
                }
            }
        }

        seekFlash?.let { f ->
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(40.dp)).background(Color(0x99000000)).padding(horizontal = 26.dp, vertical = 14.dp)) {
                Label("$f s", 26.sp, weight = FontWeight.Bold)
            }
        }

        aspectFlash?.let { f ->
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(40.dp)).background(Color(0x99000000)).padding(horizontal = 26.dp, vertical = 14.dp)) {
                Label("⛶  $f", 22.sp, weight = FontWeight.Bold)
            }
        }
        // Films / séries : titre en haut à droite, avec les contrôles, et tout le temps de la pause.
        if (!s.live) {
            var paused by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { while (true) { paused = Playback.player(ctx).let { !it.playWhenReady }; kotlinx.coroutines.delay(400) } }
            if (controlsVisible || paused) Box(
                Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopRight).padding(top = 18.dp, end = 22.dp).fillMaxWidth(0.55f),
                contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopRight,
            ) {
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xAA000000)).padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Label(title, 20.sp, weight = FontWeight.Bold, lines = 2)
                }
            }
        }
        // Bouton format (tactile) : visible avec les contrôles (VOD) ou le bandeau (direct). En haut à gauche (le titre est à droite).
        if ((!s.live && controlsVisible) || (s.live && showInfo)) {
            Box(
                Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).padding(18.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x99000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { cycleAspect(); if (s.live) infoTick++ }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) { Label("⛶  " + Str.aspectName(Store.aspect), 14.sp, weight = FontWeight.Bold) }
        }

        // Épisode suivant : carte en bas à droite avec compte à rebours.
        nextCountdown?.let { secs ->
            val playNow = remember { FocusRequester() }
            LaunchedEffect(Unit) { InitialFocusOnce(playNow) }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(28.dp).width(300.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE60E1736)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Label(Str.nextEpisode + " · ${Str.episode} ${next?.number ?: ""}", 15.sp, weight = FontWeight.Bold)
                Label(Str.nextIn(secs), 13.sp, C.text2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile({ goNext() }, Modifier.weight(1f).height(38.dp).focusRequester(playNow), RoundedCornerShape(10.dp), colors = C.live, focusScale = 1.03f) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.playNow, 13.sp, weight = FontWeight.Bold) }
                    }
                    Tile({ nextCancelled = true; nextCountdown = null }, Modifier.weight(1f).height(38.dp), RoundedCornerShape(10.dp),
                        colors = listOf(C.card), focusColors = C.live, focusScale = 1.03f) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.cancel, 13.sp, weight = FontWeight.SemiBold) }
                    }
                }
            }
        }

        // Direct : bandeau chaîne (numéro, logo, nom) + boutons tactiles ▲ ▼.
        if (s.live && showInfo) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6040817))))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label("${channel?.number?.takeIf { it > 0 } ?: (index + 1)}", 22.sp, weight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                AsyncImage(channel?.logo, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(C.card), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(14.dp))
                Label(title, 20.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (s.channels.size > 1) {
                    ZapButton("▲") { zap(+1) }
                    Spacer(Modifier.width(10.dp))
                    ZapButton("▼") { zap(-1) }
                }
            }
        }
    }
}

@Composable
private fun ZapButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x66FFFFFF))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(label, 18.sp, weight = FontWeight.Bold) }
}

/** Touches qui changent le format de l'image (touche jaune, Menu, Zoom de la télécommande). */
private val ASPECT_KEYS = setOf(KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_TV_ZOOM_MODE)
