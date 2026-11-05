package com.kanaani.ibo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanaani.ibo.data.Category
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.data.Xtream
import kotlinx.coroutines.launch

/**
 * Organisation des catégories d'un type ("live", "movie", "series") :
 * sélection d'une ou plusieurs catégories (✓), déplacement du groupe (▲ ▼, en tête, en fin), flèches par ligne pour
 * une catégorie seule, et changement de nom. Tout est enregistré immédiatement.
 */
@Composable
fun ReorderScreen(x: Xtream, s: Screen.Reorder) {
    val server by produceState<List<Category>?>(null, x, s.kind) {
        value = when (s.kind) {
            "live" -> x.liveCategories()
            "movie" -> x.movieCategories()
            else -> x.showCategories()
        }
    }
    val originals = remember(server) { server?.associate { it.id to it.name }.orEmpty() }
    // Ordre de travail (identifiants) ; les noms affichés viennent de Store (renommages compris).
    var order by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(server) { server?.let { order = Store.ordered(s.kind, it) { c -> c.id }.map { c -> c.id } } }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(order != null) { if (order != null) InitialFocusOnce(first) }
    BackHandler(enabled = selected.isNotEmpty() && renaming == null) { selected = emptySet() }
    // Dernière catégorie focalisée : les actions s'y appliquent quand rien n'est sélectionné.
    var focusedId by remember { mutableStateOf<String?>(null) }
    fun targets(): Set<String> = selected.ifEmpty { setOfNotNull(focusedId) }

    fun nameOf(id: String) = Store.categoryNames[s.kind]?.get(id) ?: originals[id]?.let { com.kanaani.ibo.data.Translations.category(it) } ?: id

    fun commit(newOrder: List<String>, focusOn: String? = null) {
        order = newOrder
        Store.setCategoryOrder(s.kind, newOrder)
        val j = focusOn?.let { newOrder.indexOf(it) } ?: -1
        if (j >= 0) scope.launch {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isNotEmpty() && (j <= visible.first().index || j >= visible.last().index)) listState.scrollToItem((j - 3).coerceAtLeast(0))
        }
    }

    /** Groupe sélectionné d'un cran vers le haut (−1) ou le bas (+1), sans changer l'ordre interne du groupe. */
    fun shift(ids: Set<String>, delta: Int) {
        val m = order?.toMutableList() ?: return
        if (delta < 0) {
            for (i in 1..m.lastIndex) if (m[i] in ids && m[i - 1] !in ids) { val t = m[i - 1]; m[i - 1] = m[i]; m[i] = t }
        } else {
            for (i in m.lastIndex - 1 downTo 0) if (m[i] in ids && m[i + 1] !in ids) { val t = m[i + 1]; m[i + 1] = m[i]; m[i] = t }
        }
        commit(m, m.firstOrNull { it in ids })
    }

    fun toEdge(ids: Set<String>, top: Boolean) {
        val m = order ?: return
        val group = m.filter { it in ids }
        val rest = m.filterNot { it in ids }
        commit(if (top) group + rest else rest + group, group.firstOrNull())
    }

    val hidden = Store.hiddenCategories[s.kind].orEmpty()
    /** Masquer / afficher : si tout est déjà masqué on affiche, sinon on masque. */
    fun toggleHidden(ids: Set<String>) { if (ids.isNotEmpty()) Store.setHidden(s.kind, ids, hide = !ids.all { it in hidden }) }

    // Touches de couleur de la télécommande : rouge = monter, bleu = descendre, vert = renommer, jaune = masquer / afficher.
    androidx.compose.runtime.DisposableEffect(renaming) {
        if (renaming == null) com.kanaani.ibo.KeyHook.handler = handler@{ e ->
            val code = e.keyCode
            if (code != android.view.KeyEvent.KEYCODE_PROG_RED && code != android.view.KeyEvent.KEYCODE_PROG_BLUE &&
                code != android.view.KeyEvent.KEYCODE_PROG_GREEN && code != android.view.KeyEvent.KEYCODE_PROG_YELLOW) return@handler false
            if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                val t = targets()
                when (code) {
                    android.view.KeyEvent.KEYCODE_PROG_RED -> if (t.isNotEmpty()) shift(t, -1)
                    android.view.KeyEvent.KEYCODE_PROG_BLUE -> if (t.isNotEmpty()) shift(t, +1)
                    android.view.KeyEvent.KEYCODE_PROG_YELLOW -> toggleHidden(t)
                    else -> if (t.size == 1) renaming = t.first()
                }
            }
            true
        }
        onDispose { com.kanaani.ibo.KeyHook.handler = null }
    }

    val title = when (s.kind) { "live" -> Str.orderLive; "movie" -> Str.orderMovies; else -> Str.orderSeries }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header(title)
            Label(Str.reorderHint, 12.sp, C.text2, lines = 2)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val list = order
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        list == null -> Loading()
                        list.isEmpty() -> Message(Str.empty)
                        else -> LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(C.panel).padding(8.dp),
                        ) {
                            itemsIndexed(list, key = { _, id -> id }) { i, id ->
                                val isSel = id in selected
                                val renamed = Store.categoryNames[s.kind]?.containsKey(id) == true
                                val isHidden = id in hidden
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Tile(
                                        { selected = if (isSel) selected - id else selected + id },
                                        (if (i == 0) Modifier.focusRequester(first) else Modifier).weight(1f).height(44.dp)
                                            .onFocusChanged { if (it.isFocused) focusedId = id },
                                        RoundedCornerShape(10.dp),
                                        colors = if (isSel) listOf(C.cardHi) else listOf(C.card),
                                        focusColors = C.live,
                                        focusScale = 1.02f,
                                    ) {
                                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // Case de sélection
                                            Box(
                                                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) C.accent else Color.Transparent)
                                                    .border(2.dp, if (isSel) C.accent else C.text3, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) { if (isSel) Label("✓", 13.sp, Color.White, FontWeight.Bold) }
                                            Spacer(Modifier.width(10.dp))
                                            Label("${i + 1}", 13.sp, C.text3, modifier = Modifier.width(34.dp))
                                            Column(Modifier.weight(1f)) {
                                                Label(nameOf(id), 14.sp, if (isHidden) C.text3 else C.text, if (isSel) FontWeight.Bold else FontWeight.Medium)
                                                if (renamed) Label("${Str.originalName}: ${originals[id] ?: ""}", 10.sp, C.text3)
                                            }
                                            if (isHidden) Label("🚫 " + Str.hiddenTag, 11.sp, C.error, FontWeight.Bold)
                                        }
                                    }
                                    ArrowButton(if (isHidden) "🚫" else "👁", enabled = true) { toggleHidden(setOf(id)) }
                                    ArrowButton("▲", enabled = i > 0) { shift(setOf(id), -1) }
                                    ArrowButton("▼", enabled = i < list.lastIndex) { shift(setOf(id), +1) }
                                }
                            }
                        }
                    }
                }
                // Panneau d'actions à côté de la liste : une seule pression ▶ depuis n'importe quelle catégorie.
                Column(
                    Modifier.width(200.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(C.panel).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Label(if (selected.isEmpty()) Str.focusedCategory else Str.selected(selected.size), 12.sp, Color(0xFF8FB3FF), FontWeight.Bold)
                    SideButton("▲ " + Str.moveUp, Color(0xFFE53935)) { targets().takeIf { it.isNotEmpty() }?.let { shift(it, -1) } }
                    SideButton("▼ " + Str.moveDown, Color(0xFF1E88E5)) { targets().takeIf { it.isNotEmpty() }?.let { shift(it, +1) } }
                    SideButton("⤒ " + Str.toTop, null) { targets().takeIf { it.isNotEmpty() }?.let { toEdge(it, top = true) } }
                    SideButton("⤓ " + Str.toBottom, null) { targets().takeIf { it.isNotEmpty() }?.let { toEdge(it, top = false) } }
                    SideButton("✎ " + Str.rename, Color(0xFF43A047)) { targets().takeIf { it.size == 1 }?.let { renaming = it.first() } }
                    val t = targets()
                    SideButton(if (t.isNotEmpty() && t.all { it in hidden }) "👁 " + Str.show else "🚫 " + Str.hide, Color(0xFFFDD835)) { toggleHidden(targets()) }
                    if (selected.isNotEmpty()) SideButton("✕ " + Str.clearSelection, null) { selected = emptySet() }
                    if (hidden.isNotEmpty()) SideButton("👁 " + Str.showAll + " (${hidden.size})", null) { Store.setHidden(s.kind, hidden, hide = false) }
                    Spacer(Modifier.weight(1f))
                    SideButton(Str.orderReset, null) {
                        Store.setCategoryOrder(s.kind, emptyList())
                        order = server?.map { it.id }
                    }
                }
            }
        }
        renaming?.let { id ->
            RenameDialog(
                current = nameOf(id),
                original = originals[id] ?: id,
                onSave = { Store.renameCategory(s.kind, id, it); renaming = null },
                onRestore = { Store.renameCategory(s.kind, id, ""); renaming = null },
                onDismiss = { renaming = null },
            )
        }
    }
}

/** Bouton du panneau ; [dot] : pastille de la touche de couleur correspondante sur la télécommande. */
@Composable
private fun SideButton(label: String, dot: Color?, onClick: () -> Unit) {
    Tile(onClick, Modifier.fillMaxWidth().height(33.dp), RoundedCornerShape(10.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.03f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(label, 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (dot != null) Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(dot))
        }
    }
}

@Composable
private fun BarButton(label: String, onClick: () -> Unit) {
    Tile(onClick, Modifier.height(36.dp), RoundedCornerShape(10.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.04f) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Label(label, 13.sp, weight = FontWeight.SemiBold)
        }
    }
}

/** Petite flèche tactile (pas de focus télécommande : on passe par la sélection et la barre). */
@Composable
private fun ArrowButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (enabled) C.card else Color.Transparent)
            .focusProperties { canFocus = false }
            .then(if (enabled) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Label(label, 16.sp, if (enabled) C.text else C.text3, FontWeight.Bold) }
}

/** Saisie du nouveau nom ; au-dessus du clavier sur téléphone. */
@Composable
private fun RenameDialog(current: String, original: String, onSave: (String) -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    var text by remember { mutableStateOf(current) }
    val field = remember { FocusRequester() }
    InitialFocus(field)
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).imePadding()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        contentAlignment = if (keyboard) Alignment.TopCenter else Alignment.Center,
    ) {
        Column(
            Modifier.padding(top = if (keyboard) 8.dp else 0.dp).width(460.dp).clip(RoundedCornerShape(20.dp)).background(C.panel)
                .border(1.dp, C.line, RoundedCornerShape(20.dp))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(Str.rename, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Label("${Str.originalName}: $original", 12.sp, C.text2)
            Field(text, { text = it }, original, Modifier.fillMaxWidth(), focusRequester = field, imeAction = ImeAction.Done, onDone = { onSave(text) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile({ onSave(text) }, Modifier.weight(1f).height(42.dp), RoundedCornerShape(10.dp), colors = C.live, focusScale = 1.03f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.save, 14.sp, weight = FontWeight.Bold) }
                }
                Tile(onRestore, Modifier.weight(1.4f).height(42.dp), RoundedCornerShape(10.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.03f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.restoreName, 13.sp, weight = FontWeight.SemiBold) }
                }
                Tile(onDismiss, Modifier.weight(1f).height(42.dp), RoundedCornerShape(10.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.03f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(Str.cancel, 14.sp, weight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
