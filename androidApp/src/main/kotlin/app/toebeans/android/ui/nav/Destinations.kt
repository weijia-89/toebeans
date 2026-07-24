package app.toebeans.android.ui.nav

/**
 * Type-safe route definitions for the NavHost in [app.toebeans.android.ui.ToebeansAppShell].
 *
 * Routes are flat strings to remain compatible with androidx.navigation 2.8.x (the
 * compiler-checked type-safe API ships in 2.8 but is stabilized in 2.9; we'll migrate
 * when that lands and Kover supports K2 reliably).
 */
public object Destinations {
    // Bottom-nav root destinations.
    public const val HOME: String = "home"
    public const val REMINDERS: String = "reminders"
    public const val PETS: String = "pets"
    public const val SETTINGS: String = "settings"

    // Stacked destinations. Routes use literal path templates; helper functions below
    // build the runtime URLs. `PET_NEW` is a literal segment so it doesn't collide with
    // {petId} pattern matching — Compose Navigation matches longest-prefix.
    public const val PET_NEW_ROUTE: String = "pets/new"
    public const val PET_DETAIL_ROUTE: String = "pets/detail/{petId}"
    public const val PET_EDIT_ROUTE: String = "pets/detail/{petId}/edit"
    public const val MEDICATION_NEW_ROUTE: String = "pets/detail/{petId}/medications/new"
    public const val MEDICATION_EDIT_ROUTE: String = "pets/detail/{petId}/medications/{medicationId}/edit"
    public const val SCHEDULE_CREATE_ROUTE: String = "pets/detail/{petId}/medications/{medicationId}/schedule/new"

    // Schedule Detail is reached from the Reminders tab (top-level surface). Using a
    // flat top-level route keeps the URL short and avoids forcing the Reminder List to
    // pass petId + medicationId it already has on hand; the detail VM resolves both
    // from the scheduleId via observeById on the repositories.
    public const val SCHEDULE_DETAIL_ROUTE: String = "schedule/{scheduleId}"

    public fun petDetail(petId: String): String = "pets/detail/$petId"

    public fun petEdit(petId: String): String = "pets/detail/$petId/edit"

    public fun medicationNew(petId: String): String = "pets/detail/$petId/medications/new"

    public fun medicationEdit(
        petId: String,
        medicationId: String,
    ): String = "pets/detail/$petId/medications/$medicationId/edit"

    public fun scheduleCreate(
        petId: String,
        medicationId: String,
    ): String = "pets/detail/$petId/medications/$medicationId/schedule/new"

    public fun scheduleDetail(scheduleId: String): String = "schedule/$scheduleId"

    /** Args for stacked destinations. Compose Navigation looks up by these exact strings. */
    public object Args {
        public const val PET_ID: String = "petId"
        public const val MEDICATION_ID: String = "medicationId"
        public const val SCHEDULE_ID: String = "scheduleId"
    }
}

/** Bottom-nav entry. Ordered by typical usage frequency. */
public enum class BottomNavItem(
    public val route: String,
    public val label: String,
) {
    HOME(Destinations.HOME, "Today"),
    REMINDERS(Destinations.REMINDERS, "Reminders"),
    PETS(Destinations.PETS, "Pets"),
    SETTINGS(Destinations.SETTINGS, "Settings"),
}
