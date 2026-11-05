package com.kanaani.ibo.data

import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Client HTTP partagé (API, lecteur, images). */
object Http {
    const val USER_AGENT = "KanaaniIBO/1.0 (Android)"
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
        .build()
    /** API : délai de connexion court pour basculer vite sur le serveur de secours. */
    val api: OkHttpClient = client.newBuilder().connectTimeout(7, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
}

data class Account(
    val status: String?,
    val expiresAt: Long?,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val createdAt: Long?,
    val trial: Boolean,
)

data class Category(val id: String, val name: String)

data class Channel(val id: String, val name: String, val logo: String?, val number: Int, val categoryId: String?)

/** [added] : date d'ajout sur le serveur (s), pour « Derniers ajouts ». */
data class Movie(val id: String, val name: String, val poster: String?, val rating: String?, val ext: String, val categoryId: String?, val added: Long = 0)

/** [updated] : dernière modification (nouvel épisode) sur le serveur (s), pour « Dernières mises à jour ». */
data class Show(val id: String, val name: String, val poster: String?, val rating: String?, val categoryId: String?, val updated: Long = 0)

data class MovieInfo(
    val plot: String?, val genre: String?, val year: String?, val duration: String?,
    val backdrop: String?, val director: String?, val cast: String?, val rating: String?,
    val tmdbId: String? = null,
)

data class Episode(val id: String, val title: String, val number: Int, val season: Int, val ext: String, val plot: String?, val image: String?, val duration: String?)

data class ShowInfo(
    val plot: String?, val genre: String?, val year: String?, val backdrop: String?, val cast: String?, val rating: String?,
    val seasons: Map<Int, List<Episode>>,
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonObject.str(k: String): String? =
    (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

private fun JsonObject.int(k: String): Int? = str(k)?.toDoubleOrNull()?.toInt()

private fun JsonElement?.objects(): List<JsonObject> = when (this) {
    is JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> values.mapNotNull { it as? JsonObject } // certains serveurs renvoient un objet indexé
    else -> emptyList()
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

private val LIVE_FIELDS = setOf("stream_id", "name", "stream_icon", "num", "category_id")
private val VOD_FIELDS = setOf("stream_id", "name", "stream_icon", "cover", "rating", "container_extension", "category_id", "added")
private val SERIES_FIELDS = setOf("series_id", "name", "cover", "rating", "category_id", "last_modified")

/** Champs utiles d'un élément, lus au fil de l'eau (le reste est sauté sans être construit). */
private fun JsonReader.fields(keep: Set<String>): Map<String, String> {
    val m = HashMap<String, String>(keep.size * 2)
    beginObject()
    while (hasNext()) {
        val n = nextName()
        if (n !in keep) { skipValue(); continue }
        when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> nextString().trim().takeIf { it.isNotEmpty() && it != "null" }?.let { m[n] = it }
            JsonToken.BOOLEAN -> m[n] = if (nextBoolean()) "1" else "0"
            else -> skipValue()
        }
    }
    endObject()
    return m
}

/**
 * Liste du serveur lue en flux (tableau, ou objet indexé chez certains serveurs). null = réponse de refus
 * (« user_info » à la place de la liste) ou illisible.
 */
private fun <T> JsonReader.items(keep: Set<String>, map: (Map<String, String>) -> T?): List<T>? {
    isLenient = true
    val out = ArrayList<T>()
    when (peek()) {
        JsonToken.BEGIN_ARRAY -> {
            beginArray()
            while (hasNext()) if (peek() == JsonToken.BEGIN_OBJECT) map(fields(keep))?.let(out::add) else skipValue()
            endArray()
        }
        JsonToken.BEGIN_OBJECT -> {
            beginObject()
            while (hasNext()) {
                if (nextName() == "user_info") return null
                if (peek() == JsonToken.BEGIN_OBJECT) map(fields(keep))?.let(out::add) else skipValue()
            }
            endObject()
        }
        else -> return null
    }
    return out
}

/** « AM | beIN Sports 1 | HD » → « beIN Sports 1 | HD » : préfixe du fournisseur retiré des noms de chaînes. */
private val channelPrefix = Regex("""^\s*AM\s*\|\s*""")
fun cleanChannel(name: String): String = name.replace(channelPrefix, "").ifBlank { name }

/**
 * API Xtream Codes, chargée À LA DEMANDE (comme IBO Player) : rien n'est téléchargé d'avance ; chaque catégorie est
 * lue au moment où on l'ouvre puis gardée en mémoire pour la session.
 */
class Xtream(hosts: List<String>, val username: String, val password: String) {
    /** Principal puis secours ; mis à jour par le panneau de contrôle. */
    @Volatile var hosts: List<String> = hosts
        private set

    fun updateHosts(list: List<String>) {
        if (list.isEmpty() || list == hosts) return
        hosts = list
        if (host !in list) host = list.first()
        primaryTried = false
    }

    private val cache = ConcurrentHashMap<String, Any>()

    /**
     * Serveur utilisé en ce moment : [hosts] = principal puis secours. On repart du dernier serveur qui a répondu ;
     * au premier appel de la session on réessaie d'abord le principal (retour automatique quand il revient).
     */
    @Volatile var host: String = Store.lastHost()?.takeIf { it in hosts } ?: hosts.first()
        private set
    @Volatile private var primaryTried = false

    private fun url(h: String, action: String?, extra: String = ""): String {
        val base = "$h/player_api.php?username=${enc(username)}&password=${enc(password)}"
        return if (action == null) base else "$base&action=$action$extra"
    }

    /**
     * Appel API avec bascule : on passe au serveur suivant si celui-ci ne répond pas (réseau, erreur HTTP, réponse
     * illisible) OU refuse le compte (identifiants inconnus, compte expiré / désactivé : le compte peut n'exister que
     * sur l'autre serveur). Si tous refusent, on rend le premier refus (message « identifiants » / « expiré »).
     */
    private suspend fun get(action: String?, extra: String = ""): JsonElement? = withContext(Dispatchers.IO) {
        val first = if (!primaryTried) hosts.first() else host
        primaryTried = true
        var refused: JsonElement? = null
        for (h in listOf(first) + hosts.filter { it != first }) {
            val res = runCatching {
                Http.api.newCall(Request.Builder().url(url(h, action, extra)).build()).execute().use { r ->
                    if (!r.isSuccessful) null else json.parseToJsonElement(r.body.string())
                }
            }.getOrNull() ?: continue
            if (refuses(res, account = action == null)) { if (refused == null) refused = res; continue }
            if (h != host) { host = h; Store.rememberHost(h) }
            return@withContext res
        }
        refused
    }

    /**
     * Comme [get] pour les grandes listes : la réponse est lue en flux, élément par élément, sans construire tout
     * l'arbre JSON en mémoire (beaucoup plus rapide sur les boîtiers modestes). Même bascule de serveur.
     */
    private suspend fun <T> list(action: String, extra: String, keep: Set<String>, map: (Map<String, String>) -> T?): List<T>? =
        withContext(Dispatchers.IO) {
            val first = if (!primaryTried) hosts.first() else host
            primaryTried = true
            for (h in listOf(first) + hosts.filter { it != first }) {
                val res = runCatching {
                    Http.api.newCall(Request.Builder().url(url(h, action, extra)).build()).execute().use { r ->
                        if (!r.isSuccessful) null else JsonReader(r.body.charStream().buffered(64 * 1024)).use { it.items(keep, map) }
                    }
                }.getOrNull() ?: continue
                if (h != host) { host = h; Store.rememberHost(h) }
                return@withContext res
            }
            null
        }

    /** Réponse de refus : « auth » à 0, ou (infos du compte) statut autre qu'actif. */
    private fun refuses(res: JsonElement, account: Boolean): Boolean {
        val ui = (res as? JsonObject)?.get("user_info") as? JsonObject ?: return false
        if (ui.str("auth") == "0") return true
        return account && ui.str("status")?.equals("Active", ignoreCase = true) == false
    }

    /** Même adresse de flux sur les autres serveurs (pour la bascule du lecteur). */
    fun alternates(streamUrl: String): List<String> =
        if (!streamUrl.startsWith(host)) emptyList() else hosts.filter { it != host }.map { it + streamUrl.removePrefix(host) }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T?): T? {
        (cache[key] as? T)?.let { return it }
        // Réponse inattendue d'un serveur : pas de plantage, la liste reste simplement vide.
        val v = try { load() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { null } ?: return null
        cache[key] = v
        return v
    }

    fun clearCache() = cache.clear()

    /** Augmente quand une liste « Tous » rafraîchie en arrière-plan arrive : l'écran ouvert se met à jour. */
    var catalogVersion by mutableIntStateOf(0)
        private set
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Liste complète « Tous » : mémoire, sinon copie sur l'appareil (affichée tout de suite, puis rafraîchie en
     * arrière-plan), sinon serveur. Un seul chargement à la fois par liste : le préchargement et l'écran se le partagent.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> full(
        key: String, disk: () -> List<T>?, save: (List<T>) -> Unit, fetch: suspend () -> List<T>?,
    ): List<T> {
        (cache[key] as? List<T>)?.let { return it }
        return locks.getOrPut(key) { Mutex() }.withLock {
            (cache[key] as? List<T>)?.let { return@withLock it }
            val saved = withContext(Dispatchers.IO) { runCatching { disk() }.getOrNull() }
            if (saved != null) {
                cache[key] = saved
                bg.launch {
                    val fresh = runCatching { fetch() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@launch
                    if (fresh != saved) { cache[key] = fresh; catalogVersion++ }
                    runCatching { save(fresh) }
                }
                return@withLock saved
            }
            val fresh = try { fetch() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { null }
                ?: return@withLock emptyList()
            cache[key] = fresh
            if (fresh.isNotEmpty()) bg.launch { runCatching { save(fresh) } }
            fresh
        }
    }

    /** Préchargement à l'ouverture de l'accueil : « Tous » films et séries sont prêts avant qu'on les ouvre. */
    fun prefetch() {
        bg.launch {
            delay(1500)
            runCatching { loadMovies(null) }
            runCatching { loadShows(null) }
        }
    }

    /** null = serveur injoignable ou réponse illisible ; status null = identifiants refusés. */
    suspend fun account(): Account? {
        val root = get(null) as? JsonObject ?: return null
        val ui = root["user_info"] as? JsonObject ?: return Account(null, null, null, null, null, false)
        if (ui.str("auth") == "0") return Account(null, null, null, null, null, false)
        // Compte actif vu en ligne : la lecture hors ligne des téléchargements reste permise 7 jours.
        if (ui.str("status").equals("Active", ignoreCase = true)) runCatching {
            Downloads.markOnline(ui.str("exp_date")?.toLongOrNull()?.takeIf { it > 0 }?.times(1000))
        }
        return Account(
            status = ui.str("status"),
            expiresAt = ui.str("exp_date")?.toLongOrNull()?.takeIf { it > 0 }?.times(1000),
            activeConnections = ui.int("active_cons"),
            maxConnections = ui.int("max_connections"),
            createdAt = ui.str("created_at")?.toLongOrNull()?.takeIf { it > 0 }?.times(1000),
            trial = ui.str("is_trial") == "1",
        )
    }

    private suspend fun categories(action: String): List<Category> = cached(action) {
        get(action)?.objects()?.mapNotNull { o ->
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name") ?: id)
        }?.distinctBy { it.id } // doublons (même identifiant) : feraient planter les listes
    } ?: emptyList()

    suspend fun liveCategories() = categories("get_live_categories")
    suspend fun movieCategories() = categories("get_vod_categories")
    suspend fun showCategories() = categories("get_series_categories")

    /** categoryId null = toutes les chaînes. */
    /** categoryId null = toutes les chaînes, sauf celles des catégories masquées. */
    suspend fun channels(categoryId: String?): List<Channel> =
        loadChannels(categoryId).let { l -> if (categoryId == null) l.filterNot { Store.isHidden("live", it.categoryId) } else l }

    private suspend fun loadChannels(categoryId: String?): List<Channel> = cached("live:${categoryId ?: "*"}") {
        val extra = categoryId?.let { "&category_id=${enc(it)}" } ?: ""
        list("get_live_streams", extra, LIVE_FIELDS) { o ->
            val id = o["stream_id"] ?: return@list null
            Channel(id, cleanChannel(o["name"] ?: id), o["stream_icon"], o["num"]?.toDoubleOrNull()?.toInt() ?: 0, o["category_id"])
        }?.distinctBy { it.id }
    } ?: emptyList()

    /** categoryId null = tous les films. */
    suspend fun movies(categoryId: String?): List<Movie> =
        loadMovies(categoryId).let { l -> if (categoryId == null) l.filterNot { Store.isHidden("movie", it.categoryId) } else l }

    private suspend fun fetchMovies(categoryId: String?): List<Movie>? =
        list("get_vod_streams", categoryId?.let { "&category_id=${enc(it)}" } ?: "", VOD_FIELDS) { o ->
            val id = o["stream_id"] ?: return@list null
            Movie(id, o["name"] ?: id, o["stream_icon"] ?: o["cover"], o["rating"], o["container_extension"] ?: "mp4", o["category_id"],
                added = o["added"]?.toLongOrNull() ?: 0)
        }?.distinctBy { it.id }

    private suspend fun loadMovies(categoryId: String?): List<Movie> =
        if (categoryId == null) full("vod:*", { ListCache.loadMovies(username, password) }, { ListCache.saveMovies(username, password, it) }) { fetchMovies(null) }
        else cached("vod:$categoryId") { fetchMovies(categoryId) } ?: emptyList()

    /** categoryId null = toutes les séries. */
    suspend fun shows(categoryId: String?): List<Show> =
        loadShows(categoryId).let { l -> if (categoryId == null) l.filterNot { Store.isHidden("series", it.categoryId) } else l }

    private suspend fun fetchShows(categoryId: String?): List<Show>? =
        list("get_series", categoryId?.let { "&category_id=${enc(it)}" } ?: "", SERIES_FIELDS) { o ->
            val id = o["series_id"] ?: return@list null
            Show(id, o["name"] ?: id, o["cover"], o["rating"], o["category_id"], updated = o["last_modified"]?.toLongOrNull() ?: 0)
        }?.distinctBy { it.id }

    private suspend fun loadShows(categoryId: String?): List<Show> =
        if (categoryId == null) full("series:*", { ListCache.loadShows(username, password) }, { ListCache.saveShows(username, password, it) }) { fetchShows(null) }
        else cached("series:$categoryId") { fetchShows(categoryId) } ?: emptyList()

    /** Films les plus récemment ajoutés au serveur (toutes catégories). */
    suspend fun latestMovies(limit: Int = 30): List<Movie> = movies(null).filter { it.added > 0 }.sortedByDescending { it.added }.take(limit)

    /** Séries les plus récemment mises à jour (nouveaux épisodes), toutes catégories. */
    suspend fun latestShows(limit: Int = 30): List<Show> = shows(null).filter { it.updated > 0 }.sortedByDescending { it.updated }.take(limit)

    suspend fun movieInfo(id: String): MovieInfo? = cached("vodinfo:$id") {
        val root = get("get_vod_info", "&vod_id=${enc(id)}") as? JsonObject ?: return@cached null
        val i = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        val backdrop = (i["backdrop_path"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: i.str("backdrop_path") ?: i.str("cover_big") ?: i.str("movie_image")
        MovieInfo(
            plot = i.str("plot") ?: i.str("description"),
            genre = i.str("genre"),
            year = (i.str("releasedate") ?: i.str("release_date"))?.take(4),
            duration = i.str("duration"),
            backdrop = backdrop,
            director = i.str("director"),
            cast = i.str("cast") ?: i.str("actors"),
            rating = i.str("rating"),
            tmdbId = i.str("tmdb_id") ?: i.str("tmdb") ?: (root["movie_data"] as? JsonObject)?.str("tmdb_id"),
        )
    }

    suspend fun showInfo(id: String): ShowInfo? = cached("seriesinfo:$id") {
        val root = get("get_series_info", "&series_id=${enc(id)}") as? JsonObject ?: return@cached null
        val i = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        val episodes = root["episodes"]
        val seasons = sortedMapOf<Int, List<Episode>>()
        val groups: List<Pair<Int, List<JsonObject>>> = when (episodes) {
            is JsonObject -> episodes.entries.map { (k, v) -> (k.toIntOrNull() ?: 0) to v.objects() }
            is JsonArray -> episodes.mapIndexed { idx, v -> (idx + 1) to v.objects() }
            else -> emptyList()
        }
        for ((season, list) in groups) {
            val eps = list.mapNotNull { o ->
                val eid = o.str("id") ?: return@mapNotNull null
                val info = o["info"] as? JsonObject
                Episode(
                    id = eid,
                    title = o.str("title") ?: eid,
                    number = o.int("episode_num") ?: 0,
                    season = o.int("season") ?: season,
                    ext = o.str("container_extension") ?: "mp4",
                    plot = info?.str("plot"),
                    image = info?.str("movie_image"),
                    duration = info?.str("duration"),
                )
            }.distinctBy { it.id }.sortedBy { it.number }
            if (eps.isNotEmpty()) seasons[season] = (seasons[season].orEmpty() + eps).distinctBy { it.id }.sortedBy { it.number }
        }
        val backdrop = (i["backdrop_path"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } ?: i.str("cover")
        ShowInfo(
            plot = i.str("plot"), genre = i.str("genre"), year = (i.str("releaseDate") ?: i.str("release_date"))?.take(4),
            backdrop = backdrop, cast = i.str("cast"), rating = i.str("rating"), seasons = seasons,
        )
    }

    fun liveUrl(id: String, hls: Boolean = false) = "$host/live/${enc(username)}/${enc(password)}/$id.${if (hls) "m3u8" else "ts"}"
    fun movieUrl(m: Movie) = "$host/movie/${enc(username)}/${enc(password)}/${m.id}.${m.ext}"
    fun episodeUrl(e: Episode) = "$host/series/${enc(username)}/${enc(password)}/${e.id}.${e.ext}"
}
