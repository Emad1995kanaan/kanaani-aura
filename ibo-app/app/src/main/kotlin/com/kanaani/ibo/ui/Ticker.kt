package com.kanaani.ibo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanaani.ibo.data.Messages
import kotlinx.coroutines.delay

/** Messages « popup » / « plein écran » déjà fermés (par ouverture de l'application). */
private var closed by mutableStateOf<Set<String>>(emptySet())

/**
 * Messages du panneau / ntfy, selon le style choisi : bandeau défilant en bas (ticker), bandeau en haut (top),
 * fenêtre au centre (popup), plein écran (full) ou carte dans le coin (corner).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageTicker(top: Screen) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val live = top is Screen.Live || (top is Screen.Play && top.live)
    val active = Messages.list.filter { it.until > now && (!it.liveOnly || live) && it.id !in closed }
    fun last(vararg st: String) = active.lastOrNull { it.style in st }

    // Bandeau en bas (et anciens messages sans style).
    val ticker = last("ticker")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(ticker != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            val m = ticker ?: return@AnimatedVisibility
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6000000)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(Brush.verticalGradient(C.live)))
                Spacer(Modifier.width(12.dp))
                Text("📢", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    m.text, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(end = 14.dp)
                        .basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately, initialDelayMillis = 800),
                )
            }
        }
    }
    // Bandeau en haut.
    val topMsg = last("top")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(topMsg != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            val m = topMsg ?: return@AnimatedVisibility
            Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(C.live)).padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text("📢  " + m.text, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately, initialDelayMillis = 800))
            }
        }
    }
    // Carte dans le coin.
    val corner = last("corner")
    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(corner != null, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut()) {
            val m = corner ?: return@AnimatedVisibility
            Row(Modifier.width(300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xEE111827)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(5.dp).height(64.dp).background(Brush.verticalGradient(C.live)))
                Text("📢 " + m.text, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 3,
                    modifier = Modifier.padding(12.dp))
            }
        }
    }
    // Plein écran (message important) puis fenêtre : à fermer avec OK.
    val full = last("full")
    val popup = last("popup")
    if (full != null) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xF27F1D1D), Color(0xF2111827)))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                Text("🚨", fontSize = 48.sp)
                Spacer(Modifier.height(14.dp))
                Text(full.text, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                val ok = remember { androidx.compose.ui.focus.FocusRequester() }
                InitialFocus(ok, full.id)
                androidx.activity.compose.BackHandler { closed = closed + full.id }
                Tile({ closed = closed + full.id }, Modifier.width(200.dp).height(48.dp).then(Modifier.focusRequester(ok)), RoundedCornerShape(12.dp), colors = C.live) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.ok, 17.sp, weight = FontWeight.Bold) }
                }
            }
        }
    } else if (popup != null) {
        ChoiceDialog("📢 " + popup.text, listOf(Str.ok to { closed = closed + popup.id })) { closed = closed + popup.id }
    }
}
