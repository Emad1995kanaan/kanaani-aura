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

    fun player(ctx: Context): ExoPlayer = exo ?: build(ctx.applicationContext).also { exo = it; handler.removeCallbacks(watchdog); handler.post(watchdog) }

    // ── Aura : chaîne qui bloque → même chaîne en qualité plus basse, sans que l'utilisateur ait à faire quoi que ce soit. ──
    /** Adresse demandée par l'écran (la chaîne choisie) ; [current] peut être une qualité plus basse de celle-ci. */
    private var requested: String? = null
    /** Fourni par la session : même chaîne en qualité juste en dessous (null s'il n'y en a pas) et son adresse. */
    var lowerQuality: ((String, Set<String>) -> Pair<com.kanaani.ibo.data.Channel, String>?)? = null
    /** Dernière bascule automatique (l'écran Direct met la chaîne affichée à jour). */
    var switched by mutableStateOf<com.kanaani.ibo.data.Channel?>(null)
        private set
    private val tried = mutableSetOf<String>()
    private var retriedSame = false
    private var readyOnce = false
    private var bufferSince = 0L
    private var startedAt = 0L
    private val stalls = ArrayDeque<Long>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val watchdog = object : Runnable {
        override fun run() { runCatching { check() }; handler.postDelayed(this, 400) }
    }
    private fun isLive(u: String?) = u != null && "/live/" in u

    private fun check() {
        val p = exo ?: return
        val url = attempt ?: return
        if (!isLive(url) || !p.playWhenReady || !com.kanaani.ibo.data.Store.autoQuality) return
        val now = android.os.SystemClock.elapsedRealtime()
        while (stalls.isNotEmpty() && now - stalls.first() > 60_000L) stalls.removeFirst()
        val stuck = p.playbackState == Player.STATE_BUFFERING && when {
            readyOnce -> bufferSince > 0 && now - bufferSince >= 2_000L
            else -> now - startedAt >= 5_000L
        }
        if (stuck || stalls.size >= 3) degrade(fromError = false)
    }

    /** Passe à la qualité juste en dessous ; sinon relance une fois la même ; sinon erreur (si c'était une erreur). */
    private fun degrade(fromError: Boolean): Boolean {
        val url = current ?: return false
        stalls.clear()
        val next = runCatching { lowerQuality?.invoke(url, tried) }.getOrNull()
        if (next != null) {
            tried += next.first.id
            alternates.clear()
            alternatesFor?.invoke(next.second)?.let { alternates.addAll(it) }
            triedHls = false
            switched = next.first
            start(next.second)
            return true
        }
        if (!retriedSame) {
            retriedSame = true
            triedHls = false
            start(attempt ?: url, keepFallbackState = true)
            return true
        }
        if (fromError) error = true
        // Rien de mieux : on laisse le flux continuer d'essayer (pas de nouvelle bascule avant un vrai blocage).
        readyOnce = false; startedAt = android.os.SystemClock.elapsedRealtime() + 25_000L
        return false
    }

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
                val now = android.os.SystemClock.elapsedRealtime()
                if (state == Player.STATE_BUFFERING) {
                    if (bufferSince == 0L) bufferSince = now
                    if (readyOnce) stalls.addLast(now)
                } else bufferSince = 0L
                if (state == Player.STATE_READY) readyOnce = true
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
                    isLive(url) && com.kanaani.ibo.data.Store.autoQuality -> degrade(fromError = true)
                    else -> error = true
                }
            }
        })
        return p
    }

    /** Lance [url] (si ce n'est pas déjà elle). */
    fun play(ctx: Context, url: String, startMs: Long = 0, manual: Boolean = false) {
        player(ctx)
        if ((url == current || (!manual && url == requested)) && !error) { exo?.playWhenReady = true; return }
        requested = url
        switched = null
        tried.clear(); tried += com.kanaani.ibo.data.QualityLadder.idOf(url)
        retriedSame = false
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
        readyOnce = false; bufferSince = 0L; stalls.clear(); startedAt = android.os.SystemClock.elapsedRealtime()
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

    fun retry(ctx: Context) { current?.let { u -> current = null; requested = null; play(ctx, u) } }

    fun stop() {
        exo?.stop()
        exo?.clearMediaItems()
        current = null
        requested = null
        attempt = null
        switched = null
        error = false
    }

    fun release() {
        exo?.release()
        exo = null
        current = null
    }
}
