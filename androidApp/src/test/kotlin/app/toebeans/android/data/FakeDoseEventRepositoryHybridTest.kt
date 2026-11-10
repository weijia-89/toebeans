package app.toebeans.android.data

import android.content.Context
import app.toebeans.core.data.SqlDelightMedicationRepository
import app.toebeans.core.data.SqlDelightPetRepository
import app.toebeans.core.data.SqlDelightScheduleRepository
import app.toebeans.core.data.db.DatabaseFactory
import app.toebeans.core.db.ToebeansDatabase
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.notifications.NotificationActuator
import app.toebeans.core.notifications.NotificationOutcome
import app.toebeans.core.notifications.ScheduledReminder
import app.toebeans.core.scheduler.DefaultScheduleCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Fake-impl regression: [FakeDoseEventRepository] with SqlDelight parent repos (Pet/Med/Schedule).
 *
 * F1: production DI binds [SqlDelightDoseEventRepository]; this test keeps fake+SqlDelight
 * parent repos for in-memory dose semantics (not the production dose binding).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FakeDoseEventRepositoryHybridTest {
    private lateinit var context: Context
    private lateinit var database: ToebeansDatabase
    private val dbName = "fake-dose-hybrid-test.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
        database =
            DatabaseFactory(
                context = context,
                databaseName = dbName,
                callback = SqliteForeignKeysCallback(),
            ).create()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `observeLastGivenForMedication sees doses when meds persist in SqlDelight`() =
        runTest {
            val petRepo = SqlDelightPetRepository(database, kotlinx.coroutines.Dispatchers.Unconfined)
            val medRepo = SqlDelightMedicationRepository(database, kotlinx.coroutines.Dispatchers.Unconfined)
            val schedRepo = SqlDelightScheduleRepository(database, kotlinx.coroutines.Dispatchers.Unconfined)
            val doseRepo = FakeDoseEventRepository(medRepo, schedRepo)

            loadDemoData(
                petRepository = petRepo,
                medicationRepository = medRepo,
                scheduleRepository = schedRepo,
                doseEventRepository = doseRepo,
                scheduleCalculator = DefaultScheduleCalculator(),
                notificationActuator = HybridNoOpNotificationActuator(),
                now = Instant.parse("2026-11-08T15:00:00Z"),
                timeZone = TimeZone.UTC,
            )

            val at = Instant.parse("2026-05-24T12:00:00Z")
            doseRepo.recordGivenNow(
                doseEventId = "dose-1",
                scheduleId = "sched-luna-methimazole",
                medicationId = "med-luna-methimazole",
                at = at,
                note = null,
            )

            val last = doseRepo.observeLastGivenForMedication("med-luna-methimazole").first()
            assertNotNull(last)
            assertEquals(DoseStatus.GIVEN, last!!.status)
            assertEquals(at, last.scheduledAt)
        }
}

private class HybridNoOpNotificationActuator : NotificationActuator {
    override fun schedule(reminder: ScheduledReminder) = Unit

    override fun cancel(reminderId: String) = Unit

    override fun show(reminder: ScheduledReminder): NotificationOutcome = NotificationOutcome.Posted(0)
}
