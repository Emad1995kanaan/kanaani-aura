package com.kanaani.ibo

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kanaani.ibo.data.Store
import com.kanaani.ibo.player.Playback
import com.kanaani.ibo.ui.App
import com.kanaani.ibo.ui.Str

class IboApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        com.kanaani.ibo.data.TmdbArt.init(this)
    }
}

/**
 * Touches de la télécommande interceptées AVANT les vues (le lecteur Media3 les consommerait sinon) :
 * l'écran de lecture y branche l'avance / le retour de 10 s.
 */
object KeyHook {
    var handler: ((android.view.KeyEvent) -> Boolean)? = null
}

class MainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (KeyHook.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            // Interface dessinée pour un écran de 540 dp de haut : même rendu sur TV, box et téléphone en paysage.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val density = Density(density = hPx / 540f, fontScale = 1f)
                val dir = if (Str.lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalDensity provides density, LocalLayoutDirection provides dir) {
                    App(onExit = { finish() })
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // En arrière-plan : on coupe le son et le réseau ; la lecture reprend quand l'utilisateur relance.
        if (!isChangingConfigurations) Playback.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Playback.release()
    }
}
