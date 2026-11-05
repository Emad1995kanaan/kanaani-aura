package com.kanaani.ibo.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanaani.ibo.data.Dl
import com.kanaani.ibo.data.DlState
import com.kanaani.ibo.data.Downloads
import com.kanaani.ibo.data.Episode
import com.kanaani.ibo.data.Movie
import com.kanaani.ibo.data.Show
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Watch
import com.kanaani.ibo.data.Xtream
import kotlinx.coroutines.delay

fun movieDl(x: Xtream, m: Movie) = Dl("movie:${m.id}", "movie", m.id, m.name, m.poster, x.movieUrl(m), m.ext.ifBlank { "mp4" })

fun episodeDl(x: Xtream, show: Show, e: Episode) = Dl(
    "episode:${e.id}", "episode", e.id, "${show.name} · S${e.season} E${e.number}", e.image ?: show.poster, x.episodeUrl(e),
    e.ext.ifBlank { "mp4" }, show.id, show.name, e.season, e.number,
)

fun sizeText(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.1f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(java.util.Locale.ROOT, "%.0f MB", b / (1L shl 20).toDouble())
    else -> "${b / 1024} KB"
}

/** Ajoute un téléchargement ; Android 13+ : demande une fois l'autorisation des notifications (barre de progression). */
@Composable
fun rememberDownloader(): (Dl) -> Unit {
    var pending by remember { mutableStateOf<List<Dl>>(emptyList()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending.forEach { Downloads.add(it) }; pending = emptyList()
    }
    val ctx = LocalContext.current
    return { d ->
        val granted = Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) Downloads.add(d) else { pending = pending + d; runCatching { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }.onFailure { Downloads.add(d) } }
    }
}

/** Lecture d'un fichier téléchargé (si l'abonnement a été vérifié récemment). Renvoie false sinon. */
fun playDownload(nav: Navigator, d: Dl): Boolean {
    if (!Downloads.canPlayOffline()) return false
    val url = Uri.fromFile(Downloads.file(d)).toString()
    val w = if (d.kind == "movie") Watch("movie", d.id, d.title, d.image, d.ext)
    else Watch("series", d.showId, d.showName, d.image, d.ext, d.id, d.season, d.episode)
    val start = Store.watchOf(w.kind, w.id)?.takeIf { it.episodeId == w.episodeId }?.pos ?: 0L
    nav.go(Screen.Play(d.title, url, live = false, watch = w, startMs = start))
    return true
}

/** Bouton d'état d'un téléchargement (fiche film / épisode). */
@Composable
fun dlLabel(d: Dl?): String = when (d?.state) {
    null -> Str.download
    DlState.Done -> "✓ " + Str.downloaded
    DlState.Running -> "${Str.dlRunning} ${(d.progress * 100).toInt()}%"
    DlState.Queued -> Str.dlQueued
    DlState.Paused -> Str.dlPaused
    DlState.Failed -> Str.dlFailed
}

@Composable
fun DownloadsScreen(nav: Navigator) {
    val list = Downloads.list
    var toast by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Dl?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(3_000); toast = null } }
    val first = remember { FocusRequester() }
    LaunchedEffect(list.isEmpty()) { if (list.isNotEmpty()) InitialFocusOnce(first) }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header(Str.downloads)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("${Str.freeSpace}: ${sizeText(Downloads.freeBytes())}", 12.sp, C.text2, modifier = Modifier.weight(1f))
                Label(Str.pausedWhileWatching, 11.sp, C.text3)
            }
            if (list.isEmpty()) { Message(Str.noDownloads); return@Column }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(list, key = { i, d -> d.key + "#" + i }) { i, d ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tile({
                            when (d.state) {
                                DlState.Done -> if (!playDownload(nav, d)) toast = Str.offlineLocked
                                DlState.Running, DlState.Queued -> Downloads.pause(d.key)
                                DlState.Paused, DlState.Failed -> Downloads.resume(d.key)
                            }
                        }, (if (i == 0) Modifier.focusRequester(first) else Modifier).weight(1f).height(72.dp),
                            RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.02f) {
                            Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(100.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(C.cardHi), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Play, 18.dp, Color.White, fill = true)
                                    if (d.image != null) AsyncImage(d.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Label(d.title, 15.sp, weight = FontWeight.SemiBold)
                                    val action = when (d.state) {
                                        DlState.Done -> "▶ " + Str.play
                                        DlState.Running, DlState.Queued -> "⏸ " + Str.pause
                                        else -> "⬇ " + Str.resumeDl
                                    }
                                    Label(dlLabel(d) + (if (d.total > 0) " · " + sizeText(d.total) else "") + "   " + action, 12.sp,
                                        if (d.state == DlState.Failed) C.error else C.text2)
                                    if (d.state != DlState.Done) Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.cardHi)) {
                                        Box(Modifier.fillMaxWidth(d.progress).fillMaxHeight().background(Brush.horizontalGradient(C.live)))
                                    }
                                }
                            }
                        }
                        Tile({ deleting = d }, Modifier.size(56.dp), RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.danger) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("🗑", 20.sp) }
                        }
                    }
                }
            }
        }
        toast?.let { Box(Modifier.align(Alignment.BottomCenter).padding(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xE6000000)).padding(14.dp)) { Label(it, 14.sp, lines = 2) } }
        deleting?.let { d ->
            ChoiceDialog(Str.deleteAsk + "\n" + d.title, listOf(Str.delete to { Downloads.delete(d.key); deleting = null }, Str.cancel to { deleting = null })) { deleting = null }
        }
    }
}
