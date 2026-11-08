# Boot receiver red test, 2026-11-08

The calibration row for `boot-receiver-enabled-flag-red` scored 79. This note is the required review before any later commit edits the manifest or `BootReceiver`.

## What was checked

- `BootReceiverManifestEnabledTest` reads `AndroidManifest.xml` and asserts `android:enabled` is `false` for `.notifications.BootReceiver`.
- QA ran that test on PR 130 head `d8cd67a`. Result: 0 passed / 1 failed. The line was `expected:<[fals]e> but was:<[tru]e>` at `BootReceiverManifestEnabledTest.kt:15`. The class compiled.
- QA reported the manifest and `BootReceiver.kt` unchanged in the slice.
- `gh pr checks 130` on 2026-11-08: every listed check passed. The gradle job is "Gradle build + shared tests". It does not run `:androidApp:testDebugUnitTest`.

## What was not checked

- This review did not re-run Gradle. The fail count is the QA run.
- The test does not check that the receiver turns on after the first schedule. A later edit can set `android:enabled="false"` and leave the receiver off forever. The test would go green. ADR-0012's second half would still be open.
- The parser uses `indexOf` on the raw XML. It failed for the right reason today. A reordered attribute could still break it.

## Next commit on this surface

Do not merge PR 130. A human reads this test first. The implementation is a separate change. That change also has to put `:androidApp:testDebugUnitTest` on the CI gradle job, or this red test stays invisible.
