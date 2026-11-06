package com.kanaani.ibo.data

/**
 * Aura : mêmes chaînes en plusieurs qualités (« beIN Sports 1 FHD », « … HD », « … SD »).
 * Quand une chaîne bloque, le lecteur demande ici la même chaîne en qualité juste en dessous.
 */
object QualityLadder {
    @Volatile private var byId: Map<String, Channel> = emptyMap()
    @Volatile private var groups: Map<String, List<Channel>> = emptyMap()

    /** Charge toutes les chaînes (liste en cache du serveur) et les regroupe par nom sans la qualité. */
    suspend fun load(x: Xtream) {
        val all = runCatching { x.channels(null) }.getOrNull() ?: return
        if (all.isEmpty()) return
        byId = all.associateBy { it.id }
        groups = all.groupBy { base(it.name) }.filterKeys { it.isNotEmpty() }.filterValues { it.size > 1 }
    }

    /** Identifiant de chaîne dans une adresse « …/live/u/p/123.ts ». */
    fun idOf(url: String): String = url.substringAfterLast('/').substringBefore('.')

    fun channel(id: String): Channel? = byId[id]

    /** Même chaîne, qualité juste en dessous, en évitant celles déjà essayées. null s'il n'y en a pas. */
    fun lower(currentId: String, tried: Set<String>): Channel? {
        val cur = byId[currentId] ?: return null
        val group = groups[base(cur.name)] ?: return null
        val q = score(cur.name)
        return group.filter { it.id != cur.id && it.id !in tried && score(it.name) < q }
            .maxByOrNull { score(it.name) }
    }

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
        return n.uppercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    }

    /** Nom sans les mots de qualité ni de codec : « BEIN SPORTS 1 ». */
    fun base(name: String): String = tokens(name).filterNot { it in ranks || it in codec || it in noise }.joinToString(" ")

    /** Qualité estimée d'après le nom (sans mention : entre HD et SD) ; HEVC un peu en dessous de la même qualité. */
    fun score(name: String): Double {
        val t = tokens(name)
        val q = t.mapNotNull { ranks[it] }.maxOrNull() ?: if ("FULL" in t) 4.0 else 2.5
        return if (t.any { it == "HEVC" || it == "H265" || it == "X265" || it == "265" }) q - 0.2 else q
    }
}
