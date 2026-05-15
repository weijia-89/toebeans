package app.toebeans.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * v0.1 entry screen. Placeholder until the Pet list / Add medication / Reminder list screens
 * are wired up in slice 1's UI PRs.
 *
 * Per AGENTS.md, this file lives on a vibe-safe surface (UI screens that do not touch the
 * scheduler), so CI gates are sufficient review.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToebeansTheme {
                Scaffold { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "toebeans v0.1 — scaffold OK\nNo features yet.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToebeansTheme(content: @Composable () -> Unit) {
    // v0.1: defer dynamic color / dark theme details until the design pass in slice 1 UI work.
    MaterialTheme(content = content)
}
