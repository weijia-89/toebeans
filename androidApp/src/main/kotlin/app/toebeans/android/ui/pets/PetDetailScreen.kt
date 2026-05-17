package app.toebeans.android.ui.pets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import app.toebeans.android.ui.components.PetAvatar
import app.toebeans.android.ui.components.PetAvatarSizeHero
import app.toebeans.core.model.Pet
import app.toebeans.core.model.PetAgeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.koin.androidx.compose.koinViewModel

/**
 * Pet detail. Shows the pet's identity card and a LazyColumn of medications. Tapping a
 * medication navigates to its edit screen; the Add FAB opens a new-medication form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PetDetailScreen(
    petId: String,
    onBack: () -> Unit,
    onEditPet: () -> Unit,
    onAddMedication: () -> Unit,
    onMedicationClick: (medicationId: String) -> Unit,
    viewModel: PetDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(petId) { viewModel.load(petId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.pet?.name ?: "Pet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditPet, enabled = state.pet != null) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit pet")
                    }
                },
            )
        },
        // FAB only when the list is non-empty. Empty state has its own primary CTA, so
        // we avoid double-CTA confusion (and an awkward FAB hovering over an EmptyState
        // illustration). When the FAB IS shown, Scaffold handles navigation-bar inset
        // padding automatically via its default contentWindowInsets.
        floatingActionButton = {
            if (state.pet != null && state.medications.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddMedication,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add medication") },
                )
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            val pet = state.pet
            if (pet == null) {
                if (!state.loading) {
                    EmptyState(title = "Pet not found", body = "It may have been deleted.")
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PetIdentityCard(pet, Modifier.padding(16.dp))
                    Text(
                        text = "Medications",
                        style = MaterialTheme.typography.titleMedium,
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp)
                                .semantics { heading() },
                    )
                    if (state.medications.isEmpty()) {
                        // Single primary CTA. FAB is suppressed (see above).
                        EmptyState(
                            emoji = "💊",
                            title = "No medications yet",
                            body = "Add a medication to start scheduling doses.",
                            primaryActionLabel = "Add medication",
                            onPrimaryAction = onAddMedication,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.medications, key = { it.medication.id }) { withStatus ->
                                MedicationRow(
                                    withStatus = withStatus,
                                    onClick = { onMedicationClick(withStatus.medication.id) },
                                    onLogDose = {
                                        viewModel.logDose(
                                            medicationId = withStatus.medication.id,
                                            activeScheduleId = withStatus.activeScheduleId,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header card showing the pet's identity at a glance. Layout: large species emoji in a
 * circular tinted badge on the left, name + facts stack on the right.
 *
 * Two-line fact layout (intentional):
 *   1. Species · Weight   — slow-changing factual data
 *   2. Age string         — relational data ("4 years old") computed from birthdate
 *
 * The split lets the eye land on the age first when scanning. Weight typically lives in
 * line 1 because vet calls often start with "what's the weight" — keeping it close to
 * the species reduces hunt-time.
 *
 * If birthdate is null we omit the age line entirely rather than rendering an empty row.
 */
@Composable
private fun PetIdentityCard(
    pet: Pet,
    modifier: Modifier = Modifier,
) {
    // Compute "today" once per recomposition driven by the pet object. Using
    // remember(pet.id) means the age string only re-derives when we swap pets, not on
    // every theme/config recomp. The day-rollover at midnight isn't an issue here —
    // the user will re-open the screen long before a 1-day age boundary matters.
    val today = remember(pet.id) { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val ageString = pet.birthdate?.let { PetAgeFormatter.format(it, today) }
    val speciesLabel =
        pet.species.name
            .lowercase()
            .replaceFirstChar(Char::titlecase)
    val weightLabel = pet.weightKg?.let { "%.1f kg".format(it) }
    val factsLine1 = listOfNotNull(speciesLabel, weightLabel).joinToString(" · ")

    // Build the merged accessibility label outside the Composable structure. TalkBack
    // reads this as one prosodic unit instead of the 4 separate Text nodes inside the
    // card (name + facts + age + notes). "kg" expanded to "kilograms" because
    // TalkBack's abbreviation dictionary doesn't always do it.
    val accessibleLabel =
        buildString {
            append(pet.name)
            append(", ")
            append(speciesLabel)
            weightLabel?.let {
                append(", ")
                append(it.replace(" kg", " kilograms"))
            }
            ageString?.let {
                append(", ")
                append(it)
            }
            if (!pet.notes.isNullOrBlank()) {
                append(". Notes: ")
                append(pet.notes)
            }
        }
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleLabel
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PetAvatar(species = pet.species, size = PetAvatarSizeHero)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = pet.name, style = MaterialTheme.typography.headlineSmall)
                if (factsLine1.isNotEmpty()) {
                    Text(
                        text = factsLine1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (ageString != null) {
                    Text(
                        text = ageString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (!pet.notes.isNullOrBlank()) {
                    Text(
                        text = pet.notes!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationRow(
    withStatus: MedicationWithStatus,
    onClick: () -> Unit,
    onLogDose: () -> Unit,
) {
    val med = withStatus.medication
    val now = remember(withStatus.lastDose?.id) { Clock.System.now() }
    val lastDoseLabel =
        withStatus.lastDose?.let { lastDose ->
            "Last dose: " + formatTimeAgo(lastDose.scheduledAt, now)
        }

    // The row itself is clickable (tap → schedule editor); the inline "Log dose" Button
    // captures its own click separately. Compose dispatches the deepest matching
    // clickable, so tapping the button does NOT also trigger the row click.
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = med.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = med.doseAmount,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastDoseLabel != null) {
                // "Last dose: 2h ago" subtitle. Only shown when a dose has been
                // logged — for never-given meds we omit the line rather than
                // showing "Last dose: never" which would feel clinical.
                Text(
                    text = lastDoseLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Log Dose button gated on having an active schedule. Real owners need
            // to set up at least one schedule (the "Methimazole, twice daily")
            // before they can quick-log against it. The gate communicates that:
            // if no schedule, the button is a TextButton hint instead of a Button.
            if (withStatus.activeScheduleId != null) {
                FilledTonalButton(
                    onClick = onLogDose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Log dose now")
                }
            } else {
                Text(
                    text = "Add a schedule first to log doses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Human-readable relative-time string for "X ago" indicators on the dose-log surface.
 * Examples: "just now", "8m ago", "2h ago", "yesterday", "3d ago".
 *
 * Boundaries are chosen so each tier reads naturally: minutes for first hour, hours for
 * the rest of the day, "yesterday" for the 24-48h band (more humane than "1d ago"),
 * day count for the 2-7d band, and a full date for anything older.
 *
 * Pure function — no platform clock access (the "now" is passed in by caller). Safe to
 * unit-test without mocking Clock. Lives on the screen file for now because it has only
 * one caller; promote to a shared util if a second use case appears.
 */
internal fun formatTimeAgo(
    instant: Instant,
    now: Instant,
): String {
    val deltaMs = (now - instant).inWholeMilliseconds
    if (deltaMs < 60_000) return "just now"
    val minutes = deltaMs / 60_000
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    if (days == 1L) return "yesterday"
    if (days < 7) return "${days}d ago"
    // For anything older, fall back to the calendar date the dose was given. We don't
    // include time-of-day for >1w because the date alone is enough context — the owner
    // is looking for "did I give this last Wednesday or the Wednesday before?", not
    // "did I give this at 9:15 vs 9:30".
    val date =
        instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "on ${date.month.name.lowercase().replaceFirstChar(Char::titlecase)} ${date.dayOfMonth}"
}
