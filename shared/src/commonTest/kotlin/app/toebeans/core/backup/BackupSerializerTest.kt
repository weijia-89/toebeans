package app.toebeans.core.backup

import app.toebeans.core.model.DoseEvent
import app.toebeans.core.model.DoseStatus
import app.toebeans.core.model.Medication
import app.toebeans.core.model.Pet
import app.toebeans.core.model.Schedule
import app.toebeans.core.model.SchedulePhase
import app.toebeans.core.model.Species
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BackupSerializerTest {
    private val serializer = BackupSerializer()

    @Test
    fun `round-trip preserves all fields including null and empty collections`() {
        val original = sampleExport()

        val json = serializer.encodeToString(original)
        val parsed = serializer.decodeFromString(json)

        assertEquals(original, parsed, "round-trip must preserve all fields exactly")
    }

    @Test
    fun `round-trip preserves empty payload`() {
        val empty =
            BackupExport(
                schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
                exportedAt = Instant.parse("2026-05-15T12:00:00Z"),
                appVersion = "0.1.0",
                pets = emptyList(),
                medications = emptyList(),
                schedules = emptyList(),
                schedulePhases = emptyList(),
                doseEvents = emptyList(),
            )

        val parsed = serializer.decodeFromString(serializer.encodeToString(empty))
        assertEquals(empty, parsed)
    }

    @Test
    fun `decoding malformed JSON throws BackupFormatException`() {
        assertFailsWith<BackupFormatException> {
            serializer.decodeFromString("not even json")
        }
        assertFailsWith<BackupFormatException> {
            serializer.decodeFromString("""{"schemaVersion": 1}""") // missing required fields
        }
    }

    @Test
    fun `decoding a backup from a future schema version is rejected with a helpful message`() {
        val future =
            """
            {
                "schemaVersion": ${BackupExport.CURRENT_SCHEMA_VERSION + 1},
                "exportedAt": "2026-05-15T12:00:00Z",
                "appVersion": "99.0.0",
                "pets": [],
                "medications": [],
                "schedules": [],
                "schedulePhases": [],
                "doseEvents": []
            }
            """.trimIndent()

        val ex = assertFailsWith<BackupFormatException> { serializer.decodeFromString(future) }
        assertTrue(
            ex.message!!.contains("newer than this app supports"),
            "error message must guide the user to upgrade. Got: ${ex.message}",
        )
    }

    @Test
    fun `unknown extra keys are ignored (forward compatibility)`() {
        // Simulates a future schemaVersion=1 file that someone added a future-optional field to.
        val withExtra =
            """
            {
                "schemaVersion": 1,
                "exportedAt": "2026-05-15T12:00:00Z",
                "appVersion": "0.1.0",
                "futureField": "ignored",
                "pets": [],
                "medications": [],
                "schedules": [],
                "schedulePhases": [],
                "doseEvents": []
            }
            """.trimIndent()

        val parsed = serializer.decodeFromString(withExtra)
        assertEquals(1, parsed.schemaVersion)
    }

    @Test
    fun `pretty and compact JSON produce semantically identical decodes`() {
        val original = sampleExport()
        val pretty = BackupSerializer(BackupSerializer.PrettyJson)

        val compactJson = serializer.encodeToString(original)
        val prettyJson = pretty.encodeToString(original)

        assertNotEquals(compactJson, prettyJson, "pretty must differ in whitespace")
        assertEquals(
            serializer.decodeFromString(compactJson),
            serializer.decodeFromString(prettyJson),
            "decode must yield same payload regardless of formatting",
        )
    }

    // ----- fixtures -----

    private fun sampleExport(): BackupExport {
        val now = Instant.parse("2026-05-15T12:00:00Z")
        val pet =
            Pet(
                id = "pet-1",
                name = "Mochi",
                species = Species.CAT,
                birthdate = LocalDate(2020, 3, 14),
                weightKg = 4.2,
                notes = "afraid of the vacuum",
                createdAt = now,
                archivedAt = null,
            )
        val med =
            Medication(
                id = "med-1",
                petId = pet.id,
                name = "Prednisone",
                doseAmount = "10mg",
                notes = null,
                createdAt = now,
                discontinuedAt = null,
            )
        val schedule =
            Schedule(
                id = "sched-1",
                medicationId = med.id,
                startDate = LocalDate(2026, 6, 1),
                endDate = null,
                createdAt = now,
            )
        val phase =
            SchedulePhase(
                id = "phase-1",
                scheduleId = schedule.id,
                phaseOrder = 0,
                durationDays = 5,
                dosesPerDay = 2,
                doseTimesLocal = listOf(LocalTime(8, 0), LocalTime(20, 0)),
                doseAmount = "10mg",
            )
        val event =
            DoseEvent(
                id = "evt-1",
                scheduleId = schedule.id,
                medicationId = med.id,
                scheduledAt = now,
                firedAt = now,
                resolvedAt = now,
                status = DoseStatus.GIVEN,
                note = "ate it from a treat",
            )
        return BackupExport(
            schemaVersion = BackupExport.CURRENT_SCHEMA_VERSION,
            exportedAt = now,
            appVersion = "0.1.0",
            pets = listOf(pet),
            medications = listOf(med),
            schedules = listOf(schedule),
            schedulePhases = listOf(phase),
            doseEvents = listOf(event),
        )
    }
}
