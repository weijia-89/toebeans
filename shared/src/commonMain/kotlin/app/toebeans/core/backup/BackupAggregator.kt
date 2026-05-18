package app.toebeans.core.backup

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.MedicationRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

/**
 * Collects the full local-state snapshot into a [BackupExport] for the manual export flow
 * specified in ADR-0016 (v1 plain JSON; encryption deferred until PII surfaces).
 *
 * The aggregator fans in `observeAll()` reads across the four repository contracts and
 * assembles them with metadata supplied by the caller. It does NOT serialize — that is
 * [BackupSerializer]'s job. Keeping fan-in and serialization in separate seams lets a
 * future SQLDelight-backed aggregator emit identical [BackupExport] payloads without any
 * downstream change.
 *
 * **What gets included.** Every pet (including archived), every medication (including
 * discontinued), every schedule (including ended), every phase, every dose event of any
 * status. The backup represents complete local state, not "active only" state. UI filters
 * for active-only views are applied at the surface that consumes the data, never here.
 *
 * **What does NOT get included.** Preference values (theme, first-launch acknowledgement),
 * crash logs, and any future device-bound state. Those are device-local affordances, not
 * portable user data, and they restore via the OS/app on the receiving device.
 *
 * **Threading.** Each `observeAll()` read uses [kotlinx.coroutines.flow.Flow.first] so the
 * aggregator takes a one-shot snapshot and returns. The caller is responsible for invoking
 * `collect` from a coroutine on an appropriate dispatcher (the UI layer wraps the call in
 * a ViewModel scope with `Dispatchers.Default` for the serialization work).
 *
 * **Atomicity.** This implementation reads each repository independently and does NOT
 * guarantee that the four snapshots represent a single consistent moment in time. A
 * pet/medication created during the read window may or may not appear, and the
 * relationship between the snapshots is best-effort. For v1, with all repositories
 * backed by in-memory fakes and the export triggered by an explicit user tap, this is
 * acceptable. The SQLDelight-backed implementation in milestone 1 will use a single
 * transaction.
 */
public class BackupAggregator(
    private val petRepository: PetRepository,
    private val medicationRepository: MedicationRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
) {
    /**
     * Take a one-shot snapshot of the local state and return it as a [BackupExport].
     *
     * @param appVersion the running app's version string (`BuildConfig.VERSION_NAME` on
     *   Android). Captured into the backup for forensic / migration purposes; the
     *   import side reads it to log which app version produced a given file.
     * @param exportedAt the wall-clock instant the user tapped Export. Captured into the
     *   backup for the same reasons.
     */
    public suspend fun collect(
        appVersion: String,
        exportedAt: Instant,
    ): BackupExport =
        BackupExport(
            schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
            exportedAt = exportedAt,
            appVersion = appVersion,
            pets = petRepository.observeAll().first(),
            medications = medicationRepository.observeAll().first(),
            schedules = scheduleRepository.observeAll().first(),
            schedulePhases = scheduleRepository.observeAllPhases().first(),
            doseEvents = doseEventRepository.observeAll().first(),
        )
}
