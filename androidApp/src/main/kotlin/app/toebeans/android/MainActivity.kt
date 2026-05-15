package app.toebeans.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.toebeans.android.ui.ToebeansAppShell
import app.toebeans.android.ui.theme.ToebeansTheme

/**
 * Single-Activity host. All screens live inside the [ToebeansAppShell] NavHost.
 *
 * Per AGENTS.md, this file lives on a vibe-safe UI surface (no scheduler logic). The
 * notifications package and AndroidManifest changes are the vibe-dangerous surfaces in
 * this module; the UI here just renders state from Koin-injected ViewModels.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToebeansTheme {
                ToebeansAppShell()
            }
        }
    }
}
