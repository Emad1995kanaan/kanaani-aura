package com.kanaani.ibo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request

/**
 * Noms des catégories en hébreu (nom du serveur → nom hébreu), lus depuis i18n/categories_he.json sur GitHub : on
 * corrige une traduction sans mettre à jour l'application. Gardés sur l'appareil pour marcher hors ligne.
 */
object Translations {
    var he by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var sp: SharedPreferences? = null
    private var loadedAt = 0L

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("translations", Context.MODE_PRIVATE)
        he = parse(sp?.getString("he", null))
    }

    /** Nom affiché d'une catégorie du serveur dans la langue de l'application. */
    fun category(name: String): String = if (Store.language == "he" && forThisServer()) he[name.trim()] ?: name else name

    /** Traductions faites pour ces serveurs seulement : ailleurs, les catégories gardent leur nom d'origine. */
    private val SERVERS = listOf("amlive.cc", "kanaani.site")
    private fun forThisServer(): Boolean {
        val h = runCatching { java.net.URI(Store.session?.host ?: return false).host?.lowercase() }.getOrNull() ?: return false
        return SERVERS.any { h == it || h.endsWith(".$it") }
    }

    suspend fun refresh() {
        val url = BuildConfig.I18N_URL
        if (url.isBlank() || System.currentTimeMillis() - loadedAt < 60 * 60_000L) return
        val raw = withContext(Dispatchers.IO) {
            runCatching {
                val fresh = url + (if ('?' in url) "&" else "?") + "t=" + System.currentTimeMillis() / 300_000L
                Http.client.newCall(Request.Builder().url(fresh).build()).execute().use { r -> if (r.isSuccessful) r.body.string() else null }
            }.getOrNull()
        } ?: return
        val map = parse(raw)
        if (map.isEmpty()) return
        he = map
        sp?.edit()?.putString("he", raw)?.apply()
        loadedAt = System.currentTimeMillis()
    }

    private fun parse(raw: String?): Map<String, String> = runCatching {
        if (raw.isNullOrBlank()) return emptyMap()
        (Json.parseToJsonElement(raw) as JsonObject).mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { k.trim() to it }
        }.toMap()
    }.getOrDefault(emptyMap())
}
