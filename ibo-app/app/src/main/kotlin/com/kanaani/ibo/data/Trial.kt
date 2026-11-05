package com.kanaani.ibo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Comptes d'essai du panneau de contrôle : le client se connecte avec un identifiant donné par le panneau, qui le place
 * sur une « ligne » (vrai compte du serveur). Un seul appareil à la fois ; renouvelé toutes les 90 s.
 */
object Trial {
    /** Réponse du panneau. [error] : none (pas un compte d'essai) | expired | full | device | disabled | net */
    data class Result(val ok: Boolean, val host: String = "", val user: String = "", val pass: String = "", val expires: Long = 0, val wa: String = "", val error: String = "")

    /** Blocage en cours (essai fini, plein, autre appareil…) : fenêtre au-dessus de tout. */
    var block by mutableStateOf<Result?>(null)
    var expires by mutableStateOf(0L)
        private set

    suspend fun login(u: String, p: String): Result {
        val base = BuildConfig.PANEL_URL.trimEnd('/')
        if (base.isBlank()) return Result(false, error = "none")
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject { put("u", u); put("p", p); put("d", Panel.deviceId()); put("k", Panel.kind()) }.toString()
                Http.api.newCall(Request.Builder().url("$base/api/trial/login/${BuildConfig.PANEL_APP}")
                    .post(body.toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching Result(false, error = "net")
                    val o = Json.parseToJsonElement(r.body.string()) as? JsonObject ?: return@runCatching Result(false, error = "net")
                    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    if (s("ok") == "true") Result(true, s("host").trimEnd('/'), s("user"), s("pass"), s("expires").toLongOrNull() ?: 0L, s("wa"))
                    else Result(false, wa = s("wa"), error = s("error").ifEmpty { "none" })
                }
            }.getOrElse { Result(false, error = "net") }
        }.also { if (it.ok) expires = it.expires }
    }

    /** Boucle tant que l'application est ouverte avec un compte d'essai. */
    suspend fun keepAlive() {
        while (true) {
            val (u, p) = Store.trialLogin() ?: return
            val r = login(u, p)
            when {
                r.ok -> {
                    block = null
                    val s = Store.session
                    if (s == null || s.username != r.user || s.password != r.pass || s.hosts != listOf(r.host)) Store.trialSwitch(r)
                }
                r.error == "net" -> {} // pas de réseau : on réessaie plus tard sans bloquer
                else -> block = r
            }
            delay(90_000L)
        }
    }
}
