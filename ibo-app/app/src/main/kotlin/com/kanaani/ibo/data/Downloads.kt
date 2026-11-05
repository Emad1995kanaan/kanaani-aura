package com.kanaani.ibo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.player.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** État d'un téléchargement. */
enum class DlState { Queued, Running, Paused, Done, Failed }

/**
 * Film ou épisode téléchargé pour être regardé hors ligne. [key] = "movie:id" ou "episode:id".
 * [showId]/[season]/[episode] : épisodes (titre, reprise). [url] : adresse sur le serveur.
 */
data class Dl(
    val key: String, val kind: String, val id: String, val title: String, val image: String?, val url: String, val ext: String,
    val showId: String = "", val showName: String = "", val season: Int = 0, val episode: Int = 0,
    val total: Long = 0, val done: Long = 0, val state: DlState = DlState.Queued, val added: Long = 0,
) {
    val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Téléchargements hors ligne : au plus 2 à la fois, reprise après coupure (en-tête Range), pause automatique pendant
 * la lecture en ligne (un abonnement n'autorise souvent qu'une connexion). Les fichiers sont dans le stockage de
 * l'application (supprimés avec elle). Lecture hors ligne autorisée si le compte a été vu actif il y a moins de 7 jours
 * et que l'abonnement n'est pas expiré.
 */
object Downloads {
    var list by mutableStateOf<List<Dl>>(emptyList())
        private set

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()
    private var engine: Job? = null
    private val client = Http.client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private const val MAX_PARALLEL = 2
    private const val OFFLINE_DAYS = 7

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        // Un téléchargement interrompu (appli fermée) reprend automatiquement.
        list = decode(sp.getString("list", null)).map { if (it.state == DlState.Running) it.copy(state = DlState.Queued) else it }
        if (list.any { it.state == DlState.Queued }) start()
    }

    private fun dir(): File = (app.getExternalFilesDir("downloads") ?: File(app.filesDir, "downloads")).apply { mkdirs() }
    fun file(d: Dl): File = File(dir(), d.key.replace(':', '_') + "." + d.ext.ifBlank { "mp4" })
    fun get(key: String): Dl? = list.firstOrNull { it.key == key }
    fun freeBytes(): Long = runCatching { dir().usableSpace }.getOrDefault(0L)

    // ── Compte : lecture hors ligne autorisée ? ──
    fun markOnline(expiresAt: Long?) {
        sp.edit().putLong("ok_at", System.currentTimeMillis()).putLong("exp", expiresAt ?: 0L).apply()
    }
    fun canPlayOffline(): Boolean {
        val ok = sp.getLong("ok_at", 0L)
        val exp = sp.getLong("exp", 0L)
        val now = System.currentTimeMillis()
        return ok > 0 && now - ok < OFFLINE_DAYS * 86_400_000L && (exp == 0L || now < exp)
    }

    fun add(d: Dl) {
        if (get(d.key) != null) { resume(d.key); return }
        update(d.copy(state = DlState.Queued, added = System.currentTimeMillis()), insert = true)
        start()
    }

    fun pause(key: String) {
        jobs.remove(key)?.cancel()
        get(key)?.takeIf { it.state != DlState.Done }?.let { update(it.copy(state = DlState.Paused)) }
    }

    fun resume(key: String) {
        get(key)?.takeIf { it.state == DlState.Paused || it.state == DlState.Failed }?.let { update(it.copy(state = DlState.Queued)); start() }
    }

    fun delete(key: String) {
        jobs.remove(key)?.cancel()
        val d = get(key) ?: return
        runCatching { file(d).delete() }
        list = list.filterNot { it.key == key }
        save()
    }

    /** Démarre le moteur (et le service de premier plan qui le garde en vie). */
    fun start() {
        DownloadService.start(app)
        if (engine?.isActive == true) return
        engine = scope.launch {
            while (isActive) {
                // Lecture en ligne en cours : on laisse la connexion au lecteur.
                if (Playback.streaming) {
                    jobs.keys.toList().forEach { k -> jobs.remove(k)?.cancel(); get(k)?.let { if (it.state == DlState.Running) update(it.copy(state = DlState.Queued)) } }
                } else {
                    jobs.entries.removeAll { !it.value.isActive }
                    val waiting = list.filter { it.state == DlState.Queued && it.key !in jobs }
                    for (d in waiting.take((MAX_PARALLEL - jobs.size).coerceAtLeast(0))) jobs[d.key] = scope.launch { run(d.key) }
                }
                if (jobs.isEmpty() && list.none { it.state == DlState.Queued }) break
                delay(1_000)
            }
            DownloadService.stop(app)
        }
    }

    private suspend fun run(key: String) {
        var d = get(key) ?: return
        val f = file(d)
        val have = if (f.exists()) f.length() else 0L
        d = d.copy(state = DlState.Running, done = have); update(d)
        try {
            val req = Request.Builder().url(d.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) { update(d.copy(state = DlState.Failed)); return }
                val partial = r.code == 206
                if (!partial && have > 0) f.delete() // le serveur ne reprend pas : on recommence
                val start = if (partial) have else 0L
                val len = r.body.contentLength()
                val total = if (len > 0) start + len else d.total
                if (total > 0 && total - start > freeBytes()) { update(d.copy(state = DlState.Failed, total = total)); return }
                var done = start
                var last = 0L
                FileOutputStream(f, partial).use { out ->
                    r.body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - last > 700) { last = now; update(d.copy(done = done, total = total), persist = false) }
                        }
                    }
                }
                update(d.copy(state = DlState.Done, done = done, total = if (total > 0) total else done))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            val cur = get(key) ?: return
            // Coupure réseau : on réessaie plus tard (reprise là où on s'est arrêté).
            update(cur.copy(state = DlState.Queued))
            delay(15_000)
        }
    }

    @Synchronized
    private fun update(d: Dl, insert: Boolean = false, persist: Boolean = true) {
        val cur = list
        if (cur.none { it.key == d.key }) { if (!insert) return; list = listOf(d) + cur } else list = cur.map { if (it.key == d.key) d else it }
        if (persist) save()
    }

    private fun save() {
        sp.edit().putString("list", JsonArray(list.map { d ->
            JsonObject(mapOf(
                "k" to JsonPrimitive(d.key), "t" to JsonPrimitive(d.kind), "i" to JsonPrimitive(d.id), "n" to JsonPrimitive(d.title),
                "m" to JsonPrimitive(d.image ?: ""), "u" to JsonPrimitive(d.url), "e" to JsonPrimitive(d.ext), "si" to JsonPrimitive(d.showId),
                "sn" to JsonPrimitive(d.showName), "s" to JsonPrimitive(d.season), "ep" to JsonPrimitive(d.episode), "to" to JsonPrimitive(d.total),
                "do" to JsonPrimitive(d.done), "st" to JsonPrimitive(d.state.name), "a" to JsonPrimitive(d.added),
            ))
        }).toString()).apply()
    }

    private fun decode(raw: String?): List<Dl> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            fun l(k: String) = s(k).toLongOrNull() ?: 0L
            Dl(s("k"), s("t"), s("i"), s("n"), s("m").ifEmpty { null }, s("u"), s("e"), s("si"), s("sn"), l("s").toInt(), l("ep").toInt(),
                l("to"), l("do"), runCatching { DlState.valueOf(s("st")) }.getOrDefault(DlState.Paused), l("a"))
        }.filter { it.key.isNotEmpty() }
    }.getOrDefault(emptyList())
}
