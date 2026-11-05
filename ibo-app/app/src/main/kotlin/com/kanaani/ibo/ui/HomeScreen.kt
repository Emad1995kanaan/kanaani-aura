package com.kanaani.ibo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanaani.ibo.Brand
import com.kanaani.ibo.BuildConfig
import com.kanaani.ibo.data.Account
import com.kanaani.ibo.data.Xtream
import kotlinx.coroutines.delay

/** Infos d'abonnement, lues une fois par session (accueil, compte). */
object AccountCache {
    var value by mutableStateOf<Account?>(null)
    suspend fun load(x: Xtream) { if (value == null) value = x.account() }
}

fun expiryLine(a: Account?): String {
    a ?: return ""
    // Compte d'essai : c'est la fin de l'essai qui compte, pas celle de la ligne du serveur.
    val trialEnd = if (com.kanaani.ibo.data.Store.trialLogin() != null) com.kanaani.ibo.data.Trial.expires.takeIf { it > 0 } else null
    val exp = trialEnd ?: a.expiresAt ?: return "${Str.expires}: ${Str.unlimited}"
    val days = ((exp - System.currentTimeMillis()).coerceAtLeast(0) + 86_399_999L) / 86_400_000L
    return "${Str.expires} ${date(exp)} · $days ${Str.daysLeft}"
}

@Composable
fun HomeScreen(nav: Navigator, x: Xtream) {
    LaunchedEffect(x) { AccountCache.load(x) }
    // « Tous » films et séries préparés en arrière-plan pendant qu'on est sur l'accueil.
    LaunchedEffect(x) { x.prefetch() }
    LaunchedEffect(Unit) { com.kanaani.ibo.data.Rates.refresh() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000L - now % 60_000L) } }
    LaunchedEffect(Unit) { com.kanaani.ibo.data.Ads.refresh() }
    // Kanaani Aura : un seul thème (menu à gauche, rangées d'affiches).
    AuraHome(nav, x, now); return
    val first = remember { FocusRequester() }
    InitialFocus(first)

    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Logo(100.dp)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(Brand.name, color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 38.sp, maxLines = 1)
                    Label(Str.welcome, 20.sp, C.text2, FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(clock(now), color = C.text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.sp, maxLines = 1)
                    Label(date(now, "EEEE d MMMM"), 12.sp, C.text2)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BigTile(Str.live, Icons.Live, C.live, Modifier.weight(1.3f).focusRequester(first)) { nav.go(Screen.Live()) }
                BigTile(Str.movies, Icons.Movies, C.movies, Modifier.weight(1f)) { nav.go(Screen.Catalog(series = false)) }
                BigTile(Str.series, Icons.Series, C.series, Modifier.weight(1f)) { nav.go(Screen.Catalog(series = true)) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SmallTile(Str.favorites, Icons.Heart, Modifier.weight(1f)) { nav.go(Screen.Favorites) }
                SmallTile(Str.downloads, Icons.Download, Modifier.weight(1f)) { nav.go(Screen.Downloads) }
                SmallTile(Str.account, Icons.Account, Modifier.weight(1f)) { nav.go(Screen.Account) }
                SmallTile(Str.settings, Icons.Settings, Modifier.weight(1f)) { nav.go(Screen.Settings) }
            }
            Spacer(Modifier.weight(1f))
            // Cours des devises, au centre en bas.
            RatesBar(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(expiryLine(AccountCache.value), 12.sp, C.text2, modifier = Modifier.weight(1f))
                Label("v${BuildConfig.VERSION_NAME}", 11.sp, C.text3)
            }
        }
    }
}

/** « $ دولار 3.70 ₪ · دينار 5.22 ₪ · € يورو 4.01 ₪ » (shekels pour une unité). */
@Composable
private fun RatesBar(modifier: Modifier) {
    val r = com.kanaani.ibo.data.Rates.value ?: return
    fun f(v: Double) = String.format(java.util.Locale.ROOT, "%.2f", v)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x990E1736)).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            RateItem("$", Str.dollar, f(r.usd))
            RateItem("JD", Str.dinar, f(r.jod))
            RateItem("€", Str.euro, f(r.eur))
        }
        if (r.viaErApi) Label("Rates By Exchange Rate API", 9.sp, C.text3)
    }
}

@Composable
private fun RateItem(symbol: String, name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(symbol, 14.sp, Color(0xFF8FB3FF), FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Label(name, 14.sp, C.text2, FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Label("$value ₪", 16.sp, C.text, FontWeight.Bold)
    }
}

@Composable
private fun BigTile(label: String, icon: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxSize(), RoundedCornerShape(20.dp), colors = colors) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, 76.dp, Color.White, stroke = 1.4f)
            Text(label, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SmallTile(label: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Tile(onClick, modifier.fillMaxSize(), RoundedCornerShape(14.dp), colors = C.small, focusColors = C.live) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, 28.dp, Color.White, stroke = 1.8f)
            Spacer(Modifier.width(12.dp))
            Label(label, 16.sp, weight = FontWeight.SemiBold)
        }
    }
}
