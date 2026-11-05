package com.kanaani.ibo.data

/**
 * Ordre des chaînes israéliennes : d'abord le sport, puis l'information, puis le reste ; chaque chaîne garde ses
 * versions ensemble, de la qualité normale à la 4K (SD → HD → FHD → 4K). Les autres catégories gardent l'ordre du serveur.
 */
object ChannelOrder {
    private val israeli = Regex("""(?i)(israel|isreal|ישראל|إسرائيل|اسرائيل|اسرائيلي|إسرائيلي|^\s*IL\b|\bIL\s*[|:\-]|\|\s*IL\b|\bISR\b)""")

    private val sports = Regex("""(?i)(sport|ספורט|رياض|\bONE\b|charlton|צ'רלטון|eurosport|\bnba\b|\bufc\b)""")
    private val news = Regex("""(?i)(news|חדשות|أخبار|اخبار|\bi24\b|i24news|\bkan\s*news\b)""")

    private val q4k = Regex("""(?i)(\b4k\b|\buhd\b|2160)""")
    private val qFhd = Regex("""(?i)(\bfhd\b|full\s*hd|\bf\.hd\b|1080)""")
    private val qHd = Regex("""(?i)(\bhd\b|720)""")
    private val qualityTokens = Regex("""(?i)(\b4k\b|\buhd\b|\bfhd\b|full\s*hd|\bf\.hd\b|\bhd\b|\bsd\b|2160p?|1080p?|720p?|\bhevc\b|\bh\.?265\b)""")

    fun isIsraeli(categoryName: String?): Boolean = categoryName != null && israeli.containsMatchIn(categoryName)

    /** 0 = normale, 1 = HD, 2 = Full HD, 3 = 4K. */
    fun quality(name: String): Int = when {
        q4k.containsMatchIn(name) -> 3
        qFhd.containsMatchIn(name) -> 2
        qHd.containsMatchIn(name) -> 1
        else -> 0
    }

    /** 0 = sport, 1 = information, 2 = autres. */
    fun group(name: String): Int = when {
        sports.containsMatchIn(name) -> 0
        news.containsMatchIn(name) -> 1
        else -> 2
    }

    /** Nom sans la mention de qualité ni préfixe pays : « IL: Sport 5 FHD » → « sport 5 ». */
    fun baseName(name: String): String = name
        .replace(qualityTokens, " ")
        .replace(Regex("""(?i)^\s*(IL|ISR|ISRAEL)\s*[|:\-]\s*"""), "")
        .replace(Regex("""[|:\-•·]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase()

    fun sortIsraeli(list: List<Channel>): List<Channel> {
        // Position d'origine du premier exemplaire de chaque chaîne : l'ordre du fournisseur est gardé entre chaînes.
        val firstSeen = HashMap<String, Int>()
        list.forEachIndexed { i, c -> firstSeen.putIfAbsent(baseName(c.name), i) }
        return list.sortedWith(
            compareBy<Channel> { group(it.name) }
                .thenBy { firstSeen[baseName(it.name)] ?: Int.MAX_VALUE }
                .thenBy { quality(it.name) },
        )
    }
}
