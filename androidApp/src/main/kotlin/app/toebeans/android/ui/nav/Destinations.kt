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
    public const val PETS: String = "pets"
    public const val SETTINGS: String = "settings"

    // Stacked destinations.
    public const val PET_DETAIL_ROUTE: String = "pets/{petId}"
    public const val PET_EDIT_ROUTE: String = "pets/{petId}/edit"
    public const val SCHEDULE_CREATE_ROUTE: String = "pets/{petId}/medications/{medicationId}/schedule/new"

    public fun petDetail(petId: String): String = "pets/$petId"

    public fun petEdit(petId: String): String = "pets/$petId/edit"

    public fun scheduleCreate(
        petId: String,
        medicationId: String,
    ): String = "pets/$petId/medications/$medicationId/schedule/new"

    /** Args for stacked destinations. Compose Navigation looks up by these exact strings. */
    public object Args {
        public const val PET_ID: String = "petId"
        public const val MEDICATION_ID: String = "medicationId"
    }
}

/** Bottom-nav entry. Ordered by typical usage frequency. */
public enum class BottomNavItem(
    public val route: String,
    public val label: String,
) {
    HOME(Destinations.HOME, "Today"),
    PETS(Destinations.PETS, "Pets"),
    SETTINGS(Destinations.SETTINGS, "Settings"),
}
