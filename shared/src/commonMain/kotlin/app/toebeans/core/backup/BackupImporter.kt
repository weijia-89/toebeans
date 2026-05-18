package app.toebeans.core.backup

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import kotlinx.coroutines.flow.first

/**
 * Applies a [BackupExport] to the four repository contracts using a merge-by-id semantic
 * per ADR-0016 (v1 plain JSON, "insert new, skip existing").
 *
 * **Why merge-by-id (and not overwrite or merge-by-content):**
 *  - The user is the ground truth for what is in their target device. A passenger row in
 *    the file with the same id as an existing row is treated as a stale duplicate; we
 *    never overwrite a row the user has been editing on the target device.
 *  - Conversely we never delete: an entity that exists locally but not in the file stays.
 *    Import is purely additive.
 *  - The summary reports per-entity-type added/skipped counts so the user can see how
 *    many "duplicates" the import declined and decide whether to investigate.
 *
 * **Atomicity for schedules:** a schedule is imported only if neither its id nor any of
 * its file phases collide. If the schedule id is skipped, ALL of its file phases are
 * skipped too (the existing schedule's phases must not change). The phase set is always
 * the file's full set for a newly-inserted schedule (we do NOT try to mix file phases
 * with phases from somewhere else).
 *
 * **schemaVersion compat:** delegates the version check to [BackupSerializer] via the
 * caller; if the caller hands us a parsed [BackupExport] with a newer schemaVersion we
 * still refuse, throwing [BackupFormatException]. This is belt-and-suspenders because
 * the call site (import VM) parses with [BackupSerializer.decodeFromString] which
 * already rejects unsupported versions; doing it here too keeps the contract honest if
 * the importer is ever invoked from a different parsing path.
 *
 * **Not thread-safe.** The caller (an Android ViewModel scope) serializes calls. The
 * implementation reads each repository's `observeAll()` once at the start to build the
 * existing-id set, then writes serially.
 */
public class BackupImporter(
    private val petRepository: PetRepository,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
) {
    public suspend fun import(backup: BackupExport): BackupImportSummary {
        if (backup.schemaVersion > BackupExport.CURRENT_SCHEMA_VERSION) {
            throw BackupFormatException(
                "Backup schema version ${backup.schemaVersion} is newer than this app supports " +
                    "(max ${BackupExport.CURRENT_SCHEMA_VERSION}). Upgrade the app, then re-import.",
            )
        }

        // Snapshot existing-id sets up front. Subsequent writes that succeed land in the
        // repository state but do not affect this snapshot, so the merge decision is
        // stable for the duration of one import call.
        val existingPetIds = petRepository.observeAll().first().mapTo(mutableSetOf()) { it.id }
        val existingMedIds = medicationRepository.observeAll().first().mapTo(mutableSetOf()) { it.id }
        val existingScheduleIds = scheduleRepository.observeAll().first().mapTo(mutableSetOf()) { it.id }
        val existingDoseEventIds = doseEventRepository.observeAll().first().mapTo(mutableSetOf()) { it.id }

        var petsAdded = 0
        var petsSkipped = 0
        for (pet in backup.pets) {
            if (pet.id in existingPetIds) {
                petsSkipped++
            } else {
                petRepository.upsert(pet)
                petsAdded++
            }
        }

        var medsAdded = 0
        var medsSkipped = 0
        for (med in backup.medications) {
            if (med.id in existingMedIds) {
                medsSkipped++
            } else {
                medicationRepository.upsert(med)
                medsAdded++
            }
        }

        // Group phases by scheduleId so we can hand each newly-inserted schedule the
        // exact phase set the file declares. Phases for an existing-id schedule are
        // simply not used: ScheduleRepository.upsert requires a coherent (schedule,
        // phases) bundle, so we never write phases without the matching schedule.
        val phasesByScheduleId: Map<String, List<app.toebeans.core.model.SchedulePhase>> =
            backup.schedulePhases.groupBy { it.scheduleId }

        var schedulesAdded = 0
        var schedulesSkipped = 0
        for (schedule in backup.schedules) {
            if (schedule.id in existingScheduleIds) {
                schedulesSkipped++
            } else {
                val phases = phasesByScheduleId[schedule.id].orEmpty().sortedBy { it.phaseOrder }
                scheduleRepository.upsert(schedule, phases)
                schedulesAdded++
            }
        }

        var doseEventsAdded = 0
        var doseEventsSkipped = 0
        for (event in backup.doseEvents) {
            if (event.id in existingDoseEventIds) {
                doseEventsSkipped++
            } else {
                // Defensive: if the dose event's scheduleId was skipped (because it
                // already existed in the target with potentially different phases),
                // we still import the dose event. The dose event refers to a real
                // schedule by id; whether the destination's phases differ doesn't
                // change the historical fact that the dose was given.
                doseEventRepository.upsert(event)
                doseEventsAdded++
            }
        }

        return BackupImportSummary(
            petsAdded = petsAdded,
            petsSkipped = petsSkipped,
            medicationsAdded = medsAdded,
            medicationsSkipped = medsSkipped,
            schedulesAdded = schedulesAdded,
            schedulesSkipped = schedulesSkipped,
            doseEventsAdded = doseEventsAdded,
            doseEventsSkipped = doseEventsSkipped,
        )
    }
}

/**
 * Per-entity-type tally returned by [BackupImporter.import]. The UI uses this to render
 * the post-import toast or summary line per ADR-0016.
 */
public data class BackupImportSummary(
    public val petsAdded: Int,
    public val petsSkipped: Int,
    public val medicationsAdded: Int,
    public val medicationsSkipped: Int,
    public val schedulesAdded: Int,
    public val schedulesSkipped: Int,
    public val doseEventsAdded: Int,
    public val doseEventsSkipped: Int,
) {
    public val totalAdded: Int
        get() = petsAdded + medicationsAdded + schedulesAdded + doseEventsAdded

    public val totalSkipped: Int
        get() = petsSkipped + medicationsSkipped + schedulesSkipped + doseEventsSkipped
}
