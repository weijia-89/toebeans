package app.toebeans.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.toebeans.core.db.ToebeansDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Schema-parity gate for the Medication table (vibe-dangerous per AGENTS.md).
 *
 * SQLDelight's golden fixture `1.db` predates `dose_unit`. Migration `1.sqm` adds it with
 * `ALTER TABLE Medication ADD COLUMN dose_unit TEXT NOT NULL DEFAULT 'MG'`, and SQLite
 * ALWAYS appends an added column at the end of the table. A fresh `Schema.create` built
 * from the current `Medication.sq` must therefore describe the same final layout:
 * `dose_unit` last, carrying the migration's default, with `notes` / `created_at` /
 * `discontinued_at` shifted one position earlier.
 *
 * This test runs on the JVM because [JdbcSqliteDriver] is JVM-only. It never edits the
 * golden fixture: the file is copied to a temp path, and only that copy is migrated.
 */
class MedicationSchemaParityTest {
    private data class ColumnInfo(
        val cid: Long,
        val name: String,
        val type: String,
        val notNull: Long,
        val defaultValue: String?,
        val pk: Long,
    )

    @Test
    fun `fresh Medication schema matches migrated golden fixture`() {
        val freshDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val goldenCopy =
            locateGoldenFixture().let { source ->
                File.createTempFile("toebeans-golden-", ".db").apply {
                    deleteOnExit()
                    source.copyTo(this, overwrite = true)
                }
            }
        val migratedDriver = JdbcSqliteDriver("jdbc:sqlite:${goldenCopy.absolutePath}")

        try {
            ToebeansDatabase.Schema.create(freshDriver)

            // JdbcDriver closes its connection after every statement outside a
            // transaction. Migration 4.sqm stages rows in a TEMP table, so the whole
            // chain must run on one connection. ToebeansDatabase is a Transacter;
            // using its transaction keeps one JDBC connection for every statement.
            val migratedDb = ToebeansDatabase(migratedDriver)
            migratedDb.transaction {
                ToebeansDatabase.Schema.migrate(migratedDriver, 1, ToebeansDatabase.Schema.version)
            }

            val freshColumns = tableInfo(freshDriver)
            val migratedColumns = tableInfo(migratedDriver)

            // Full parity: names, declared types, notnull flags, defaults, and ordinal
            // positions (cid) all come from PRAGMA table_info; list order adds a second
            // guard on ordinal position.
            assertEquals(
                migratedColumns,
                freshColumns,
                "fresh Schema.create Medication table_info must match migrated 1.db",
            )

            // Pin the direction: SQLite ADD COLUMN appends, so dose_unit is the last
            // column and keeps the DEFAULT 'MG' from 1.sqm. Fresh must adopt that shape.
            val doseUnit = freshColumns.single { it.name == "dose_unit" }
            assertEquals(
                freshColumns.last().name,
                doseUnit.name,
                "dose_unit must be the last Medication column (SQLite ADD COLUMN appends)",
            )
            assertEquals(
                "'MG'",
                doseUnit.defaultValue,
                "dose_unit must carry the migration's DEFAULT 'MG'",
            )

            val expectedIndexes = setOf("Medication_pet_id_idx", "Medication_discontinued_at_idx")
            assertEquals(expectedIndexes, userIndexNames(freshDriver))
            assertEquals(
                userIndexNames(migratedDriver),
                userIndexNames(freshDriver),
                "Medication index sets must match",
            )
        } finally {
            migratedDriver.close()
            freshDriver.close()
        }
    }

    private fun tableInfo(driver: SqlDriver): List<ColumnInfo> =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA table_info(Medication)",
                mapper = { cursor ->
                    QueryResult.Value(
                        buildList {
                            while (cursor.next().value) {
                                add(
                                    ColumnInfo(
                                        cid = cursor.getLong(0)!!,
                                        name = cursor.getString(1)!!,
                                        type = cursor.getString(2)!!,
                                        notNull = cursor.getLong(3)!!,
                                        defaultValue = cursor.getString(4),
                                        pk = cursor.getLong(5)!!,
                                    ),
                                )
                            }
                        },
                    )
                },
                parameters = 0,
            ).value

    private fun userIndexNames(driver: SqlDriver): Set<String> =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA index_list(Medication)",
                mapper = { cursor ->
                    QueryResult.Value(
                        buildSet {
                            while (cursor.next().value) {
                                // origin 'c' = created by CREATE INDEX; skip the PK autoindex.
                                if (cursor.getString(3) == "c") add(cursor.getString(1)!!)
                            }
                        },
                    )
                },
                parameters = 0,
            ).value

    private fun locateGoldenFixture(): File {
        val relative = "shared/src/commonMain/sqldelight/databases/1.db"
        var dir = File(".").absoluteFile
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: error("golden fixture not found: $relative")
        }
        error("golden fixture not found: $relative (searched upward from ${File(".").absoluteFile})")
    }
}
