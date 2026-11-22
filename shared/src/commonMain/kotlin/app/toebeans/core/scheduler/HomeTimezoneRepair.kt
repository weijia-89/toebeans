package app.toebeans.core.scheduler

import app.toebeans.core.data.DoseEventRepository
import app.toebeans.core.data.PetRepository
import app.toebeans.core.data.ScheduleWithPhases
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.HomeTimezone
import app.toebeans.core.model.Pet
import app.toebeans.core.notifications.NotificationActuator
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Repair path for a quarantined [Pet.homeTimezone]. Called only after the owner picks a
 * valid zone in pet edit and explicitly taps Recalculate.
 *
 * Old PENDING rows and their alarms are cancelled and removed; GIVEN/SKIPPED/MISSED rows
 * are preserved. Fresh doses are materialized in the repaired zone. Quarantined pets are
 * otherwise never re-materialized (see [TimeZonePolicy.Resolution.Quarantined]).
 */
public object HomeTimezoneRepair {
    public suspend fun repair(
        pet: Pet,
        zoneId: String,
        schedulesWithPhases: List<ScheduleWithPhases>,
        petRepository: PetRepository,
        doseEventRepository: DoseEventRepository,
        notificationActuator: NotificationActuator,
        scheduleCalculator: ScheduleCalculator,
        deviceTimeZone: TimeZone,
        now: Instant,
    ): Int {
        require(HomeTimezone.isValid(zoneId)) { "Refusing to persist an invalid home timezone id" }

        val allEvents = doseEventRepository.observeAll().first()
        val preservedIds = allEvents.filter { it.status != DoseStatus.PENDING }.map { it.id }.toSet()
        val pendingBySchedule = allEvents.filter { it.status == DoseStatus.PENDING }.groupBy { it.scheduleId }

        val repairedPet = pet.copy(homeTimezone = zoneId)
        petRepository.upsert(repairedPet)
        for (bundle in schedulesWithPhases) {
            for (old in pendingBySchedule[bundle.schedule.id].orEmpty()) {
                notificationActuator.cancel(old.id)
                doseEventRepository.delete(old.id)
            }
        }

        var armed = 0
        for (bundle in schedulesWithPhases) {
            val zone =
                when (val resolution = TimeZonePolicy.resolve(bundle.schedule, repairedPet, deviceTimeZone)) {
                    is TimeZonePolicy.Resolution.Use -> resolution.timeZone
                    TimeZonePolicy.Resolution.Quarantined -> error("validated zone id must not quarantine")
                }
            val reminders =
                ReminderRescheduler.materializeHorizonForSchedule(
                    schedule = bundle.schedule,
                    phases = bundle.phases,
                    medicationId = bundle.schedule.medicationId,
                    doseEventRepository = doseEventRepository,
                    scheduleCalculator = scheduleCalculator,
                    timeZone = zone,
                    now = now,
                    skipExistingIds = preservedIds,
                )
            for (reminder in reminders) notificationActuator.schedule(reminder)
            armed += reminders.size
        }
        return armed
    }
}
