package app.toebeans.android.data

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    override suspend fun recordGivenNow(
        doseEventId: String,
        scheduleId: String,
        at: Instant,
        note: String?,
    ): DoseEvent {
        val event =
            DoseEvent(
                id = doseEventId,
                scheduleId = scheduleId,
                scheduledAt = at,
                firedAt = null,
                resolvedAt = at,
                status = DoseStatus.GIVEN,
                note = note,
            )
        doseEvents.value = doseEvents.value + (doseEventId to event)
        return event
    }

    override suspend fun delete(doseEventId: String) {
        doseEvents.value = doseEvents.value - doseEventId
    }
}
