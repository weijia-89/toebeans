package app.toebeans.android.ui.nav

/**
 * Pet-detail medication row tap: schedule create when no schedule exists yet (beta-smoke
 * step 3), medication edit when a schedule is already on file (beta-smoke known-gap fix).
 */
internal enum class PetDetailMedicationDestination {
    SCHEDULE_CREATE,
    MEDICATION_EDIT,
}

internal fun resolvePetDetailMedicationDestination(activeScheduleId: String?): PetDetailMedicationDestination =
    if (activeScheduleId != null) {
        PetDetailMedicationDestination.MEDICATION_EDIT
    } else {
        PetDetailMedicationDestination.SCHEDULE_CREATE
    }
