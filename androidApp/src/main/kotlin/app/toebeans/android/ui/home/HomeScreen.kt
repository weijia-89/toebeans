package app.toebeans.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.toebeans.android.ui.components.EmptyState
import app.toebeans.android.ui.components.PetAvatar
import app.toebeans.android.ui.components.PetAvatarSizeCompact
import app.toebeans.android.ui.pets.formatTimeAgo
import app.toebeans.android.ui.theme.ToebeansTheme
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Species
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.koin.androidx.compose.koinViewModel

/**
 * Home / Today screen. Shows the user's tracked pets as tappable chips and today's
 * dose surfaces ("Logged today" + due-today cards). Dose rows persist via
 * [SqlDelightDoseEventRepository][app.toebeans.core.data.SqlDelightDoseEventRepository]
 * (AppModule DI); 72h materializer and boot rehydration remain deferred.
 *
 * Three states:
 *   - Loading: nothing (the parent suppresses recompositions while loading).
 *   - No pets:  one-CTA empty state inviting the user to add their first pet.
 *   - Has pets: Today header → "Your pets" tappable row → today's dose cards.
 *
 * The pet chips are the only tappable surface on this screen so far. They route to the
 * pet's detail (which is also reachable from the Pets tab) — Today is a fast-path home
 * for users who think "oh I need to check on Rufus" rather than "let me browse the
 * pets list." Same destination, two paths.
 */
@Composable
public fun HomeScreen(
    onAddPet: () -> Unit,
    onPetClick: (petId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreenContent(
        state = state,
        onAddPet = onAddPet,
        onPetClick = onPetClick,
        onMarkGiven = viewModel::markGiven,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onAddPet: () -> Unit,
    onPetClick: (petId: String) -> Unit,
    onMarkGiven: (scheduleId: String, medicationId: String, scheduledAt: Instant) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (state.pets.isEmpty() && !state.loading) {
        EmptyState(
            emoji = "🐾",
            title = "No pets yet",
            // The original "No doses scheduled" framing was confusing when the user
            // had no pets at all — it implied dose-scheduling was the missing step
            // when really the missing step was pet entry. Now the empty-state CTA
            // matches the actual blocking action.
            body = "Add Rufus, Luna, or whoever you share toe beans with to get started.",
            primaryActionLabel = "Add pet",
            onPrimaryAction = onAddPet,
            modifier = modifier.padding(contentPadding),
        )
        return
    }
    // Compute date string once per parent composition. The day-rollover-at-midnight
    // edge case is fine — the user will close and reopen the app long before the date
    // line goes stale enough to matter. Hand-formatted from kotlinx-datetime fields
    // rather than pulling in a heavier formatter dependency; English-only for v1
    // (i18n lives in a future milestone alongside Plurals).
    val today: LocalDate =
        remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val dateString = formatTodayHeader(today)

    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // heading() semantic lets TalkBack's heading-rotor jump between top-level
            // sections on this screen. Without it, screen-reader users have to swipe
            // through every interactive element to navigate.
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Pet quick-tap row. Each chip is a Card with avatar + name; tapping routes to
        // pet detail. LazyRow gives us horizontal scrolling for users with many pets
        // (the 4-pet household exists) without breaking the vertical scroll model of
        // the parent Column.
        Text(
            text = "Your pets",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // 4dp vertical contentPadding leaves room for the Card's elevation shadow
            // to render without clipping at the top/bottom of the LazyRow.
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(items = state.pets, key = { it.id }) { pet ->
                PetChip(
                    pet = pet,
                    medCount = state.medCountByPetId[pet.id] ?: 0,
                    onClick = { onPetClick(pet.id) },
                )
            }
        }

        Text(
            text = "Today's doses",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        DueTodayCard(dueDoses = state.dueDoses, onMarkGiven = onMarkGiven)

        Text(
            text = "Logged today",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        LoggedTodayCard(recentDoses = state.recentDoses)
    }
}

/**
 * Forward-looking "what's due today" worklist. The user's primary action surface on
 * the Home screen: glance, tap Log to mark a dose given, watch the row gray out. Per
 * the AGENTS.md vibe-dangerous policy, the button writes a real DoseEvent with the
 * slot's intended `scheduledAt` — that's what lets the row flip from pending to given
 * on the next flow tick.
 *
 * Empty state happens when no active schedule yields a dose for today. We don't try
 * to differentiate "no schedules" vs "all caught up" vs "no doses today" — for v0.1
 * those all collapse to the same friendly nudge.
 */
@Composable
private fun DueTodayCard(
    dueDoses: List<DueDoseUi>,
    onMarkGiven: (scheduleId: String, medicationId: String, scheduledAt: Instant) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (dueDoses.isEmpty()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "No doses scheduled for today",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        "Add a schedule for one of your pets to see today's doses here. " +
                            "You can still log an ad-hoc dose from a pet's detail screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Card
        }
        // `now` captured per-recomposition for the time label. Same staleness story as
        // LoggedTodayCard — a minute drift on a "12:00" label is fine.
        val tz = remember { TimeZone.currentSystemDefault() }
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            dueDoses.forEach { dose ->
                DueDoseRow(
                    dose = dose,
                    timeZone = tz,
                    onMarkGiven = { onMarkGiven(dose.scheduleId, dose.medicationId, dose.scheduledAt) },
                )
            }
        }
    }
}

@Composable
private fun DueDoseRow(
    dose: DueDoseUi,
    timeZone: TimeZone,
    onMarkGiven: () -> Unit,
) {
    val slotLabel = remember(dose.scheduledAt, timeZone) { formatLocalTime(dose.scheduledAt, timeZone) }
    val a11y =
        if (dose.isGiven) {
            "${dose.petName}, ${dose.medicationName}, ${dose.doseAmount}, $slotLabel, given"
        } else {
            "${dose.petName}, ${dose.medicationName}, ${dose.doseAmount}, $slotLabel, pending"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = a11y
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = "${dose.petName} · ${dose.medicationName}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${dose.doseAmount} · $slotLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (dose.isGiven) {
            // Given rows show a quiet text confirmation instead of a button. The
            // visible affordance change ("Log dose" → "Given ✓") is the primary signal
            // that the tap landed. No icon dependency needed — the checkmark is a
            // free-rendering unicode character that matches the body text style.
            Text(
                text = "Given ✓",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FilledTonalButton(onClick = onMarkGiven) {
                Text("Log dose")
            }
        }
    }
}

/**
 * Render an [Instant] as a local "8:00 AM" / "8:00 PM" time label. Hand-rolled because
 * v0.1 is English-only and pulling in a Locale-aware formatter is premature. The
 * format mirrors the Pet Detail dose-times UI for visual consistency.
 */
private fun formatLocalTime(
    instant: Instant,
    timeZone: TimeZone,
): String {
    val ldt = instant.toLocalDateTime(timeZone)
    val hour24 = ldt.hour
    val hour12 = ((hour24 + 11) % 12) + 1
    val minute = ldt.minute.toString().padStart(2, '0')
    val suffix = if (hour24 < 12) "AM" else "PM"
    return "$hour12:$minute $suffix"
}

/**
 * Retrospective "what care has been completed today" card. If empty, shows a quiet
 * one-liner pointing the user at the active surface (Pet Detail's Log Dose button).
 * If populated, renders one row per dose with pet name, medication, and "Nm ago".
 *
 * The card stays at the bottom of the screen — the user opens Today first thing in the
 * morning to see what's coming, not what's already done. Putting completed-doses at the
 * top would make the "fresh slate" of a new morning feel cluttered. End-of-day, the
 * card grows; the visual weight at the bottom matches the cognitive weight of "look
 * how much I got done."
 */
@Composable
private fun LoggedTodayCard(recentDoses: List<RecentDoseUi>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (recentDoses.isEmpty()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Nothing logged yet today",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Open a pet to record a dose. Doses you log appear here so you can see the day at a glance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Card
        }
        // `now` is captured once per recomposition. Time-ago strings will only refresh
        // on the next composition pass (tab switch, log new dose, etc.) — fine for a
        // surface where the difference between "5m ago" and "6m ago" doesn't matter.
        val now = remember(recentDoses.firstOrNull()?.id) { Clock.System.now() }
        // Padding-only separation between rows. A Divider would feel heavy on a
        // surfaceVariant card; the extra vertical breathing room reads cleaner and
        // avoids the M3 1.0→1.2 divider-API rename.
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            recentDoses.forEach { dose ->
                LoggedDoseRow(dose = dose, now = now)
            }
        }
    }
}

@Composable
private fun LoggedDoseRow(
    dose: RecentDoseUi,
    now: Instant,
) {
    val timeAgo = formatTimeAgo(dose.givenAt, now)
    // Merged semantics so TalkBack announces the whole row as one node
    // ("Luna, Methimazole, 2 hours ago") instead of three sequential text reads.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${dose.petName}, ${dose.medicationName}, $timeAgo"
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = "${dose.petName} · ${dose.medicationName}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = dose.petSpecies,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = timeAgo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Renders a [LocalDate] as "Wednesday, May 15" for the Today-screen header. Pure
 * function — no Composable scope needed — so unit-testable without a UI test runner.
 *
 * Hand-rolled to avoid pulling in java.time formatters (which would force us to deal
 * with Locale plumbing) or kotlinx-datetime's Format builders (which would still need
 * locale-aware DayOfWeekNames). v1 is English-only; the i18n milestone will replace
 * this with strings.xml + Plurals.
 */
internal fun formatTodayHeader(date: LocalDate): String {
    val weekday =
        date.dayOfWeek.name
            .lowercase()
            .replaceFirstChar(Char::titlecase)
    val month =
        date.month.name
            .lowercase()
            .replaceFirstChar(Char::titlecase)
    return "$weekday, $month ${date.dayOfMonth}"
}

/**
 * Compact, tappable pet card for the Today screen's pet row. Horizontal layout —
 * 40dp avatar + (name / "N meds" subtitle) — gives roughly two chips visible at once on
 * a typical phone width with a third peeking, signaling horizontal scrollability.
 *
 * The med-count subtitle is the density payoff: a glance at the chip tells you whether
 * this pet has any active meds to attend to, without drilling into the detail screen.
 * The subtitle is omitted when count == 0 to avoid clutter on pets with no meds yet
 * (the chip is then just avatar + name, mirroring the original v0.1 layout).
 */
@Composable
private fun PetChip(
    pet: Pet,
    medCount: Int,
    onClick: () -> Unit,
) {
    val speciesLabel =
        pet.species.name
            .lowercase()
            .replaceFirstChar(Char::titlecase)
    // Plural-correct subtitle. Hand-formatted because v0.1 is English-only and pulling
    // in Android Plurals.xml resource lookup for a single string is premature.
    val medCountLabel =
        when (medCount) {
            0 -> null
            1 -> "1 med"
            else -> "$medCount meds"
        }
    // Merge descendant semantics so TalkBack announces this chip as one node
    // ("Luna, cat, 1 med") instead of three separate reads. The decorative avatar is
    // already excluded via clearAndSetSemantics inside PetAvatar.
    val accessibleLabel =
        buildString {
            append(pet.name)
            append(", ")
            append(speciesLabel)
            if (medCountLabel != null) {
                append(", ")
                append(medCountLabel)
            }
        }
    Card(
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleLabel
                },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PetAvatar(species = pet.species, size = PetAvatarSizeCompact)
            Column {
                Text(
                    text = pet.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (medCountLabel != null) {
                    Text(
                        text = medCountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        // onSecondaryContainer at 75% effective via the bodySmall +
                        // muted-pair convention used elsewhere. We use
                        // onSecondaryContainer (not onSurfaceVariant) because the chip
                        // background IS the secondaryContainer, so contrast pairing
                        // stays in-family.
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    ToebeansTheme(dynamic = false) {
        HomeScreenContent(
            state = HomeUiState(pets = emptyList()),
            onAddPet = {},
            onPetClick = {},
            onMarkGiven = { _, _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPopulatedPreview() {
    val previewNow = Instant.parse("2026-05-16T20:00:00Z")
    ToebeansTheme(dynamic = false) {
        HomeScreenContent(
            state =
                HomeUiState(
                    pets =
                        listOf(
                            Pet(
                                id = "pet-1",
                                name = "Rufus",
                                species = Species.DOG,
                                birthdate = null,
                                weightKg = 12.0,
                                notes = null,
                                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                                archivedAt = null,
                            ),
                            Pet(
                                id = "pet-2",
                                name = "Luna",
                                species = Species.CAT,
                                birthdate = null,
                                weightKg = 4.1,
                                notes = null,
                                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                                archivedAt = null,
                            ),
                        ),
                    medCountByPetId = mapOf("pet-2" to 1),
                    recentDoses =
                        listOf(
                            RecentDoseUi(
                                id = "dose-1",
                                petName = "Luna",
                                petSpecies = "Cat",
                                medicationName = "Methimazole",
                                givenAt = previewNow,
                            ),
                        ),
                ),
            onAddPet = {},
            onPetClick = {},
            onMarkGiven = { _, _, _ -> },
        )
    }
}
