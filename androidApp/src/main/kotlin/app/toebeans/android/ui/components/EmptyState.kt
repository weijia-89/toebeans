package app.toebeans.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toebeans.android.ui.theme.ToebeansTheme

/**
 * Reusable empty-state placeholder used by Home, Pets, and Schedule list screens. Single
 * primary call-to-action only — empty states with two equally-prominent buttons cause
 * decision paralysis. The secondary path (if any) is a TextButton in the caller, not here.
 *
 * **Illustration:** optional [emoji] renders inside a 96 dp sage-tinted circle above the
 * title — same visual vocabulary as [PetAvatar] so the empty state feels of-a-piece with
 * the rest of the app instead of looking like a different design language. The circle is
 * marked decorative for screen readers (the title and body already carry the meaning).
 *
 * Why an emoji not a vector: emojis ship with the platform, render in full color, and
 * carry warmth (a 🐾 reads as "pets" instantly across cultures). Pulling in custom
 * vectors for every empty state would mean either material-icons-extended (vibe-dangerous
 * dep add) or a growing library of hand-authored ImageVectors — yak-shavey when the
 * Unicode glyph does the job better.
 */
@Composable
public fun EmptyState(
    title: String,
    body: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    emoji: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (emoji != null) {
            // Tinted circle with the emoji centered inside it — same construction as
            // PetAvatar but bigger (96 dp vs 72 dp hero / 48 dp list / 40 dp chip). The
            // tertiaryContainer (sage) gives a warm "all clear" cue rather than the
            // alarm-red an error/warning palette would imply.
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape,
                        )
                        // Decorative — the title and body carry the actual meaning. If
                        // we left this in the a11y tree, TalkBack would announce things
                        // like "paw prints" or "pill" before the real heading.
                        .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 48.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPrimaryAction) {
                Text(primaryActionLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    ToebeansTheme(dynamic = false) {
        EmptyState(
            emoji = "🐾",
            title = "No pets yet",
            body = "Add Rufus, Luna, or whoever you share toe beans with to get started.",
            primaryActionLabel = "Add pet",
            onPrimaryAction = {},
        )
    }
}
