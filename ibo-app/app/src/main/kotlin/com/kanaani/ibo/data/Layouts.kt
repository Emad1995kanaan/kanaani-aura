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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Un ordre préparé dans le panneau (nom, par défaut ou non, version = date de modification). */
data class LayoutRef(val id: Long, val name: String, val default: Boolean, val v: Long)

/**
 * Ordres prêts du panneau : ordre des catégories (masquées, renommées) des chaînes, films et séries, et ordre des
 * chaînes dans chaque catégorie. Mode : « auto » = ordre par défaut du panneau (nouveaux clients), un identifiant =
 * cet ordre-là, « custom » = l'utilisateur a rangé lui-même (on n'y touche plus jusqu'à ce qu'il en choisisse un).
 */
object Layouts {
    private var sp: SharedPreferences? = null
    var list by mutableStateOf<List<LayoutRef>>(emptyList())
        private set
    var mode by mutableStateOf("auto")
        private set
    /** Ordre des chaînes par catégorie (de l'ordre suivi). */
    private var channels: Map<String, List<String>> = emptyMap()
    @Volatile private var applying = false

    fun init(ctx: Context, userCustomized: Boolean) {
        val p = ctx.getSharedPreferences("layouts", Context.MODE_PRIVATE)
        sp = p
        // Première fois avec cette version : celui qui a déjà rangé lui-même garde son ordre.
        mode = p.getString("mode", null) ?: (if (userCustomized) "custom" else "auto").also { p.edit().putString("mode", it).apply() }
        channels = decodeChannels(p.getString("channels", null))
    }

    /** Ordre des chaînes d'une catégorie, si l'ordre suivi en a un. */
    fun channelOrder(categoryId: String): List<String>? = if (mode == "custom") null else channels[categoryId]

    /** L'utilisateur a rangé / masqué / renommé lui-même. */
    fun markCustom() {
        if (applying || mode == "custom") return
        mode = "custom"
        sp?.edit()?.putString("mode", "custom")?.apply()
    }

    /** Liste reçue avec la configuration du panneau : applique l'ordre suivi s'il a changé. */
    suspend fun onConfig(refs: List<LayoutRef>, assigned: Pair<Long, Long>? = null) {
        list = refs
        // Ordre envoyé à ce client en particulier : appliqué même s'il avait rangé lui-même.
        if (assigned != null && assigned.second > (sp?.getLong("assigned_sent", 0L) ?: 0L)) {
            sp?.edit()?.putLong("assigned_sent", assigned.second)?.apply()
            mode = assigned.first.toString()
            sp?.edit()?.putString("mode", mode)?.remove("applied_v")?.apply()
        }
        val target = target() ?: return
        if (sp?.getLong("applied_v", -1L) == target.v && sp?.getLong("applied_id", -1L) == target.id) return
        fetchAndApply(target)
    }

    private fun target(): LayoutRef? = when (mode) {
        "custom" -> null
        "auto" -> list.firstOrNull { it.default } ?: list.firstOrNull()
        else -> list.firstOrNull { it.id.toString() == mode } ?: list.firstOrNull { it.default }
    }

    /** Choix dans les Réglages : un ordre du panneau (suivi ensuite automatiquement). */
    suspend fun choose(ref: LayoutRef): Boolean {
        mode = if (ref.default) "auto" else ref.id.toString()
        sp?.edit()?.putString("mode", mode)?.apply()
        return fetchAndApply(ref)
    }

    private suspend fun fetchAndApply(ref: LayoutRef): Boolean {
        val base = BuildConfig.PANEL_URL.trimEnd('/')
        if (base.isBlank()) return false
        val root = withContext(Dispatchers.IO) {
            runCatching {
                Http.client.newCall(Request.Builder().url("$base/api/layout/${BuildConfig.PANEL_APP}/${ref.id}").build()).execute().use { r ->
                    if (r.isSuccessful) Json.parseToJsonElement(r.body.string()) as? JsonObject else null
                }
            }.getOrNull()
        } ?: return false
        val data = root["data"] as? JsonObject ?: return false
        applying = true
        try {
            for (kind in listOf("live", "movie", "series")) {
                val k = data[kind] as? JsonObject
                Store.applyLayout(kind, ids(k?.get("order")), ids(k?.get("hidden")).toSet(), names(k?.get("names")))
            }
            channels = ((data["live"] as? JsonObject)?.get("channels") as? JsonObject)?.mapValues { ids(it.value) }.orEmpty()
        } finally { applying = false }
        sp?.edit()?.putLong("applied_v", ref.v)?.putLong("applied_id", ref.id)
            ?.putString("channels", JsonObject(channels.mapValues { e -> JsonArray(e.value.map { JsonPrimitive(it) }) }).toString())?.apply()
        return true
    }

    /**
     * Outil caché (5 appuis sur « À propos ») : envoie l'ordre de cet appareil au panneau, avec l'identifiant et le mot
     * de passe d'un administrateur du panneau. [replaceId] = remplacer cet ordre, sinon nouvel ordre nommé [name].
     */
    suspend fun upload(user: String, pass: String, name: String, replaceId: Long?): String? {
        val base = BuildConfig.PANEL_URL.trimEnd('/')
        if (base.isBlank()) return "no panel"
        val data = buildJsonObject {
            for (kind in listOf("live", "movie", "series")) {
                put(kind, buildJsonObject {
                    put("order", JsonArray(Store.categoryOrders[kind].orEmpty().map { JsonPrimitive(it) }))
                    put("hidden", JsonArray(Store.hiddenCategories[kind].orEmpty().map { JsonPrimitive(it) }))
                    put("names", JsonObject(Store.categoryNames[kind].orEmpty().mapValues { JsonPrimitive(it.value) }))
                    if (kind == "live") put("channels", JsonObject(channels.mapValues { e -> JsonArray(e.value.map { JsonPrimitive(it) }) }))
                })
            }
        }
        val body = buildJsonObject {
            put("username", user); put("password", pass); put("name", name)
            put("mode", if (replaceId != null) "replace" else "new"); if (replaceId != null) put("id", replaceId)
            put("data", data)
        }.toString()
        return withContext(Dispatchers.IO) {
            runCatching {
                Http.api.newCall(Request.Builder().url("$base/api/layout-upload/${BuildConfig.PANEL_APP}")
                    .post(body.toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                    if (r.isSuccessful) null
                    else ((Json.parseToJsonElement(r.body.string()) as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull ?: "HTTP ${r.code}"
                }
            }.getOrElse { "network" }
        }
    }

    fun parseRefs(arr: JsonArray?): List<LayoutRef> = arr.orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        LayoutRef(s("id")?.toLongOrNull() ?: return@mapNotNull null, s("name").orEmpty(), s("default") == "true", s("v")?.toLongOrNull() ?: 0L)
    }

    private fun ids(e: Any?): List<String> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    private fun names(e: Any?): Map<String, String> = (e as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { k to it } }?.toMap().orEmpty()
    private fun decodeChannels(raw: String?): Map<String, List<String>> = runCatching {
        if (raw.isNullOrBlank()) emptyMap() else (Json.parseToJsonElement(raw) as JsonObject).mapValues { ids(it.value) }
    }.getOrDefault(emptyMap())
}
