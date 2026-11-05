package com.kanaani.ibo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request

/**
 * Publicités des thèmes d'accueil, lues depuis un fichier ads.json publié sur GitHub (dossier ads/ du dépôt) :
 * on change les images sans mettre à jour l'application.
 *
 * {"interval": 10, "billboard": ["promo1.jpg", ...]}
 * Les noms relatifs sont résolus par rapport au dossier du fichier ads.json.
 */
object Ads {
    var billboard by mutableStateOf<List<String>>(emptyList())
        private set
    /** Durée d'affichage de chaque image (s). */
    var interval by mutableIntStateOf(10)
        private set

    private var loadedAt = 0L
    /** Publicités reçues du panneau de contrôle : ads.json (GitHub) n'est plus lu. */
    private var fromPanel = false

    /** Panneau : "mix" (films + pubs), "movies", "ads", "none". */
    var mode by mutableStateOf("mix")
        private set
    /** Nombre de derniers films ; films entre deux pubs. */
    var movieCount by mutableIntStateOf(5)
        private set
    var every by mutableIntStateOf(3)
        private set
    /** Films choisis dans le panneau (image, titre, note). */
    var pinned by mutableStateOf<List<Slide>>(emptyList())
        private set

    fun fromPanel(interval: Int, list: List<String>, mode: String = "mix", movies: Int = 5, every: Int = 3, pinned: List<Slide> = emptyList()) {
        fromPanel = true
        this.interval = interval.coerceIn(3, 120)
        if (list != billboard) billboard = list
        this.mode = mode
        movieCount = movies.coerceIn(0, 30)
        this.every = every.coerceIn(1, 10)
        if (pinned != this.pinned) this.pinned = pinned
    }

    suspend fun refresh() {
        val url = BuildConfig.ADS_URL
        if (fromPanel || url.isBlank() || System.currentTimeMillis() - loadedAt < 10 * 60_000L) return
        val root = withContext(Dispatchers.IO) {
            runCatching {
                // Paramètre horaire : contourne le cache du CDN (quelques minutes au plus).
                val fresh = url + (if ('?' in url) "&" else "?") + "t=" + System.currentTimeMillis() / 300_000L
                Http.client.newCall(Request.Builder().url(fresh).build()).execute().use { r ->
                    if (!r.isSuccessful) null else Json.parseToJsonElement(r.body.string()) as? JsonObject
                }
            }.getOrNull()
        } ?: return
        val base = url.substringBefore('?').substringBeforeLast('/') + "/"
        fun list(k: String) = (root[k] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }.map { if (it.startsWith("http")) it else base + it }
        billboard = list("billboard")
        interval = ((root["interval"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 10).coerceIn(3, 120)
        loadedAt = System.currentTimeMillis()
    }
}
