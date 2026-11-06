package com.kanaani.ibo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.Brand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/** Élément favori, avec ce qu'il faut pour l'afficher et le lancer sans recharger la catégorie. */
data class Favorite(val kind: String, val id: String, val name: String, val image: String?, val ext: String = "")

/**
 * Lecture en cours d'un film ou d'une série (« Continuer à regarder »). Une entrée par film ou par série : pour une
 * série, c'est le dernier épisode regardé ([episodeId], [season], [episode]). [pos]/[dur] en ms, [at] = horodatage.
 */
data class Watch(
    val kind: String, val id: String, val name: String, val image: String?, val ext: String,
    val episodeId: String = "", val season: Int = 0, val episode: Int = 0,
    val pos: Long = 0, val dur: Long = 0, val at: Long = 0,
) {
    val progress: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

/**
 * Réglages et état persistants (SharedPreferences) : identifiants, langue, favoris. Les valeurs lues par l'interface
 * sont des états Compose : un changement (langue, connexion) recompose tout de suite.
 */
object Store {
    private lateinit var sp: SharedPreferences

    var language by mutableStateOf("ar")
        private set
    var session by mutableStateOf<Xtream?>(null)
        private set
    var favorites by mutableStateOf<List<Favorite>>(emptyList())
        private set
    /** Ordre des catégories choisi par l'utilisateur, par type ("live", "movie", "series") : liste d'identifiants. */
    var categoryOrders by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    /** Noms de catégories choisis par l'utilisateur, par type puis par identifiant. */
    var categoryNames by mutableStateOf<Map<String, Map<String, String>>>(emptyMap())
        private set

    /** « Continuer à regarder », le plus récent en premier. */
    var watching by mutableStateOf<List<Watch>>(emptyList())
        private set

    /** Format de l'image en plein écran : 0 = adapter, 1 = remplir (étirer), 2 = zoom (rogner). */
    var aspect by mutableIntStateOf(0)
        private set

    /** Thème de l'accueil : 0 = classique, 1 = panneau publicitaire. */
    var homeTheme by mutableIntStateOf(0)
        private set

    fun chooseHomeTheme(t: Int) {
        homeTheme = t.coerceIn(0, 1)
        sp.edit().putInt("home_theme", homeTheme).apply()
    }

    fun chooseAspect(mode: Int) {
        aspect = mode.mod(3)
        sp.edit().putInt("aspect", aspect).apply()
    }

    /** Catégories masquées par l'utilisateur, par type : ni affichées ni chargées (et exclues de « Tous »). */
    var hiddenCategories by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set

    fun isHidden(kind: String, categoryId: String?) = categoryId != null && hiddenCategories[kind]?.contains(categoryId) == true

    fun setHidden(kind: String, ids: Set<String>, hide: Boolean) {
        Layouts.markCustom()
        val cur = hiddenCategories[kind].orEmpty()
        val upd = if (hide) cur + ids else cur - ids
        hiddenCategories = hiddenCategories + (kind to upd)
        if (upd.isEmpty()) sp.edit().remove("hidden_$kind").apply() else sp.edit().putString("hidden_$kind", upd.joinToString("\n")).apply()
    }

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("kanaaniaura", Context.MODE_PRIVATE)
        lockedCategories = sp.getStringSet("locked_cats", emptySet())?.toSet() ?: emptySet()
        Translations.init(ctx)
        Downloads.init(ctx)
        ListCache.init(ctx)
        Panel.init(ctx)
        language = sp.getString("lang", "ar") ?: "ar"
        val u = sp.getString("user", null)
        val p = sp.getString("pass", null)
        val th = sp.getString("trial_host", null)
        session = when {
            u.isNullOrEmpty() || p == null || Brand.host.isEmpty() -> null
            // Compte d'essai : uniquement le serveur de sa ligne (le panneau peut le changer, voir Trial.keepAlive)
            th != null && sp.getString("trial_u", null) != null -> Xtream(listOf(th), u, p)
            else -> Xtream(Panel.hosts(), u, p)
        }
        favorites = decode(sp.getString("favorites", null))
        categoryOrders = listOf("live", "movie", "series").associateWith { k ->
            sp.getString("order_$k", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        }
        categoryNames = listOf("live", "movie", "series").associateWith { k -> decodeNames(sp.getString("names_$k", null)) }
        hiddenCategories = listOf("live", "movie", "series").associateWith { k ->
            sp.getString("hidden_$k", null)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        }
        watching = decodeWatch(sp.getString("watching", null))
        Layouts.init(ctx, categoryOrders.values.any { it.isNotEmpty() } || hiddenCategories.values.any { it.isNotEmpty() } || categoryNames.values.any { it.isNotEmpty() })
        aspect = sp.getInt("aspect", 0).mod(3)
        homeTheme = sp.getInt("home_theme", 0).let { if (it == 1) 1 else 0 }
    }

    /** Dernier serveur (principal / secours) qui a répondu. */
    /** Aura : dernière chaîne regardée (catégorie + chaîne) pour rouvrir le direct dessus. */
    fun lastLive(): Pair<String, String>? {
        val c = sp.getString("last_live_cat", null) ?: return null
        val ch = sp.getString("last_live_ch", null) ?: return null
        return c to ch
    }
    fun rememberLive(category: String, channel: String) { sp.edit().putString("last_live_cat", category).putString("last_live_ch", channel).apply() }

    /** Aura : catégories verrouillées (code parental) et code (0000 par défaut). */
    var lockedCategories by androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
        private set
    fun isLocked(kind: String, id: String) = "$kind:$id" in lockedCategories
    fun toggleLock(kind: String, id: String) {
        val k = "$kind:$id"
        lockedCategories = if (k in lockedCategories) lockedCategories - k else lockedCategories + k
        sp.edit().putStringSet("locked_cats", lockedCategories).apply()
    }
    fun pin(): String = sp.getString("parent_pin", "0000") ?: "0000"
    fun setPin(p: String) { sp.edit().putString("parent_pin", p).apply() }

    fun lastHost(): String? = sp.getString("host", null)
    fun rememberHost(h: String) { sp.edit().putString("host", h).apply() }

    fun watchOf(kind: String, id: String): Watch? = watching.firstOrNull { it.kind == kind && it.id == id }

    /** Enregistre la position (remplace l'entrée du même film / de la même série et la remet en tête). */
    fun saveWatch(w: Watch) {
        val entry = w.copy(at = System.currentTimeMillis())
        watching = (listOf(entry) + watching.filterNot { it.kind == w.kind && it.id == w.id }).take(80)
        sp.edit().putString("watching", encodeWatch(watching)).apply()
    }

    fun removeWatch(kind: String, id: String) {
        if (watchOf(kind, id) == null) return
        watching = watching.filterNot { it.kind == kind && it.id == id }
        sp.edit().putString("watching", encodeWatch(watching)).apply()
    }

    private fun encodeWatch(list: List<Watch>): String = JsonArray(list.map { w ->
        JsonObject(mapOf(
            "k" to JsonPrimitive(w.kind), "i" to JsonPrimitive(w.id), "n" to JsonPrimitive(w.name),
            "m" to JsonPrimitive(w.image ?: ""), "e" to JsonPrimitive(w.ext), "ei" to JsonPrimitive(w.episodeId),
            "s" to JsonPrimitive(w.season), "en" to JsonPrimitive(w.episode),
            "p" to JsonPrimitive(w.pos), "d" to JsonPrimitive(w.dur), "a" to JsonPrimitive(w.at),
        ))
    }).toString()

    private fun decodeWatch(raw: String?): List<Watch> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            fun l(k: String) = s(k).toLongOrNull() ?: 0L
            Watch(s("k"), s("i"), s("n"), s("m").ifEmpty { null }, s("e"), s("ei"), l("s").toInt(), l("en").toInt(), l("p"), l("d"), l("a"))
        }.filter { it.id.isNotEmpty() }
    }.getOrDefault(emptyList())

    /** Renomme une catégorie ; nom vide = nom d'origine. */
    fun renameCategory(kind: String, id: String, name: String) {
        Layouts.markCustom()
        val current = categoryNames[kind].orEmpty()
        val updated = if (name.isBlank()) current - id else current + (id to name.trim())
        categoryNames = categoryNames + (kind to updated)
        sp.edit().putString("names_$kind", JsonObject(updated.mapValues { JsonPrimitive(it.value) }).toString()).apply()
    }

    /** Catégories dans l'ordre et sous les noms choisis par l'utilisateur. */
    fun arrange(kind: String, list: List<Category>): List<Category> {
        val names = categoryNames[kind].orEmpty()
        val hidden = hiddenCategories[kind].orEmpty()
        // Nom choisi par l'utilisateur, sinon traduction (hébreu), sinon nom du serveur.
        return ordered(kind, list.filterNot { it.id in hidden }) { it.id }.map { c -> c.copy(name = names[c.id] ?: Translations.category(c.name)) }
    }

    private fun decodeNames(raw: String?): Map<String, String> = runCatching {
        if (raw.isNullOrBlank()) return emptyMap()
        (Json.parseToJsonElement(raw) as JsonObject).mapValues { (it.value as JsonPrimitive).contentOrNull.orEmpty() }.filterValues { it.isNotEmpty() }
    }.getOrDefault(emptyMap())

    fun setCategoryOrder(kind: String, ids: List<String>) {
        Layouts.markCustom()
        categoryOrders = categoryOrders + (kind to ids)
        if (ids.isEmpty()) sp.edit().remove("order_$kind").apply()
        else sp.edit().putString("order_$kind", ids.joinToString("\n")).apply()
    }

    /** Ordre prêt du panneau (Layouts) : remplace ordre, catégories masquées et noms, sans passer en « ordre perso ». */
    fun applyLayout(kind: String, order: List<String>, hidden: Set<String>, names: Map<String, String>) {
        categoryOrders = categoryOrders + (kind to order)
        hiddenCategories = hiddenCategories + (kind to hidden)
        categoryNames = categoryNames + (kind to names)
        val e = sp.edit()
        if (order.isEmpty()) e.remove("order_$kind") else e.putString("order_$kind", order.joinToString("\n"))
        if (hidden.isEmpty()) e.remove("hidden_$kind") else e.putString("hidden_$kind", hidden.joinToString("\n"))
        e.putString("names_$kind", JsonObject(names.mapValues { JsonPrimitive(it.value) }).toString())
        e.apply()
    }

    /** Applique l'ordre choisi : catégories rangées d'abord, nouvelles catégories du serveur ensuite (ordre d'origine). */
    fun <T> ordered(kind: String, list: List<T>, id: (T) -> String): List<T> {
        val order = categoryOrders[kind].orEmpty()
        if (order.isEmpty()) return list
        val rank = order.withIndex().associate { (i, v) -> v to i }
        return list.withIndex().sortedBy { rank[id(it.value)] ?: (order.size + it.index) }.map { it.value }
    }

    fun chooseLanguage(code: String) {
        language = code
        sp.edit().putString("lang", code).apply()
    }

    fun login(x: Xtream, trial: Pair<String, String>? = null) {
        val e = sp.edit().putString("user", x.username).putString("pass", x.password)
        if (trial != null) e.putString("trial_u", trial.first).putString("trial_p", trial.second).putString("trial_host", x.hosts.first())
        else e.remove("trial_u").remove("trial_p").remove("trial_host")
        e.apply()
        session = x
    }

    /** Identifiants d'essai (ceux donnés par le panneau), null pour un abonnement normal. */
    fun trialLogin(): Pair<String, String>? {
        val u = sp.getString("trial_u", null) ?: return null
        return u to (sp.getString("trial_p", null) ?: return null)
    }

    /** Le panneau a déplacé le compte d'essai sur une autre ligne : nouvelle session. */
    fun trialSwitch(r: Trial.Result) {
        sp.edit().putString("user", r.user).putString("pass", r.pass).putString("trial_host", r.host).apply()
        session = Xtream(listOf(r.host), r.user, r.pass)
    }

    fun logout() {
        sp.edit().remove("user").remove("pass").remove("favorites").remove("watching").remove("trial_u").remove("trial_p").remove("trial_host").apply()
        Trial.block = null
        watching = emptyList()
        // L'ordre des catégories est une préférence de l'appareil : il reste après une déconnexion.
        favorites = emptyList()
        session = null
    }

    fun isFavorite(kind: String, id: String) = favorites.any { it.kind == kind && it.id == id }

    fun toggleFavorite(f: Favorite) {
        favorites = if (isFavorite(f.kind, f.id)) favorites.filterNot { it.kind == f.kind && it.id == f.id } else favorites + f
        sp.edit().putString("favorites", encode(favorites)).apply()
    }

    private fun encode(list: List<Favorite>): String = JsonArray(list.map { f ->
        JsonObject(mapOf(
            "k" to JsonPrimitive(f.kind), "i" to JsonPrimitive(f.id), "n" to JsonPrimitive(f.name),
            "m" to JsonPrimitive(f.image ?: ""), "e" to JsonPrimitive(f.ext),
        ))
    }).toString()

    private fun decode(raw: String?): List<Favorite> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            Favorite(s("k"), s("i"), s("n"), s("m").ifEmpty { null }, s("e"))
        }
    }.getOrDefault(emptyList())
}
