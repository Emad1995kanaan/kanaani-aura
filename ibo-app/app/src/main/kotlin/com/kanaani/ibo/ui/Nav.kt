package com.kanaani.ibo.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.data.Channel
import com.kanaani.ibo.data.Episode
import com.kanaani.ibo.data.Movie
import com.kanaani.ibo.data.Show
import com.kanaani.ibo.data.Watch

/**
 * Écrans de l'application. Chaque écran garde son état (catégorie choisie, défilement, élément focalisé) dans son
 * objet : on le retrouve tel quel en revenant en arrière.
 */
sealed class Screen {
    object Home : Screen()
    class Live : Screen() {
        // null à l'ouverture : le premier vrai bouquet est choisi dès que la liste arrive (focus dessus).
        var category by mutableStateOf<String?>(null)
        /** Recherche dans les noms de catégories. */
        var catFilter by mutableStateOf("")
        var catSearching by mutableStateOf(false)
        /** Premier affichage fait (focus initial sur la catégorie, pas sur les chaînes). */
        var opened = false
        var focusIndex by mutableIntStateOf(0)
        val catState = LazyListState()
        val listState = LazyListState()
        var filter by mutableStateOf("")
        var searching by mutableStateOf(false)
        /** Chaîne affichée dans l'aperçu (un 2e appui ouvre le plein écran). */
        var playing by mutableStateOf<Channel?>(null)
    }
    class Catalog(val series: Boolean) : Screen() {
        // À l'ouverture : « Derniers ajouts », focus sur le plus récent.
        var category by mutableStateOf<String?>(LATEST)
        var focusIndex by mutableIntStateOf(0)
        var catFilter by mutableStateOf("")
        var catSearching by mutableStateOf(false)
        val catState = LazyListState()
        val gridState = LazyGridState()
        var filter by mutableStateOf("")
        var searching by mutableStateOf(false)
    }
    class MovieDetail(val movie: Movie) : Screen()
    class ShowDetail(val show: Show) : Screen() {
        var season by mutableStateOf<Int?>(null)
    }
    /** Lecture plein écran. [channels]/[index] : liste pour zapper (Direct) ; [episodes] : épisodes suivants. */
    class Play(
        val title: String,
        val url: String,
        val live: Boolean,
        val channels: List<Channel> = emptyList(),
        val index: Int = 0,
        val episodes: List<Episode> = emptyList(),
        /** Film / épisode à mémoriser pour « Continuer à regarder » (position ignorée) ; [startMs] = reprise. */
        val watch: Watch? = null,
        val startMs: Long = 0,
    ) : Screen()
    object Favorites : Screen()
    /** Ordre des catégories d'un type : "live", "movie" ou "series". */
    class Reorder(val kind: String) : Screen()
    object Account : Screen()
    /** Téléchargements hors ligne. */
    object Downloads : Screen()
    object Settings : Screen()

    companion object {
        /** Catégorie spéciale du Direct : les favoris. */
        const val FAVORITES = "__fav__"
        /** Catégorie spéciale : tout. */
        const val ALL = "__all__"
        /** Films / séries : derniers ajouts ou dernières mises à jour. */
        const val LATEST = "__latest__"
        /** Films / séries : continuer à regarder. */
        const val CONTINUE = "__continue__"
    }
}

/** Pile de navigation : Retour dépile ; la racine est l'accueil. */
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }
    /** false si on est déjà à la racine (l'activité se ferme). */
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun reset() { stack.clear(); stack.add(Screen.Home) }
}
