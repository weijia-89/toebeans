package app.toebeans.android.ui.nav

import app.toebeans.android.ui.nav.BottomNavItem.HISTORY
import app.toebeans.android.ui.nav.BottomNavItem.HOME
import app.toebeans.android.ui.nav.BottomNavItem.PETS
import app.toebeans.android.ui.nav.BottomNavItem.REMINDERS
import app.toebeans.android.ui.nav.BottomNavItem.SETTINGS
import app.toebeans.core.navigation.DefaultToebeansRootComponent
import app.toebeans.core.navigation.DoseDestination
import app.toebeans.core.navigation.ToebeansRootComponent
import app.toebeans.core.navigation.ToebeansTab

/** Builds the shared Decompose navigation model used by [app.toebeans.android.ui.ToebeansAppShell]. */
public fun createToebeansRootComponent(): ToebeansRootComponent = DefaultToebeansRootComponent()

/** Maps a shared tab to the flat NavHost route that renders it. */
internal fun ToebeansTab.route(): String =
    when (this) {
        ToebeansTab.TODAY -> Destinations.HOME
        ToebeansTab.MED_LIST -> Destinations.REMINDERS
        ToebeansTab.PETS -> Destinations.PETS
        ToebeansTab.SETTINGS -> Destinations.SETTINGS
        ToebeansTab.HISTORY -> Destinations.HISTORY
    }

/** Maps a bottom-nav entry to its shared tab. */
internal fun BottomNavItem.toTab(): ToebeansTab =
    when (this) {
        HOME -> ToebeansTab.TODAY
        REMINDERS -> ToebeansTab.MED_LIST
        PETS -> ToebeansTab.PETS
        SETTINGS -> ToebeansTab.SETTINGS
        HISTORY -> ToebeansTab.HISTORY
    }

/**
 * Maps the shared dose destination to the route that renders it. A missing destination
 * falls back to medication edit, which is the route the shell used before ADR-0023.
 */
internal fun doseLoggingRoute(
    destination: DoseDestination?,
    petId: String,
    medicationId: String,
): String =
    when (destination) {
        DoseDestination.SCHEDULE_CREATE -> Destinations.scheduleCreate(petId, medicationId)
        else -> Destinations.medicationEdit(petId, medicationId)
    }
