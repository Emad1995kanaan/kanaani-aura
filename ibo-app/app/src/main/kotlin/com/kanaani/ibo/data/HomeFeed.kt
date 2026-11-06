package com.kanaani.ibo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/**
 * Aura : rangées de l'accueil (derniers films / séries) affichées TOUT DE SUITE, puis les grosses listes en arrière-plan.
 *  1) copie gardée sur l'appareil depuis la dernière fois ;
 *  2) sinon une petite catégorie « nouveautés » (quelques secondes) ;
 *  3) puis, l'une après l'autre : tous les films, toutes les séries, toutes les chaînes (qualités du direct).
 * Lancé au niveau de l'application : continue même si on quitte l'accueil.
 */
object HomeFeed {
    var movies by mutableStateOf<List<Movie>?>(null)
        private set
    var shows by mutableStateOf<List<Show>?>(null)
        private set
    /** +1 = tout recharger (bouton « Actualiser »). */
    var gen by mutableIntStateOf(0)

    private val fresh = Regex("(?i)(2026|new|latest|recent|جديد|احدث|أحدث|حديث|חדש)")
    private val year = Regex("(?i)(2025)")

    private fun pick(cats: List<Category>): Category? =
        cats.firstOrNull { fresh.containsMatchIn(it.name) } ?: cats.firstOrNull { year.containsMatchIn(it.name) } ?: cats.firstOrNull()

    /** Jamais d'arrêt de l'application à cause de l'accueil : toute erreur est ignorée. */
    suspend fun run(x: Xtream) {
        try { load(x) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) {
            if (movies == null) movies = emptyList()
            if (shows == null) shows = emptyList()
        }
    }

    private suspend fun load(x: Xtream) {
        if (movies.isNullOrEmpty()) decodeMovies(Store.raw("home_movies")).takeIf { it.isNotEmpty() }?.let { movies = it }
        if (shows.isNullOrEmpty()) decodeShows(Store.raw("home_shows")).takeIf { it.isNotEmpty() }?.let { shows = it }
        // Première fois : petite catégorie de nouveautés, en attendant les listes complètes.
        if (movies.isNullOrEmpty()) runCatching {
            pick(x.movieCategories())?.let { c -> x.movies(c.id).sortedByDescending { it.added }.take(24).takeIf { it.isNotEmpty() }?.let { movies = it } }
        }
        if (shows.isNullOrEmpty()) runCatching {
            pick(x.showCategories())?.let { c -> x.shows(c.id).sortedByDescending { it.updated }.take(24).takeIf { it.isNotEmpty() }?.let { shows = it } }
        }
        // Listes complètes, une à la fois.
        val m = runCatching { x.latestMovies(24) }.getOrDefault(emptyList())
        if (m.isNotEmpty()) { movies = m; Store.putRaw("home_movies", encodeMovies(m)) } else if (movies == null) movies = emptyList()
        val s = runCatching { x.latestShows(24) }.getOrDefault(emptyList())
        if (s.isNotEmpty()) { shows = s; Store.putRaw("home_shows", encodeShows(s)) } else if (shows == null) shows = emptyList()
    }

    fun clear() { movies = null; shows = null }

    private fun p(v: String?) = JsonPrimitive(v ?: "")
    private fun encodeMovies(l: List<Movie>) = JsonArray(l.map { m ->
        JsonObject(mapOf("i" to p(m.id), "n" to p(m.name), "p" to p(m.poster), "r" to p(m.rating), "e" to p(m.ext), "c" to p(m.categoryId), "a" to JsonPrimitive(m.added)))
    }).toString()
    private fun encodeShows(l: List<Show>) = JsonArray(l.map { m ->
        JsonObject(mapOf("i" to p(m.id), "n" to p(m.name), "p" to p(m.poster), "r" to p(m.rating), "c" to p(m.categoryId), "u" to JsonPrimitive(m.updated)))
    }).toString()

    private fun <T> decode(raw: String?, f: ((String) -> String) -> T?): List<T> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            f { k -> (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty() }
        }
    }.getOrDefault(emptyList())
    private fun decodeMovies(raw: String?) = decode(raw) { g ->
        g("i").takeIf { it.isNotEmpty() }?.let { Movie(it, g("n"), g("p").ifEmpty { null }, g("r").ifEmpty { null }, g("e").ifEmpty { "mp4" }, g("c").ifEmpty { null }, g("a").toLongOrNull() ?: 0) }
    }
    private fun decodeShows(raw: String?) = decode(raw) { g ->
        g("i").takeIf { it.isNotEmpty() }?.let { Show(it, g("n"), g("p").ifEmpty { null }, g("r").ifEmpty { null }, g("c").ifEmpty { null }, g("u").toLongOrNull() ?: 0) }
    }
}
