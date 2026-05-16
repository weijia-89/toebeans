package app.toebeans.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.toebeans.core.model.Species

/**
 * Circular tinted badge with a species emoji centered inside it. Shared across the
 * pet-detail header (72dp), pets-list rows (48dp), and the today-screen pet preview
 * row (56dp) so the same visual vocabulary repeats across the app.
 *
 * Sizing strategy:
 *  - [size] is the circle diameter in dp.
 *  - [emojiFontSize] defaults to ~56% of the circle diameter, which keeps the emoji
 *    visually centered with breathing room on standard system emoji fonts. Override
 *    only if a parent layout needs an unusually tight or loose fit.
 *
 * Color strategy:
 *  - Container defaults to [MaterialTheme.colorScheme.tertiaryContainer] (sage in our
 *    terracotta-warm palette) which provides a complementary cool note against the
 *    cream surface. Override only if the parent surface itself is already tertiary-
 *    tinted (e.g. inside a tertiary-tinted Card).
 *
 * Accessibility:
 *  - The emoji is decorative; the pet's name should carry the screen-reader content
 *    description elsewhere on the row.
 *  - Emoji font size uses sp (not dp) so users with a larger font-size setting see a
 *    proportionally larger glyph.
 */
@Composable
public fun PetAvatar(
    species: Species,
    size: Dp,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    emojiFontSize: TextUnit = (size.value * 0.56f).sp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(color = containerColor, shape = CircleShape)
                // Mark the entire avatar decorative for screen readers. Without this,
                // TalkBack announces "dog face" / "cat face" (the emoji's Unicode name)
                // which adds noise and confuses identity — the parent row already names
                // the pet, which is the actual content. clearAndSetSemantics with an
                // empty block drops this node entirely from the a11y tree.
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = speciesEmoji(species),
            fontSize = emojiFontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Species → emoji string. Kept in the UI layer (not on the Species enum) so the shared
 * commonMain module stays UI-agnostic when the iOS target lands and may want
 * Apple-styled emoji or custom drawables instead.
 *
 *  - U+1F436 DOG FACE: friendlier register than U+1F415 DOG (in-profile working dog).
 *  - U+1F431 CAT FACE: matches the dog-face stylistic register.
 *
 * If a future Species variant arrives (e.g. RABBIT), add a branch here. The enum is
 * exhaustive so the compiler will flag the omission.
 */
public fun speciesEmoji(species: Species): String =
    when (species) {
        Species.DOG -> "\uD83D\uDC36"
        Species.CAT -> "\uD83D\uDC31"
    }

// 8dp = the smallest avatar size we use (e.g. compact list rows). Exposed so callers
// can keep their padding/spacing in lockstep with avatar sizing.
public val PetAvatarSizeCompact: Dp = 40.dp
public val PetAvatarSizeList: Dp = 48.dp
public val PetAvatarSizeHero: Dp = 72.dp
