package com.kanaani.ibo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanaani.ibo.BuildConfig
import com.kanaani.ibo.data.Updater
import com.kanaani.ibo.data.Updater.Phase
import kotlinx.coroutines.launch

/** Fenêtre « Nouvelle mise à jour » (au-dessus de tous les écrans). */
@Composable
fun UpdateDialog() {
    val phase = Updater.phase
    val visible = when (phase) {
        Phase.Available -> !Updater.dismissed
        Phase.Downloading, Phase.Ready, Phase.Failed, Phase.UpToDate -> true
        Phase.Idle -> false
    }
    if (!visible) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    InitialFocus(first, phase)
    BackHandler { if (phase != Phase.Downloading) Updater.later() }
    val info = Updater.info

    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(44.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    when (phase) {
                        Phase.UpToDate -> Str.upToDate
                        Phase.Failed -> Str.updateFailed
                        else -> Str.updateAvailable
                    },
                    color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                )
            }
            when (phase) {
                Phase.Available, Phase.Ready -> {
                    Label("${Str.version} ${info?.name ?: ""}  ·  ${Str.current} ${BuildConfig.VERSION_NAME}", 13.sp, C.text2)
                    info?.notes?.takeIf { it.isNotBlank() }?.let { Label(it, 13.sp, C.text, lines = 5) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogButton(if (phase == Phase.Ready) Str.install else Str.updateNow, true, Modifier.weight(1f).focusRequester(first)) {
                            if (phase == Phase.Ready) Updater.install(ctx) else scope.launch { Updater.downloadAndInstall(ctx) }
                        }
                        DialogButton(Str.later, false, Modifier.weight(1f)) { Updater.later() }
                    }
                    if (phase == Phase.Ready) Label(Str.installHint, 12.sp, C.text2, lines = 3)
                }
                Phase.Downloading -> {
                    Label(Str.downloading + "  ${(Updater.progress * 100).toInt()}%", 14.sp, C.text2)
                    LinearProgressIndicator(
                        progress = { Updater.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = C.accent, trackColor = C.card,
                    )
                    Box(Modifier.focusRequester(first)) {}
                }
                Phase.UpToDate -> {
                    Label("${Str.version} ${BuildConfig.VERSION_NAME}", 13.sp, C.text2)
                    DialogButton(Str.ok, true, Modifier.fillMaxWidth().focusRequester(first)) { Updater.later() }
                }
                Phase.Failed -> {
                    Label(
                        when (Updater.problem) {
                            Updater.Problem.Corrupt -> Str.problemCorrupt
                            Updater.Problem.OldVersion -> Str.problemOld
                            Updater.Problem.Signature -> Str.problemSignature
                            Updater.Problem.Install -> Str.problemInstall
                            else -> Str.problemDownload
                        },
                        13.sp, C.text, lines = 4,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (Updater.info != null && Updater.problem != Updater.Problem.OldVersion && Updater.problem != Updater.Problem.Signature) {
                            DialogButton(Str.retryUpdate, true, Modifier.weight(1f).focusRequester(first)) { scope.launch { Updater.downloadAndInstall(ctx) } }
                            DialogButton(Str.later, false, Modifier.weight(1f)) { Updater.later() }
                        } else DialogButton(Str.ok, true, Modifier.weight(1f).focusRequester(first)) { Updater.later() }
                    }
                }
                Phase.Idle -> {}
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.height(44.dp), RoundedCornerShape(12.dp), colors = if (primary) C.live else listOf(C.card), focusColors = C.live, focusScale = 1.03f) {
        Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Label(label, 15.sp, weight = FontWeight.Bold) }
    }
}
