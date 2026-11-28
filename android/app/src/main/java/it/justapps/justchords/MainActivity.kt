package it.justapps.justchords

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import it.justapps.justchords.ui.JustChordsRoot
import it.justapps.justchords.ui.JustTheme
import it.justapps.justchords.ui.shareRoute

/** Just Chords, native: Material 3 with the JustApps look, full screen. */
class MainActivity : ComponentActivity() {
    private var pending by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(0, 0), SystemBarStyle.auto(0, 0))
        super.onCreate(savedInstanceState)
        immersive()
        if (savedInstanceState == null) pending = routeFor(intent)
        val app = application as ChordsApp
        setContent {
            val st by app.settings.state.collectAsState()
            JustTheme(st.theme) {
                JustChordsRoot(pending) { pending = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFor(intent)?.let { pending = it }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    /** Full screen: status and navigation bars hidden, back with a swipe from the edge. */
    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun routeFor(i: Intent?): String? {
        if (i?.action != Intent.ACTION_SEND) return null
        val type = i.type ?: return null
        if (type.startsWith("text/")) return shareRoute(i.getStringExtra(Intent.EXTRA_SUBJECT), i.getStringExtra(Intent.EXTRA_TEXT))
        // A video or song file: analyse it.
        @Suppress("DEPRECATION")
        val uri = i.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: return null
        return if (type.startsWith("video/") || type.startsWith("audio/")) "analyze?file=${it.justapps.justchords.ui.enc(uri.toString())}" else null
    }
}
