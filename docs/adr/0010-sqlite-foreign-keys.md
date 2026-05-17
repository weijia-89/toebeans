# 0010. SQLite foreign-key constraints are enabled per connection

- Status: accepted
- Date: 2026-05-17
- Deciders: Wei
- Tier: vibe-dangerous (per AGENTS.md; this gates real persistence correctness)

## Context

The toebeans schema at `shared/src/commonMain/sqldelight/app/toebeans/core/db/toebeans.sq` declares foreign-key relationships with explicit ON DELETE semantics:

- `Medication.pet_id` REFERENCES `Pet(id)` ON DELETE CASCADE
- `Schedule.medication_id` REFERENCES `Medication(id)` ON DELETE CASCADE
- `SchedulePhase.schedule_id` REFERENCES `Schedule(id)` ON DELETE CASCADE
- `DoseEvent.schedule_id` REFERENCES `Schedule(id)` ON DELETE CASCADE
- `DoseEvent.medication_id` REFERENCES `Medication(id)` ON DELETE CASCADE (to migrate to SET NULL per its KDoc, separate ADR when that lands)

The KDoc comments and our schema review assume these cascades fire.

**They do not, by default.** SQLite has foreign-key enforcement OFF by default per the SQLite project's own documentation:

> "Foreign key constraints are disabled by default (for backwards compatibility), so must be enabled separately for each database connection."
>, https://www.sqlite.org/foreignkeys.html § 2

SQLDelight's AndroidSqliteDriver inherits this default. Confirmed in:

- https://github.com/cashapp/sqldelight/issues/1241, "Hi, Ive noticed my ON CASCADE DELETE doesnt work, is it because of foreign keys being turned off on android by default?"
- https://github.com/sqldelight/sqldelight/issues/3571, documents the required Callback pattern

Without explicit enablement, every CASCADE clause in `toebeans.sq` is parsed-but-not-enforced. Deleting a Pet would leave orphan Medications, Schedules, SchedulePhases, and DoseEvents. The orphans would silently survive every backup and restore.

For toebeans, the medication-critical consequence: a user who deletes a deceased pet's record finds that their other pet's medication-firing alarms still reference the deleted pet's medications through the orphan chain. The alarms fire correctly (they read DoseEvent rows directly) but the UI's Pet-detail screen renders nothing for those doses because the Pet is gone.

## Decision

Enable SQLite foreign-key constraints on every connection opened by the toebeans database driver. Use SQLDelight's `AndroidSqliteDriver.Callback.onOpen` hook with an explicit `PRAGMA foreign_keys=ON` statement, matching the pattern recommended in SQLDelight issue #1241.

## Implementation pattern

When the SQLDelight wire-up lands in milestone 1, replace the FakeRepository singles in `androidApp/src/main/kotlin/app/toebeans/android/di/AppModule.kt` with the real driver + repositories. The driver construction MUST include the callback shown below.

```kotlin
package app.toebeans.android.data

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.toebeans.core.db.ToebeansDatabase

/**
 * AndroidSqliteDriver.Callback that enforces foreign-key constraints on every
 * database connection opened by the toebeans Android driver. See ADR-0010 for
 * the rationale and the primary-source citations.
 */
public class SqliteForeignKeysCallback :
    AndroidSqliteDriver.Callback(ToebeansDatabase.Schema) {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys=ON")
    }
}
```

Usage in `AppModule`:

```kotlin
single<SqlDriver> {
    AndroidSqliteDriver(
        schema = ToebeansDatabase.Schema,
        context = androidContext(),
        name = "toebeans.db",
        callback = SqliteForeignKeysCallback(),
    )
}
single { ToebeansDatabase(driver = get()) }
```

## Test contract (vibe-dangerous; tier-95 floor)

Two tests, both required before the SQLDelight wire-up PR can merge:

1. **CASCADE actually cascades**: insert a Pet + Medication + Schedule + SchedulePhase + DoseEvent chain. Delete the Pet. Assert all four downstream rows are gone (count == 0 in each table after the delete).

2. **PRAGMA is set**: open a fresh database connection, query `PRAGMA foreign_keys`, assert the returned row equals `1`. This catches a future refactor that removes the callback by accident.

Both tests live under `androidApp/src/test/kotlin/app/toebeans/android/data/` and use Robolectric for SQLite-on-JVM. The callback class itself lives in `androidApp/src/main/kotlin/app/toebeans/android/data/SqliteForeignKeysCallback.kt`.

## Consequences

- **Positive**: schema cascades behave as the SQL declares. Deleting a Pet does not leave orphan rows. The KDoc comments in `toebeans.sq` are no longer aspirational.
- **Positive**: future SET-NULL migration for `DoseEvent.medication_id` (deferred to its own ADR) actually preserves retrospective DoseEvent history when a Medication is deleted.
- **Negative**: every connection-open pays the cost of one PRAGMA statement. Negligible (microseconds).
- **Risk**: the FK enforcement test must pass before any feature work touches the schema. If an existing feature relied on the silent-non-enforcement (none should, per the schema's KDoc claims), it will break loudly. This is the right loudness.

## References

- SQLite Foreign Key Support § 2: https://www.sqlite.org/foreignkeys.html
- SQLDelight FK discussion: https://github.com/cashapp/sqldelight/issues/1241
- SQLDelight Callback pattern: https://github.com/sqldelight/sqldelight/issues/3571
- AGENTS.md § Vibe-safety tiers (SQLDelight schema is vibe-dangerous, ≥95)
