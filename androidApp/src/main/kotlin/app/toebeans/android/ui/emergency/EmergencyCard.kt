package app.toebeans.android.ui.emergency

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.toebeans.android.R

/**
 * Collapsible emergency-contacts card. Static curated public contact information,
 * the app makes no medical claim (ADR-0019 §3): it hands the user a phone number and
 * opens the dialer on tap via a user-initiated ACTION_DIAL (no permission, ADR-0003).
 *
 * Collapsed by default so the Today screen stays glanceable; one tap reveals only
 * the two verified poison-control hotline rows. The first-line explanation,
 * find-an-emergency-vet guidance, and no-medical-advice disclaimer live in the
 * Settings dialog via [EmergencyResourcesContent], not in this card: an owner in a
 * panic needs two numbers, not a paragraph. [onCall] receives the tel: [Uri], the
 * caller owns starting the intent, which keeps this composable context-free and
 * testable.
 */
@Composable
fun EmergencyCard(
    onCall: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            val headerA11y =
                stringResource(
                    if (expanded) {
                        R.string.emergency_collapse_a11y
                    } else {
                        R.string.emergency_expand_a11y
                    },
                )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = headerA11y
                            role = Role.Button
                        },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.emergency_card_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = null,
                )
            }
            if (expanded) {
                EmergencyCardHotlineContent(onCall = onCall)
            }
        }
    }
}

/**
 * Card-specific expanded body. Renders only the two tappable hotline rows with the
 * same padding and spacing as the full content, so the card stays glanceable in a
 * stressful moment. The explanatory text and disclaimer remain in the Settings
 * dialog via [EmergencyResourcesContent].
 */
@Composable
private fun EmergencyCardHotlineContent(onCall: (Uri) -> Unit) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmergencyResources.hotlines.forEach { hotline ->
            EmergencyHotlineRow(hotline = hotline, onCall = onCall)
        }
    }
}

/**
 * Full emergency content used by the Settings dialog: first-line guidance, the two
 * hotline rows, find-a-vet guidance, and the no-medical-advice disclaimer (ADR-0019
 * §3 framing). The Today card deliberately shows only [EmergencyCardHotlineContent].
 * Rows fire [onCall]; the host screen turns the tel: Uri into the dialer intent.
 */
@Composable
internal fun EmergencyResourcesContent(
    onCall: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.emergency_first_line),
            style = MaterialTheme.typography.bodyMedium,
        )
        EmergencyResources.hotlines.forEach { hotline ->
            EmergencyHotlineRow(hotline = hotline, onCall = onCall)
        }
        Text(
            text = stringResource(R.string.emergency_find_vet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.emergency_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmergencyHotlineRow(
    hotline: EmergencyHotline,
    onCall: (Uri) -> Unit,
) {
    val name = stringResource(hotline.nameRes)
    val phone = stringResource(hotline.phoneRes)
    val rowA11y = stringResource(R.string.emergency_row_call_a11y, name, phone)
    // Merge descendant semantics so TalkBack announces the row as one action
    // ("Call ASPCA Animal Poison Control: (888) 426-4435") instead of three reads.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCall(EmergencyResources.telUri(hotline.phoneDigits)) }
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = rowA11y
                    role = Role.Button
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(hotline.detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = phone,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
