package com.kanaani.ibo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanaani.ibo.R
import kotlinx.coroutines.delay

/** Palette « IBO » : bleu nuit, tuiles en dégradé. */
object C {
    // Kanaani Aura : noir de luxe, argent, une seule touche de bleu (celui du logo).
    val bgTop = Color(0xFF000000)
    val bgBottom = Color(0xFF0A0A0C)
    val glow = Color(0x14C0C8D8)
    val panel = Color(0xFF0E0E10)
    val card = Color(0xFF17171A)
    val cardHi = Color(0xFF232328)
    val line = Color(0xFF2A2A30)
    val text = Color(0xFFFFFFFF)
    val text2 = Color(0xFFB4B4BC)
    val text3 = Color(0xFF6E6E78)
    val accent = Color(0xFF2F6BFF)
    val accentDark = Color(0xFF1846C8)
    val focus = Color(0xFFE8ECF5)
    val error = Color(0xFFFF7A7A)
    val live = listOf(Color(0xFF2F6BFF), Color(0xFF1846C8))
    val movies = listOf(Color(0xFF2A2A30), Color(0xFF17171A))
    val series = listOf(Color(0xFF2A2A30), Color(0xFF17171A))
    val small = listOf(Color(0xFF17171A), Color(0xFF111113))
    val danger = listOf(Color(0xFF7A1C2B), Color(0xFF4A0F1A))
}

private fun variable(id: Int, w: Int) = Font(id, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

// Aura : police système (pas celle de Kanaani).
val Sora: FontFamily = FontFamily.SansSerif
val Manrope = FontFamily(variable(R.font.manrope, 400), variable(R.font.manrope, 500), variable(R.font.manrope, 600), variable(R.font.manrope, 700))

/** Fond commun : dégradé bleu nuit + halo. */
@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(C.bgTop, C.bgBottom))),
        content = content,
    )
}

/**
 * Élément cliquable au toucher comme à la télécommande : au focus il grossit un peu et prend un contour blanc.
 * [colors] : dégradé de fond ; [focusColors] : dégradé quand il a le focus (sinon le même).
 */
@Composable
fun Tile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: List<Color> = C.small,
    focusColors: List<Color>? = null,
    focusScale: Float = 1.05f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = if (focused && focusColors != null) focusColors else colors
    Box(
        modifier
            .scale(if (focused) focusScale else 1f)
            .clip(shape)
            .background(if (bg.size > 1) Brush.linearGradient(bg) else SolidColor(bg.first()))
            .then(if (focused) Modifier.border(2.dp, C.focus, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content(focused) }
}

/** Logo de la marque (pastille carrée). */
@Composable
fun Logo(size: Dp) {
    Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(size))
}

/** En-tête d'écran : logo, titre, heure. */
@Composable
fun Header(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000L - now % 60_000L) } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Logo(40.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
        Spacer(Modifier.width(16.dp))
        Text(clock(now), color = C.text, fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, maxLines = 1)
    }
}

/** Heure sur 12 h (ex. 5:04 م / 5:04 PM). */
fun clock(now: Long): String {
    val pm = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.AM_PM) == java.util.Calendar.PM
    val suffix = when (Str.lang) { Lang.AR -> if (pm) "م" else "ص"; else -> if (pm) "PM" else "AM" }
    return java.text.SimpleDateFormat("h:mm", java.util.Locale.ROOT).format(java.util.Date(now)) + " " + suffix
}

fun date(ms: Long, pattern: String = "d MMM yyyy"): String =
    java.text.SimpleDateFormat(pattern, java.util.Locale.forLanguageTag(Str.lang.code + "-u-nu-latn")).format(java.util.Date(ms))

@Composable
fun Label(text: String, size: TextUnit = 14.sp, color: Color = C.text, weight: FontWeight = FontWeight.Medium, lines: Int = 1, modifier: Modifier = Modifier) {
    Text(text, color = color, fontFamily = Manrope, fontWeight = weight, fontSize = size, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = C.accent, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
    }
}

/** Message centré, cliquable (ex. « Réessayer »). */
@Composable
fun Message(text: String, onClick: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (onClick != null) Tile(onClick, colors = listOf(C.card)) {
            Label(text, 15.sp, C.text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        } else Label(text, 15.sp, C.text2)
    }
}

/** Champ texte sombre, utilisable à la télécommande (OK ouvre le clavier). */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    /** Masquer le texte (●●●) ; false = mot de passe visible (clavier « mot de passe » quand même, sans suggestions). */
    masked: Boolean = password,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Next,
    numeric: Boolean = false,
    onDone: () -> Unit = {},
    /** Champ de recherche : clavier ouvert tout de suite (l'utilisateur vient d'appuyer sur « Rechercher »). */
    startEditing: Boolean = false,
    onBlur: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Clavier JAMAIS ouvert tout seul : le champ reste en lecture seule (pas de session de saisie, donc certains
    // claviers de TV ne peuvent pas s'ouvrir) jusqu'à OK, un toucher, ou l'ouverture de la recherche.
    var editing by remember { mutableStateOf(startEditing) }
    var wasFocused by remember { mutableStateOf(false) }
    val touch = androidx.compose.ui.platform.LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Touch
    LaunchedEffect(focused) {
        if (focused) { wasFocused = true; if (touch) editing = true }
        else if (wasFocused) { wasFocused = false; editing = false; keyboard?.hide(); onBlur() }
    }
    LaunchedEffect(editing, focused) { if (editing && focused) { delay(60); keyboard?.show() } }
    BasicTextField(
        readOnly = !editing,
        value = value,
        onValueChange = onChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = TextStyle(color = C.text, fontFamily = Manrope, fontSize = 16.sp),
        cursorBrush = SolidColor(C.accent),
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        // Télécommande : le clavier ne s'ouvre PAS en passant sur le champ (il bloquerait la navigation), seulement sur OK.
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                numeric && password -> KeyboardType.NumberPassword
                numeric -> KeyboardType.Number
                password -> KeyboardType.Password
                else -> KeyboardType.Text
            },
            imeAction = imeAction,
            autoCorrectEnabled = false,
            showKeyboardOnFocus = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }, onSearch = { onDone() }),
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    if (editing) keyboard?.show() else editing = true
                    true
                } else false
            }
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(C.card)
            .border(if (focused) 2.dp else 1.dp, if (focused) C.focus else C.line, RoundedCornerShape(12.dp)),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Label(placeholder, 15.sp, C.text3)
                inner()
            }
        },
    )
}

/**
 * Recherche « à la demande » : un simple bouton 🔍 tant qu'on ne l'a pas pressé (aucun clavier en arrivant sur
 * l'écran) ; pressé, il devient un champ avec le clavier ouvert, et ✕ ferme la recherche et vide le filtre.
 */
@Composable
fun SearchBox(open: Boolean, onOpen: (Boolean) -> Unit, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = Str.searchButton) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    if (!open) {
        Tile({ onOpen(true) }, modifier.height(40.dp), RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live, focusScale = 1.03f) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Search, 18.dp, Color.White)
                Spacer(Modifier.width(10.dp))
                Label(label, 14.sp, C.text2, FontWeight.SemiBold, lines = 1)
            }
        }
        return
    }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) { delay(60); if (runCatching { field.requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // En quittant le champ : clavier fermé ; recherche vide = bouton « Rechercher » de nouveau.
        Field(value, onChange, Str.search, Modifier.weight(1f), focusRequester = field, imeAction = ImeAction.Search, onDone = { keyboard?.hide() },
            startEditing = true, onBlur = { if (value.isBlank()) onOpen(false) })
        Spacer(Modifier.width(6.dp))
        Tile({ keyboard?.hide(); onChange(""); onOpen(false) }, Modifier.size(40.dp), RoundedCornerShape(12.dp), colors = listOf(C.card), focusColors = C.live) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("✕", 15.sp, weight = FontWeight.Bold) }
        }
    }
}

/** Boîte de dialogue simple : titre + liste de choix. */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(20.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            options.forEachIndexed { i, (label, action) ->
                Tile(action, Modifier.fillMaxWidth().height(48.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    colors = listOf(C.card), focusColors = C.live, focusScale = 1.02f) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { Label(label, 16.sp, weight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** Demande le focus initial (télécommande) une fois l'écran composé. */
@Composable
fun InitialFocus(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        repeat(10) {
            delay(60)
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
}
