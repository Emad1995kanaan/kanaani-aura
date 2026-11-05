package com.kanaani.ibo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.kanaani.ibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import java.net.URLEncoder

/**
 * Affiches de secours depuis TMDB : pour les films / séries sans affiche (ou dont l'image ne s'ouvre pas), on cherche
 * le titre (nettoyé, + année) sur TMDB. Résultat gardé sur l'appareil : chaque titre n'est cherché qu'une fois.
 */
object TmdbArt {
    data class Art(val poster: String?, val backdrop: String?, val overview: String?)

    private const val API = "https://api.themoviedb.org/3"
    private const val POSTER = "https://image.tmdb.org/t/p/w342"
    private const val BACKDROP = "https://image.tmdb.org/t/p/w1280"
    private var sp: SharedPreferences? = null
    private val mem = HashMap<String, Art?>()
    private val gate = Semaphore(4)

    fun init(ctx: Context) { sp = ctx.getSharedPreferences("tmdb_art", Context.MODE_PRIVATE) }

    /** [kind] : "movie" ou "tv". */
    suspend fun find(kind: String, name: String): Art? {
        val key = BuildConfig.TMDB_KEY
        if (key.isBlank() || name.isBlank()) return null
        val year = Regex("""(19|20)\d{2}""").findAll(name).lastOrNull()?.value
        val title = Promo.clean(name).replace(Regex("""\s*[-–]?\s*(19|20)\d{2}\s*$"""), "").trim()
        val ck = "$kind|${title.lowercase()}|${year.orEmpty()}"
        synchronized(mem) { if (mem.containsKey(ck)) return mem[ck] }
        sp?.getString(ck, null)?.let { raw ->
            val a = if (raw == "-") null else parse(raw)
            synchronized(mem) { mem[ck] = a }
            return a
        }
        val art = gate.withPermit { search(key, kind, title, year) ?: if (year != null) search(key, kind, title, null) else null }
        synchronized(mem) { mem[ck] = art }
        sp?.edit()?.putString(ck, art?.let { buildJsonObject { put("p", it.poster); put("b", it.backdrop); put("o", it.overview) }.toString() } ?: "-")?.apply()
        return art
    }

    private fun parse(raw: String): Art? = runCatching {
        val o = Json.parseToJsonElement(raw) as JsonObject
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        Art(s("p"), s("b"), s("o"))
    }.getOrNull()

    private suspend fun search(key: String, kind: String, title: String, year: String?): Art? = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(title, "UTF-8")
            val y = year?.let { if (kind == "tv") "&first_air_date_year=$it" else "&year=$it" }.orEmpty()
            Http.client.newCall(Request.Builder().url("$API/search/$kind?api_key=$key&language=ar&query=$q$y").build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val res = ((Json.parseToJsonElement(r.body.string()) as? JsonObject)?.get("results") as? JsonArray) ?: return@use null
                val o = res.firstOrNull { ((it as? JsonObject)?.get("poster_path") as? JsonPrimitive)?.contentOrNull != null } as? JsonObject ?: return@use null
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                Art(s("poster_path")?.let { POSTER + it }, s("backdrop_path")?.let { BACKDROP + it }, s("overview"))
            }
        }.getOrNull()
    }
}

/**
 * Image d'affiche : celle du serveur, sinon (absente ou qui ne s'ouvre pas) celle de TMDB.
 * [kind] : "movie" ou "tv".
 */
@Composable
fun PosterImage(url: String?, kind: String, name: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val own = url?.takeIf { it.isNotBlank() }
    var src by remember(url, name) { mutableStateOf(own) }
    // L'image du serveur n'a pas pu s'ouvrir.
    var failed by remember(url, name) { mutableStateOf(false) }
    // Clés stables (pas d'état modifié dedans qui relancerait l'effet en annulant la recherche en cours).
    LaunchedEffect(url, name, failed) {
        if (own != null && !failed) return@LaunchedEffect
        val p = TmdbArt.find(kind, name)?.poster
        src = p ?: if (failed) null else src
    }
    val s = src ?: return
    coil3.compose.AsyncImage(s, name, modifier, contentScale = contentScale, onError = { if (s == own) failed = true })
}
