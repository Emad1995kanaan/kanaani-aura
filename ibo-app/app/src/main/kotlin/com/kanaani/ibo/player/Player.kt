package com.kanaani.ibo.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kanaani.ibo.data.Http

/**
 * Lecteur unique de l'application : l'aperçu du Direct et le plein écran affichent le MÊME lecteur, donc passer en
 * plein écran ne recharge pas le flux (comme IBO Player).
 */
object Playback {
    private var exo: ExoPlayer? = null

    /** Adresse en cours de lecture (null = rien). */
    var current by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    /** Fin de la vidéo atteinte (épisode suivant). */
    var ended by mutableStateOf(false)
        private set

    private var triedHls = false
    /** Lecture d'un flux réseau en cours (les téléchargements se mettent en pause pour lui laisser la connexion). */
    val streaming: Boolean get() = current?.startsWith("http") == true
    /** Adresse réellement chargée (HLS de secours, autre serveur…) ; [current] garde l'adresse demandée. */
    private var attempt: String? = null
    private var attemptStart = 0L
    /** Mêmes flux sur les autres serveurs (principal / secours), essayés si celui-ci échoue. */
    private val alternates = ArrayDeque<String>()
    /** Fourni par la session : adresses équivalentes sur les autres serveurs. */
    var alternatesFor: ((String) -> List<String>)? = null

    fun player(ctx: Context): ExoPlayer = exo ?: build(ctx.applicationContext).also { exo = it }

    private fun build(ctx: Context): ExoPlayer {
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_500, 3_000).build()
        // Décodeur audio FFmpeg intégré, seulement en secours : quand l'écran ne sait pas lire le son (MP2, AC3, EAC3, DTS…).
        val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val p = ExoPlayer.Builder(ctx, renderers)
            // Réseau via OkHttp ; fichiers locaux (téléchargements hors ligne) via DefaultDataSource.
            .setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(ctx, OkHttpDataSource.Factory(Http.client))))
            .setLoadControl(load)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
                if (state == Player.STATE_READY) error = false
            }
            override fun onPlayerError(e: PlaybackException) {
                // 1) Direct en .ts refusé : on retente une fois en HLS (.m3u8), que beaucoup de serveurs servent aussi.
                // 2) Puis le même flux sur l'autre serveur (secours / principal), avant d'afficher l'erreur.
                val url = attempt
                val pos = exo?.currentPosition?.takeIf { it > 0 } ?: attemptStart
                when {
                    url != null && url.endsWith(".ts") && !triedHls -> {
                        triedHls = true
                        start(url.removeSuffix(".ts") + ".m3u8", startMs = pos, keepFallbackState = true)
                    }
                    alternates.isNotEmpty() -> {
                        triedHls = false
                        start(alternates.removeFirst(), startMs = pos, keepFallbackState = true)
                    }
                    else -> error = true
                }
            }
        })
        return p
    }

    /** Lance [url] (si ce n'est pas déjà elle). */
    fun play(ctx: Context, url: String, startMs: Long = 0) {
        player(ctx)
        if (url == current && !error) { exo?.playWhenReady = true; return }
        triedHls = false
        alternates.clear()
        alternatesFor?.invoke(url)?.let { alternates.addAll(it) }
        start(url, startMs = startMs)
    }

    private fun start(url: String, startMs: Long = 0, keepFallbackState: Boolean = false) {
        val p = exo ?: return
        if (!keepFallbackState) current = url
        attempt = url
        attemptStart = startMs
        error = false
        ended = false
        val item = MediaItem.Builder().setUri(url).apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        p.setMediaItem(item, startMs)
        p.prepare()
        p.playWhenReady = true
    }

    /** Avance (+) ou recul (−) en millisecondes dans la vidéo en cours. */
    fun seekBy(deltaMs: Long) {
        val p = exo ?: return
        val dur = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, dur))
    }

    fun togglePause() { exo?.let { it.playWhenReady = !it.playWhenReady } }

    /** Position et durée (ms) ; durée ≤ 0 tant qu'elle n'est pas connue. */
    fun position(): Long = exo?.currentPosition ?: 0
    fun duration(): Long = exo?.duration?.takeIf { it > 0 } ?: 0

    fun retry(ctx: Context) { current?.let { u -> current = null; play(ctx, u) } }

    fun stop() {
        exo?.stop()
        exo?.clearMediaItems()
        current = null
        error = false
    }

    fun release() {
        exo?.release()
        exo = null
        current = null
    }
}
