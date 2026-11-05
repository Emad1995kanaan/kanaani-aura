package com.kanaani.ibo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.Brand
import com.kanaani.ibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Request

/** Message de maintenance affiché à l'ouverture. [until] = 0 : sans fin. */
data class Notice(val text: String, val until: Long)

/**
 * Lien avec le panneau de contrôle (Cloudflare) : publicités, hôtes, messages et avis de maintenance, lus à l'ouverture
 * puis toutes les 10 minutes. Rien n'est bloquant : sans réponse, on garde la dernière configuration reçue (hôtes
 * gardés sur l'appareil) puis celle de l'application.
 */
object Panel {
    var notice by mutableStateOf<Notice?>(null)
        private set
    private var sp: SharedPreferences? = null

    private var tv = false

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("panel", Context.MODE_PRIVATE)
        tv = ctx.packageManager.hasSystemFeature("android.software.leanback")
    }

    /** Identifiant anonyme de l'appareil (compte des appareils actifs dans le panneau). */
    private fun device(): String {
        val p = sp ?: return ""
        return p.getString("device", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    fun deviceId(): String = device()
    fun kind(): String = if (tv) "tv" else "phone"

    /** Hôtes : liste du panneau (gardée sur l'appareil) sinon ceux de l'application. */
    fun hosts(): List<String> {
        val saved = sp?.getString("hosts", null)?.split('\n')?.map { it.trim() }?.filter { it.startsWith("http") }.orEmpty()
        return saved.ifEmpty { Brand.hosts }
    }

    suspend fun refresh() {
        val base = BuildConfig.PANEL_URL.trimEnd('/')
        if (base.isBlank()) return
        val root = withContext(Dispatchers.IO) {
            runCatching {
                // « Je suis actif » au plus toutes les 25 minutes (limite d'écritures du panneau).
                val now = System.currentTimeMillis()
                val ping = now - (sp?.getLong("ping", 0L) ?: 0L) > 25 * 60_000L
                // Identifiant du client : pour un ordre envoyé à ce client en particulier (panneau › Ordre).
                val who = java.net.URLEncoder.encode(Store.trialLogin()?.first ?: Store.session?.username ?: "", "UTF-8")
                val q = (if (ping) "?d=${device()}&v=${BuildConfig.VERSION_NAME}&k=${if (tv) "tv" else "phone"}&u=$who" else "?u=$who")
                Http.client.newCall(Request.Builder().url("$base/api/config/${BuildConfig.PANEL_APP}$q").build()).execute().use { r ->
                    if (ping && r.isSuccessful) sp?.edit()?.putLong("ping", now)?.apply()
                    if (!r.isSuccessful) null else Json.parseToJsonElement(r.body.string()) as? JsonObject
                }
            }.getOrNull()
        } ?: return
        runCatching {
            // Publicités (remplacent ads.json de GitHub).
            (root["ads"] as? JsonObject)?.let { a ->
                fun n(k: String) = (a[k] as? JsonPrimitive)?.contentOrNull
                val list = (a["billboard"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                val pinned = (a["pinned"] as? JsonArray).orEmpty().mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                    Slide(s("image") ?: return@mapNotNull null, s("title") ?: "", s("rating"))
                }
                Ads.fromPanel(n("interval")?.toIntOrNull() ?: 10, list, n("mode") ?: "mix", n("movies")?.toIntOrNull() ?: 5, n("every")?.toIntOrNull() ?: 3, pinned)
            }
            // Hôtes.
            val hosts = (root["hosts"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.trimEnd('/') }
                .filter { it.startsWith("http") }.distinct()
            if (hosts.isNotEmpty()) {
                sp?.edit()?.putString("hosts", hosts.joinToString("\n"))?.apply()
                if (Store.trialLogin() == null) Store.session?.updateHosts(hosts)
            }
            // Avis de maintenance.
            notice = (root["notice"] as? JsonObject)?.let { n ->
                val text = (n["text"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val until = (n["until"] as? JsonPrimitive)?.longOrNull ?: 0L
                if (text.isEmpty() || (until > 0 && until < System.currentTimeMillis())) null else Notice(text, until)
            }
            // Ordres prêts (catégories, chaînes) : celui que suit l'appareil est mis à jour s'il a changé.
            (root["layouts"] as? JsonArray)?.let { arr ->
                val a = root["assigned"] as? JsonObject
                val assigned = a?.let { o -> (o["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let { id -> id to ((o["sent"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L) } }
                Layouts.onConfig(Layouts.parseRefs(arr), assigned)
            }
            // Messages (en plus de ntfy, s'il n'a pas pu les livrer).
            (root["messages"] as? JsonArray).orEmpty().forEach { e ->
                val o = e as? JsonObject ?: return@forEach
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
                Messages.fromPanel("p" + (s("id") ?: return@forEach), s("text").orEmpty(), s("sent")?.toLongOrNull() ?: 0L,
                    s("until")?.toLongOrNull() ?: 0L, s("liveOnly") == "true", s("style") ?: "ticker")
            }
        }
    }
}
