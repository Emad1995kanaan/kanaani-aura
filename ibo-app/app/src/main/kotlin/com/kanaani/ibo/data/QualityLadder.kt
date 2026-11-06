package com.kanaani.ibo.data

import kotlinx.coroutines.sync.withLock

/**
 * Aura : mêmes chaînes en plusieurs qualités (« beIN Sports 1 FHD », « … HD », « … SD »).
 * Quand une chaîne bloque, le lecteur demande ici la même chaîne en qualité juste en dessous.
 */
object QualityLadder {
    /** Seulement les chaînes qui existent en plusieurs qualités : id → groupe (nom sans qualité). */
    @Volatile private var groupOf: Map<String, String> = emptyMap()
    @Volatile private var groups: Map<String, List<Pair<Channel, Double>>> = emptyMap()
    @Volatile private var loaded = false
    private val lock = kotlinx.coroutines.sync.Mutex()

    /**
     * Prépare les groupes UNE fois, en arrière-plan (jamais sur le fil de l'écran). Appelé à l'ouverture du Direct.
     * Toute erreur est ignorée : sans groupes, il n'y a simplement pas de bascule de qualité.
     */
    suspend fun ensure(x: Xtream) {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            try {
                val all = x.channels(null)
                if (all.isEmpty()) return
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val g = HashMap<String, MutableList<Pair<Channel, Double>>>()
                    for (c in all) {
                        val t = tokens(c.name)
                        val b = baseOf(t)
                        if (b.isEmpty()) continue
                        g.getOrPut(b) { ArrayList(2) }.add(c to scoreOf(t))
                    }
                    val multi = g.filterValues { it.size > 1 }
                    val ids = HashMap<String, String>()
                    multi.forEach { (b, l) -> l.forEach { ids[it.first.id] = b } }
                    groups = multi; groupOf = ids
                }
                loaded = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { }
        }
    }

    fun reset() { loaded = false; groups = emptyMap(); groupOf = emptyMap() }

    /** Identifiant de chaîne dans une adresse « …/live/u/p/123.ts ». */
    fun idOf(url: String): String = url.substringAfterLast('/').substringBefore('.')

    /** Même chaîne, qualité juste en dessous, en évitant celles déjà essayées. null s'il n'y en a pas. */
    fun lower(currentId: String, tried: Set<String>): Channel? = runCatching {
        val group = groups[groupOf[currentId] ?: return null] ?: return null
        val q = group.firstOrNull { it.first.id == currentId }?.second ?: return null
        group.filter { it.first.id != currentId && it.first.id !in tried && it.second < q }.maxByOrNull { it.second }?.first
    }.getOrNull()

    private val splitter = Regex("[^\\p{L}\\p{N}]+")
    private val sup = listOf("ᵁᴴᴰ" to " UHD ", "ᶠᴴᴰ" to " FHD ", "ᴴᴱⱽᶜ" to " HEVC ", "ᴴᴰ" to " HD ", "ˢᴰ" to " SD ", "⁴ᴷ" to " 4K ", "ᴿᴬᵂ" to " RAW ")
    private val ranks = mapOf(
        "8K" to 6.0, "4K" to 5.0, "UHD" to 5.0, "2160" to 5.0, "2160P" to 5.0,
        "FHD" to 4.0, "FULLHD" to 4.0, "1080" to 4.0, "1080P" to 4.0, "1080I" to 4.0,
        "HD" to 3.0, "720" to 3.0, "720P" to 3.0, "HQ" to 3.0,
        "SD" to 2.0, "480" to 2.0, "480P" to 2.0, "576" to 2.0, "576P" to 2.0,
        "LOW" to 1.0, "LQ" to 1.0, "360" to 1.0, "360P" to 1.0, "240" to 1.0, "240P" to 1.0, "3G" to 1.0, "MOBILE" to 1.0,
    )
    private val codec = setOf("HEVC", "H265", "H264", "265", "264", "X265", "X264")
    private val noise = setOf("FULL")

    private fun tokens(name: String): List<String> {
        var n = name
        sup.forEach { (a, b) -> n = n.replace(a, b) }
        return n.uppercase().split(splitter).filter { it.isNotEmpty() }
    }

    /** Nom sans les mots de qualité ni de codec : « BEIN SPORTS 1 ». */
    private fun baseOf(t: List<String>): String = t.filterNot { it in ranks || it in codec || it in noise }.joinToString(" ")

    /** Qualité estimée d'après le nom (sans mention : entre HD et SD) ; HEVC un peu en dessous de la même qualité. */
    private fun scoreOf(t: List<String>): Double {
        val q = t.mapNotNull { ranks[it] }.maxOrNull() ?: if ("FULL" in t) 4.0 else 2.5
        return if (t.any { it == "HEVC" || it == "H265" || it == "X265" || it == "265" }) q - 0.2 else q
    }
}
