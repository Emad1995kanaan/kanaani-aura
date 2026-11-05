package com.kanaani.ibo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request

/**
 * Cours du dollar, du dinar jordanien et de l'euro en shekels, pour le bas de l'accueil. Source principale :
 * fawazahmed0/currency-api (données libres CC0, via jsDelivr) ; secours : open.er-api.com (attribution affichée).
 * Mis à jour une fois par jour par les sources ; relu au plus toutes les heures.
 */
object Rates {
    /** Shekels pour 1 unité de chaque devise. */
    data class Snapshot(val usd: Double, val jod: Double, val eur: Double, val viaErApi: Boolean)

    var value by mutableStateOf<Snapshot?>(null)
        private set
    private var loadedAt = 0L

    suspend fun refresh() {
        if (value != null && System.currentTimeMillis() - loadedAt < 60 * 60_000L) return
        val s = withContext(Dispatchers.IO) { fromFawaz() ?: fromErApi() } ?: return
        value = s
        loadedAt = System.currentTimeMillis()
    }

    private fun get(url: String): JsonObject? = runCatching {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) null else Json.parseToJsonElement(r.body.string()) as? JsonObject
        }
    }.getOrNull()

    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    /** Taux depuis une table « 1 USD = x devise ». */
    private fun snapshot(ils: Double?, jod: Double?, eur: Double?, er: Boolean): Snapshot? {
        if (ils == null || jod == null || eur == null || jod <= 0 || eur <= 0) return null
        return Snapshot(usd = ils, jod = ils / jod, eur = ils / eur, viaErApi = er)
    }

    private fun fromFawaz(): Snapshot? {
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json",
            "https://latest.currency-api.pages.dev/v1/currencies/usd.min.json",
        )
        for (u in urls) {
            val t = get(u)?.get("usd") as? JsonObject ?: continue
            snapshot(t.num("ils"), t.num("jod"), t.num("eur"), er = false)?.let { return it }
        }
        return null
    }

    private fun fromErApi(): Snapshot? {
        val o = get("https://open.er-api.com/v6/latest/USD") ?: return null
        val r = o["rates"] as? JsonObject ?: return null
        return snapshot(r.num("ILS"), r.num("JOD"), r.num("EUR"), er = true)
    }
}
