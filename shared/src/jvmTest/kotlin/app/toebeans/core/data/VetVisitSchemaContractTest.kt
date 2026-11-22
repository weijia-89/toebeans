package app.toebeans.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RED TEST (test-as-spec; do NOT make green in this PR): the ADR-0020 vet-visit and
 * vaccine schema must exist and round-trip. Pins the DESIRED end state of ADR-0020 F5a
 * (docs/adr/0020-vet-visit-domain-model.md, Status: Accepted; the F5a slice row in
 * docs/launch-checklist.md on the launch-triage wave) at the schema-contract level. None
 * of the three tables exists in
 * `shared/src/commonMain/sqldelight/` today, which is exactly why this test is red.
 *
 * Desired end state pinned here, per ADR-0020 §A, §B, and §C:
 *
 * 1. Three tables exist: `VetVisit`, `Vaccine`, `VaccinationGiven` (PascalCase per the
 *    `Pet`/`Medication`/`Schedule` convention; timestamps INTEGER epoch-ms UTC per the
 *    `Pet.sq` header; FKs `ON DELETE CASCADE` per ADR-0010).
 * 2. A pet's vet visits (kind, scheduled/occurred date, vet name or clinic, notes) and
 *    vaccine records (name, date administered, next-due date) persist and round-trip.
 * 3. A vaccine rule-pack entry can be looked up by `(species, age_range,
 *    regulatory_region)` - the ADR-0020 §C tuple. The rule-pack lookup itself is NOT
 *    executable here: the pack file is F5b and the loader is F5c, and referencing either
 *    would not compile. F5b's known-answer test and F5c's scheduler test own that
 *    executable surface; the tuple contract is recorded in this KDoc so F5a's schema
 *    reviewer sees it in the same read.
 *
 * Red style, chosen deliberately: this test COMPILES today and fails on missing behavior
 * (the three `sqlite_master` rows), matching the repo's red-test precedent PR #155
 * (`DoseHistorySurvivesScheduleDeleteTest`), which also compiled and failed on behavior.
 * A repository-level round-trip (the `ScheduleRepositoryContract` style) is impossible in
 * a red-only PR because no `VetVisit` repository interface exists yet and inventing one
 * would add production code; until F5a adds it, raw SQL over the JDBC driver stands in
 * for the round-trip section, and F5a adds the SqlDelight-backed repository contract test
 * proper. After F5a lands, this test goes green: the table assert passes and the raw-SQL
 * round-trip runs for real.
 *
 * Lives in `:shared:jvmTest` like [SqlDelightScheduleRepositoryContractTest]: the JDBC
 * SQLite driver is not available in `commonTest`. FK enforcement follows ADR-0010:
 * `PRAGMA foreign_keys=ON` per connection.
 */
class VetVisitSchemaContractTest {
    private lateinit var driver: SqlDriver

    private fun tableNames(): List<String> =
        driver
            .executeQuery(
                identifier = null,
                sql = "SELECT name FROM sqlite_master WHERE type = 'table'",
                mapper = { cursor ->
                    QueryResult.Value(
                        buildList {
                            while (cursor.next().value) add(cursor.getString(0)!!)
                        },
                    )
                },
                parameters = 0,
            ).value

    @Test
    fun `vet visits and vaccine records persist with a species age region rule pack lookup`() =
        runTest {
            driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            ToebeansDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            val db = ToebeansDatabase(driver)
            val createdAtMs = Instant.parse("2026-05-19T00:00:00Z").toEpochMilliseconds()
            db.petQueries.insertPet(
                id = "p-vet",
                name = "Vet Pet",
                species = "dog",
                birthdate_iso = null,
                weight_kg = 1.0,
                notes = null,
                created_at = createdAtMs,
                archived_at = null,
            )

            // RED gate: ADR-0020 F5a must add these three tables. Today none exists.
            assertEquals(
                setOf("VetVisit", "Vaccine", "VaccinationGiven"),
                tableNames().toSet().intersect(setOf("VetVisit", "Vaccine", "VaccinationGiven")),
                "ADR-0020 F5a schema missing: VetVisit, Vaccine, VaccinationGiven tables do not exist yet",
            )

            // Round-trip pin (runs green only after F5a): record a past vaccination visit,
            // its vaccine reference row, and the join row from the discharge paperwork.
            driver.execute(
                null,
                """
                INSERT INTO VetVisit (id, pet_id, kind, scheduled_at, occurred_at, provider, notes, created_at)
                VALUES ('vv-1', 'p-vet', 'vaccination', NULL, 1779148800000, 'Lakeside Veterinary Clinic', 'Rabies booster given', 1779148800000)
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                INSERT INTO Vaccine (id, name, species, core_or_non_core, default_interval_months, regulatory_region)
                VALUES ('rabies-us', 'Rabies (US-regulated)', 'dog', 'core', 36, '["US-rabies"]')
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                INSERT INTO VaccinationGiven (vet_visit_id, vaccine_id, lot_number, expiration_date, dose_route)
                VALUES ('vv-1', 'rabies-us', 'LOT-4471', '2029-05-01', 'IM')
                """.trimIndent(),
                0,
            )
            val row =
                driver
                    .executeQuery(
                        identifier = null,
                        sql =
                            """
                            SELECT v.kind, v.occurred_at, v.provider, v.notes,
                                   vac.name, vac.default_interval_months, vg.lot_number
                            FROM VetVisit v
                            JOIN VaccinationGiven vg ON vg.vet_visit_id = v.id
                            JOIN Vaccine vac ON vac.id = vg.vaccine_id
                            WHERE v.id = 'vv-1'
                            """.trimIndent(),
                        mapper = { cursor ->
                            QueryResult.Value(
                                if (cursor.next().value) {
                                    Triple(
                                        listOf(
                                            cursor.getString(0),
                                            cursor.getLong(1),
                                            cursor.getString(2),
                                            cursor.getString(3),
                                        ),
                                        listOf(
                                            cursor.getString(4),
                                            cursor.getLong(5),
                                        ),
                                        cursor.getString(6),
                                    )
                                } else {
                                    null
                                },
                            )
                        },
                        parameters = 0,
                    ).value
            val visitFields = row?.first ?: emptyList()
            val vaccineFields = row?.second ?: emptyList()
            assertEquals(
                listOf("vaccination", 1779148800000L, "Lakeside Veterinary Clinic", "Rabies booster given"),
                visitFields,
            )
            assertEquals(listOf("Rabies (US-regulated)", 36L), vaccineFields)
            assertEquals("LOT-4471", row?.third)

            // Next-due pin (ADR-0020 §C: derived at scheduling time from cadence, never
            // inferred): visit date + default_interval_months is the v1 derivation; the
            // F5b rule pack supplies the cadence F5c actually uses.
            val administered =
                Instant
                    .fromEpochMilliseconds(visitFields[1] as Long)
                    .toLocalDateTime(TimeZone.UTC)
                    .date
            val nextDue = administered.plus(36, DateTimeUnit.MONTH)
            assertEquals(
                LocalDate.parse("2029-05-19"),
                nextDue,
                "vaccine record must yield a next-due date (visit date + cadence months)",
            )
            assertEquals(
                "2026-05-19",
                administered.toString(),
                "round-trip sanity: occurred_at epoch-ms maps back to the visit date",
            )
        }
}
