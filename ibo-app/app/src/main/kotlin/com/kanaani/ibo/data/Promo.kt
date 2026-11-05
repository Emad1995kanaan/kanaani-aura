package com.kanaani.ibo.data

import androidx.compose.runtime.getValue
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
import java.net.URLEncoder

/** Une image du panneau publicitaire : publicité ([title] null) ou film (titre + note). */
data class Slide(val image: String, val title: String? = null, val rating: String? = null)

/**
 * Panneau publicitaire : les 5 derniers films ajoutés au serveur, avec leur grande image (backdrop) et leur note
 * TMDB. L'identifiant TMDB vient du serveur (get_vod_info) ; sinon on cherche le film par son titre.
 */
object Promo {
    var movies by mutableStateOf<List<Slide>>(emptyList())
        private set
    private var loadedAt = 0L

    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/w1280"

    private var loadedCount = -1

    suspend fun refresh(x: Xtream, count: Int = 5) {
        val key = BuildConfig.TMDB_KEY
        if (count <= 0) { movies = emptyList(); loadedCount = 0; return }
        if (key.isBlank() || (count == loadedCount && System.currentTimeMillis() - loadedAt < 30 * 60_000L)) return
        loadedCount = count
        val latest = runCatching { x.latestMovies(count) }.getOrDefault(emptyList())
        if (latest.isEmpty()) return
        val slides = latest.mapNotNull { m ->
            runCatching {
                val title = clean(m.name)
                val tmdbId = x.movieInfo(m.id)?.tmdbId?.takeIf { it.isNotBlank() && it != "0" }
                val details = tmdbId?.let { tmdb("$API/movie/$it?api_key=$key&language=ar") }
                    ?: search(key, title)
                val backdrop = details?.str("backdrop_path")
                val image = backdrop?.let { IMG + it } ?: m.poster ?: return@runCatching null
                val vote = (details?.get("vote_average") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { it > 0 }
                    ?: m.rating?.toDoubleOrNull()?.takeIf { it > 0 }
                Slide(image, title, vote?.let { String.format(java.util.Locale.ROOT, "%.1f", it) })
            }.getOrNull()
        }
        if (slides.isNotEmpty()) { movies = slides; loadedAt = System.currentTimeMillis() }
    }

    /** « AR | Titre (2024) » → « Titre ». */
    fun clean(name: String): String =
        name.replace(Regex("""^\s*[A-Za-z0-9]{1,4}\s*[|:\-]\s*"""), "").replace(Regex("""\s*[(\[]\d{4}[)\]]\s*$"""), "").trim().ifEmpty { name }

    private suspend fun search(key: String, title: String): JsonObject? {
        val q = URLEncoder.encode(title, "UTF-8")
        val r = tmdb("$API/search/movie?api_key=$key&language=ar&query=$q") ?: return null
        return (r["results"] as? JsonArray)?.firstOrNull() as? JsonObject
    }

    private suspend fun tmdb(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else Json.parseToJsonElement(r.body.string()) as? JsonObject
            }
        }.getOrNull()
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
}
