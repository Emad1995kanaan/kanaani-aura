package com.kanaani.ibo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Message affiché dans le bandeau défilant. [until] : fin d'affichage (ms) ; [liveOnly] : seulement au Direct. */
data class Msg(val id: String, val text: String, val sent: Long, val until: Long, val liveOnly: Boolean, val style: String = "ticker")

/** Façons d'afficher un message (choisies dans le panneau). */
val MSG_STYLES = setOf("ticker", "top", "popup", "full", "corner")

/**
 * Messages instantanés via ntfy (https://ntfy.sh) : l'application reste abonnée à un sujet secret ; un message publié
 * (appli ntfy sur le téléphone) apparaît en 1 à 2 s sur tous les appareils où l'application est ouverte.
 *
 * Commandes en tête du message : [10s] [5m] [2h] = durée (1 h par défaut), [live] = seulement au Direct,
 * [clear] = efface les messages en cours.
 */
object Messages {
    var list by mutableStateOf<List<Msg>>(emptyList())
        private set

    private val stream = Http.client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val token = Regex("""^\s*\[([^\]]*)]""")

    /** Boucle d'abonnement (tant que l'application est ouverte) ; reconnexion automatique. */
    suspend fun run() {
        val topic = BuildConfig.MSG_TOPIC
        if (topic.isBlank()) return
        val server = BuildConfig.MSG_SERVER.trimEnd('/')
        var since = "12h"
        var wait = 2_000L
        while (coroutineContext.isActive) {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val call = stream.newCall(Request.Builder().url("$server/$topic/json?since=$since").build())
                    val handle = coroutineContext.job.invokeOnCompletion { call.cancel() }
                    try {
                        call.execute().use { r ->
                            if (!r.isSuccessful) return@runCatching false
                            val src = r.body.source()
                            while (true) {
                                val line = src.readUtf8Line() ?: break
                                val o = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
                                if (s("event") != "message") continue
                                val id = s("id") ?: continue
                                since = id
                                handle(id, s("message").orEmpty(), (s("time")?.toLongOrNull() ?: 0L) * 1000)
                            }
                        }
                        true
                    } finally { handle.dispose() }
                }.getOrDefault(false)
            }
            wait = if (ok) 2_000L else (wait * 2).coerceAtMost(60_000L)
            delay(wait)
        }
    }

    /** Message reçu du panneau de contrôle (si ntfy ne l'a pas déjà livré). */
    fun fromPanel(id: String, text: String, sent: Long, until: Long, liveOnly: Boolean, style: String = "ticker") {
        val t = text.trim()
        if (t.isEmpty() || until <= System.currentTimeMillis() || list.any { it.id == id || (it.text == t && kotlin.math.abs(it.sent - sent) < 120_000) }) return
        list = (list + Msg(id, t, sent, until, liveOnly, if (style in MSG_STYLES) style else "ticker")).sortedBy { it.sent }.takeLast(20)
    }

    private fun handle(id: String, raw: String, sentAt: Long) {
        var text = raw
        var duration = 60 * 60_000L
        var liveOnly = false
        var clear = false
        var style = "ticker"
        while (true) {
            val m = token.find(text) ?: break
            val t = m.groupValues[1].trim().lowercase()
            val d = Regex("""^(\d+)\s*([smh])$""").find(t)
            when {
                d != null -> duration = d.groupValues[1].toLong() * when (d.groupValues[2]) { "s" -> 1_000L; "m" -> 60_000L; else -> 3_600_000L }
                t == "live" -> liveOnly = true
                t == "clear" -> clear = true
                t in MSG_STYLES -> style = t
                else -> break // crochets qui font partie du texte
            }
            text = text.substring(m.range.last + 1)
        }
        val sent = if (sentAt > 0) sentAt else System.currentTimeMillis()
        if (clear) { list = list.filter { it.sent > sent }; return }
        text = text.trim()
        if (text.isEmpty() || list.any { it.id == id }) return
        val msg = Msg(id, text, sent, sent + duration, liveOnly, style)
        if (msg.until <= System.currentTimeMillis()) return
        list = (list + msg).sortedBy { it.sent }.takeLast(20)
    }
}
