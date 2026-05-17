# ADR-0009: Capture uncaught exceptions to a local crash log; no telemetry

Date: 2026-05-16
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

The product posture (ADR-0003) is local-first with zero network calls and zero
analytics. A CI fitness function fails the build if any analytics or
crash-reporting library appears in the dependency graph. That posture is
intentional — it is one of the moats called out in the feasibility dossier and
it makes the privacy story unambiguous.

It also means **production exceptions are silent**. There is no Crashlytics,
no Sentry, no Bugsnag, no Firebase. A null-pointer on the medication-firing
path will either kill the app silently or surface as the system "Toebeans has
stopped" dialog with no further diagnostic information available to either the
user or the developer.

For a medication-critical application moving toward beta, this is unsafe.

## Decision

Install a JVM `Thread.UncaughtExceptionHandler` in `ToebeansApp.onCreate`
that:

1. Captures the throwable to a single rotating file under the app's private
   `filesDir` (`crash.log`, max ~256 KB, rotated to `crash.log.1` on size).
2. Includes minimal metadata: timestamp (UTC), build version code, Android
   API level, device model, thread name, full stack trace.
3. **Never** transmits the log over the network. The file exists only on the
   user's device.
4. **Always** delegates to the previously-registered default handler after
   writing, so the OS still receives the crash signal and the process
   terminates normally. We do not attempt to "swallow" the crash.

The log is surfaced through Settings → "Export logs" (a user-initiated
share-intent that copies the file to a location the user chooses). The user
is in full control of whether the log ever leaves the device. The Settings
copy is explicit: "We collect nothing automatically. If you hit a bug and
want to send us the log, you can export it here."

### What we are NOT doing

- **No automatic upload.** Telemetry, including crash reports, is the surface
  area we are deliberately not building. Any future change to this requires
  its own ADR and an explicit `permission_allowlist.sh` update.
- **No PII in the log.** The handler writes the stack trace and build/device
  metadata only. It does **not** dump SharedPreferences, the SQLDelight
  database, the in-memory `BackupExport`, or any other data structure. The
  fitness function `scripts/test_no_pii_in_crash_log.sh` (TODO, M1) will grep
  the crash-log handler for any reference to repository, dao, or model
  classes and fail the build if found.
- **No crash-reporting libraries.** We use the JVM-builtin
  `Thread.setDefaultUncaughtExceptionHandler` and `java.io.File`. Zero new
  dependencies.

## Consequences

### Positive

- A user who hits a crash can self-export and share the log, which gives us
  *some* diagnostic surface area for beta without violating the no-telemetry
  posture.
- The handler is a single small file (~80 LOC). The blast radius is
  contained.
- Local-only logs cannot be subpoena'd from a server the developer doesn't
  run.

### Negative

- Users who do not export their log give us zero signal. We will under-count
  real-world crashes by orders of magnitude relative to a telemetry-enabled
  app.
- A naive implementation could leak PII into the log (e.g. by including
  `toString()` output of a model object that contains a pet name or a
  caregiver email). The fitness function above is the mitigation; it has to
  exist before this ADR is "production-ready" in any meaningful sense.
- Log rotation is a place where bugs are easy to introduce. The rotation
  logic gets a unit test on first commit.

### Rejected alternatives

- **Crashlytics (Firebase).** Rejected: pulls in Firebase, which pulls in
  Google Play Services and a 2-MB-ish set of dependencies, and is a remote
  telemetry sink. Both contradict ADR-0003.
- **Sentry self-hosted.** Rejected: requires a server. Out of scope until
  there is a paid tier (milestone 6).
- **Do nothing; rely on Play Console crash reports.** Rejected for two
  reasons: (1) Play Console only catches native/JVM crashes that propagate
  to the OS, not ANRs or silently-killed background work, and (2) Play
  Console requires Play Store distribution, which beta does not yet have.
- **Tombstone-style file per crash, never rotate.** Rejected: unbounded
  disk usage on a phone is hostile to users.

## Verification

- Unit test: writing a throwable produces the expected file contents.
- Unit test: a second crash beyond the size threshold rotates the file.
- Unit test: the handler delegates to its predecessor after writing.
- Manual: trigger a crash in a debug build, verify the file appears under
  `/data/data/app.toebeans/files/crash.log`.
- Fitness function (M1): `scripts/test_no_pii_in_crash_log.sh` greps the
  crash-handler source for any reference to repository, dao, or model
  package names.

## References

- ADR-0003 (Local-first storage. No cloud at v1.)
- ADR-0005 (Vibe-dangerous reminder firing.)
- AGENTS.md § Refusal list ("No analytics. No crash reporting that exits
  the device.").
