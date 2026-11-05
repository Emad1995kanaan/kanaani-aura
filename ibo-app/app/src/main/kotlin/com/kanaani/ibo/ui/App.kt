package com.kanaani.ibo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.player.Playback
import kotlinx.coroutines.launch

/** Racine : connexion tant qu'aucun abonnement n'est enregistré, puis la pile d'écrans. */
@Composable
fun App(onExit: () -> Unit) {
    // Mise à jour : vérifiée à chaque ouverture ; la fenêtre passe au-dessus de tous les écrans.
    LaunchedEffect(Unit) { com.kanaani.ibo.data.Updater.check() }
    // Messages instantanés (ntfy) : abonnement tant que l'application est ouverte.
    LaunchedEffect(Unit) { com.kanaani.ibo.data.Messages.run() }
    LaunchedEffect(Unit) { com.kanaani.ibo.data.Translations.refresh() }
    // Panneau de contrôle : à l'ouverture puis toutes les 10 minutes.
    LaunchedEffect(Unit) { while (true) { com.kanaani.ibo.data.Panel.refresh(); kotlinx.coroutines.delay(10 * 60_000L) } }
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
        Screens(onExit)
        UpdateDialog()
        NoticeDialog()
        TrialDialog()
    }
}

@Composable
private fun Screens(onExit: () -> Unit) {
    val session = Store.session
    if (session == null) {
        LoginScreen()
        return
    }
    // Compte d'essai : renouvelé toutes les 90 s (un seul appareil, ligne pouvant changer).
    LaunchedEffect(Store.trialLogin() != null) { if (Store.trialLogin() != null) com.kanaani.ibo.data.Trial.keepAlive() }
    val nav = remember(session) { Navigator() }
    // Lecteur : si un flux échoue sur ce serveur, il essaie le même flux sur l'autre (principal / secours).
    Playback.alternatesFor = { u -> session.alternates(u) }
    val top = nav.top
    BackHandler { if (!nav.back()) onExit() }
    // Quitter le Direct ou un lecteur VOD vers un autre écran : la lecture s'arrête (sauf Direct ↔ plein écran).
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(top) {
        keyboard?.hide() // le clavier ne reste jamais ouvert d'un écran à l'autre
        if (top !is Screen.Live && top !is Screen.Play) Playback.stop()
    }
    when (top) {
        Screen.Home -> HomeScreen(nav, session)
        is Screen.Live -> LiveScreen(nav, session, top)
        is Screen.Catalog -> CatalogScreen(nav, session, top)
        is Screen.MovieDetail -> MovieDetailScreen(nav, session, top)
        is Screen.ShowDetail -> ShowDetailScreen(nav, session, top)
        // Direct plein écran : dessiné par LiveVideoHost (même surface vidéo que l'aperçu).
        is Screen.Play -> if (top.live) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
            else PlayerScreen(nav, session, top)
        Screen.Favorites -> FavoritesScreen(nav, session)
        is Screen.Reorder -> ReorderScreen(session, top)
        Screen.Account -> AccountScreen(session)
        Screen.Downloads -> DownloadsScreen(nav)
        Screen.Settings -> SettingsScreen(nav, session)
    }
    LiveVideoHost(nav, session, top)
    MessageTicker(top)
}

/** Essai terminé / plein / autre appareil : fenêtre bloquante (réessayer, WhatsApp, se déconnecter). */
@Composable
private fun TrialDialog() {
    val b = com.kanaani.ibo.data.Trial.block ?: return
    if (Store.session == null) return
    LaunchedEffect(b) { Playback.stop() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val opts = buildList<Pair<String, () -> Unit>> {
        if (b.error == "full" || b.error == "device") add(Str.retry to {
            scope.launch {
                val (u, p) = Store.trialLogin() ?: return@launch
                val r = com.kanaani.ibo.data.Trial.login(u, p)
                if (r.ok) { com.kanaani.ibo.data.Trial.block = null; Store.trialSwitch(r) } else if (r.error != "net") com.kanaani.ibo.data.Trial.block = r
            }
        })
        if (b.wa.isNotBlank()) add(Str.whatsapp to {
            runCatching {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/" + b.wa.filter { it.isDigit() }))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        add(Str.logout to { Store.logout() })
    }
    ChoiceDialog(trialMessage(b), opts) {}
}
