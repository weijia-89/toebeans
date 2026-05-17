package app.toebeans.android.data

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant

/**
 * In-memory [DoseEventRepository] for the UI-scaffold milestone. Joins through the
 * in-memory `medications` and `schedules` maps held in [FakeRepositories.kt] to scope
 * dose events to a pet, mirroring the SQLDelight `selectDoseEventsForPetSince` query.
 *
 * **Not production-grade.** The SQLDelight-backed implementation will land alongside
 * the schedule materializer in a follow-up commit. This fake exists to let the UI
 * surface (log-dose button, last-dose indicator) be shipped and reviewed today instead
 * of being blocked on the materializer.
 *
 * State survives within the process but is lost on process death — fine for scaffold
 * review since the absent SQLDelight schema would be no different.
 */
public class FakeDoseEventRepository : DoseEventRepository {
    override fun observeForPet(
        petId: String,
        sinceInclusive: Instant,
    ): Flow<List<DoseEvent>> =
        // We need a tri-flow combine so the Pet-detail view reacts when meds, schedules,
        // OR doses change. Using combine() over StateFlow yields synchronous emission of
        // the current value on each subscription — Compose sees the data immediately, no
        // initial-emission flicker.
        combine(medications, schedules, doseEvents) { meds, scheds, events ->
            val petMedIds =
                meds.values
                    .filter { it.petId == petId }
                    .map { it.id }
                    .toSet()
            val petSchedIds =
                scheds.values
                    .filter { it.medicationId in petMedIds }
                    .map { it.id }
                    .toSet()
            events.values
                .asSequence()
                .filter { it.scheduleId in petSchedIds && it.scheduledAt >= sinceInclusive }
                .sortedByDescending { it.scheduledAt }
                .toList()
        }

    override fun observeLastGivenForMedication(medicationId: String): Flow<DoseEvent?> =
        combine(schedules, doseEvents) { scheds, events ->
            val medSchedIds =
                scheds.values
                    .filter { it.medicationId == medicationId }
                    .map { it.id }
                    .toSet()
            events.values
                .asSequence()
                .filter { it.scheduleId in medSchedIds && it.status == DoseStatus.GIVEN }
                .maxByOrNull { it.scheduledAt }
        }

    override fun observeAllRecent(sinceInclusive: Instant): Flow<List<DoseEvent>> =
        // Single-flow observation — no join is needed for filtering, the UI does the
        // pet/med name lookup separately via its own MedicationRepository flow. This
        // keeps the query cheap and avoids triggering recompositions on med/sched
        // edits that don't actually change which doses fall in the window.
        doseEvents.map { events ->
            events.values
                .asSequence()
                .filter { it.status == DoseStatus.GIVEN && it.scheduledAt >= sinceInclusive }
                .sortedByDescending { it.scheduledAt }
                .toList()
        }

    override suspend fun recordGivenForSlot(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        scheduledAt: Instant,
        resolvedAt: Instant,
        note: String?,
    ): DoseEvent {
        assertScheduleMedicationConsistent(scheduleId, medicationId)
        // Idempotent on (scheduleId, scheduledAt) per the contract. If a GIVEN event
        // already exists for this slot, replace it with the new resolvedAt. This makes
        // a double-tap on "Log dose" a no-op rather than a crash — important because the
        // Today worklist's Log button is finger-sized and stress-tap-prone.
        val existing =
            doseEvents.value.values.firstOrNull { existing ->
                existing.scheduleId == scheduleId &&
                    existing.scheduledAt == scheduledAt &&
                    existing.status == DoseStatus.GIVEN
            }
        val event =
            DoseEvent(
                id = existing?.id ?: doseEventId,
                scheduleId = scheduleId,
                medicationId = medicationId,
                scheduledAt = scheduledAt,
                firedAt = existing?.firedAt,
                resolvedAt = resolvedAt,
                status = DoseStatus.GIVEN,
                note = note ?: existing?.note,
            )
        doseEvents.update { it + (event.id to event) }
        return event
    }

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        medicationId: String,
        at: Instant,
        note: String?,
    ): DoseEvent {
        assertScheduleMedicationConsistent(scheduleId, medicationId)
        val event =
            DoseEvent(
                id = doseEventId,
                scheduleId = scheduleId,
                medicationId = medicationId,
                scheduledAt = at,
                firedAt = null,
                resolvedAt = at,
                status = DoseStatus.GIVEN,
                note = note,
            )
        doseEvents.value = doseEvents.value + (doseEventId to event)
        return event
    }

    /**
     * Debug-time cross-check that [medicationId] matches the schedule's actual medication.
     * In v0.1 with the in-memory fake this is essentially free; in M1 with SQLDelight the
     * FK constraint enforces it at write time. This `require` exists so a caller bug
     * (passing the wrong medicationId) fails loud here rather than producing a silently
     * orphan-looking dose event.
     */
    private fun assertScheduleMedicationConsistent(
        scheduleId: String,
        medicationId: String,
    ) {
        // Unknown schedule — caller's problem; SQLDelight FK will catch it in M1.
        val schedule = schedules.value[scheduleId] ?: return
        require(schedule.medicationId == medicationId) {
            "recordGiven* called with medicationId=$medicationId but schedule $scheduleId belongs to " +
                "medication ${schedule.medicationId}. Caller passed the wrong medicationId."
        }
    }

    override suspend fun delete(doseEventId: String) {
        doseEvents.value = doseEvents.value - doseEventId
    }
}
