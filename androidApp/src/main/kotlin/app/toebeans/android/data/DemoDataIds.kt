package app.toebeans.android.data

/**
 * The fixed demo data id set written by [loadDemoData] when the first-launch dialog's
 * "Load demo data" tap seeds Rufus and Luna.
 *
 * **Why this exists:** the database schema does not tag demo rows, so the Settings
 * "Remove demo data" affordance identifies demo rows by matching these ids (see
 * [SettingsViewModel][app.toebeans.android.ui.settings.SettingsViewModel.clearDemoData]).
 * User-created rows use `pet-${Uuid.random()}` style ids with different prefixes, so a
 * collision is not a real case.
 *
 * **Sync duty:** the literals mirror the seeding constants in [FakeRepositories.kt]
 * (`demoPets`, `demoMedications`, `DemoScheduleSpec`), which are private and cannot be
 * referenced from here. Keep the two lists in sync when demo seeding changes; a missed
 * sync leaves that row behind on clear.
 */
public object DemoDataIds {
    public val petIds: List<String> =
        listOf(
            "pet-rufus",
            "pet-luna",
        )

    public val medicationIds: List<String> =
        listOf(
            "med-rufus-carprofen",
            "med-rufus-fish-oil",
            "med-rufus-apoquel",
            "med-luna-methimazole",
            "med-luna-prednisolone",
            "med-luna-fortiflora",
        )

    public val scheduleIds: List<String> =
        listOf(
            "sched-rufus-carprofen",
            "sched-rufus-fish-oil",
            "sched-rufus-apoquel",
            "sched-luna-methimazole",
            "sched-luna-prednisolone",
            "sched-luna-fortiflora",
        )
}
