# ADR-0013: Adopt `androidx.compose.ui.test` for rendered-UI contract verification

Date: 2026-05-17
Status: **Proposed**
Deciders: Wei Jia; human reviewer must approve the dependency additions before this moves to Accepted.

## Context

Three UI surfaces shipped in M1 Tier B (B7 Schedule Detail, B8 inline calculator error UI, B9 night-dose warning) have **ViewModel-level state tests but no rendered-banner verification.** Each:

- Pins the state contract: a flag like `nightDoseWarning: Boolean` flips at the right moment.
- Declares Compose semantics (`clearAndSetSemantics`, `LiveRegionMode.Polite`, `contentDescription`).
- Has NO automated test that verifies the banner actually renders, that the affirm button actually wires to the VM, or that TalkBack would actually announce the warning on appearance.

The deferral was rationalized in v0.1-followups #1 as "the project does not yet use a snapshot framework." Empirically that's correct: `androidApp/build.gradle.kts` test deps are `junit`, `robolectric`, `kotlinx-coroutines-test` only. `grep -rE "androidx\.compose\.ui\.test" androidApp/src` returns zero hits. The framework is **not on the classpath**, and adopting it requires a Gradle dependency addition — which AGENTS.md classifies as **vibe-dangerous** (floor ≥95, mandatory human review).

This ADR exists to make the dep addition reviewable in isolation rather than smuggled into an unrelated commit.

## Decision

Add four test-only dependencies to `gradle/libs.versions.toml` and `androidApp/build.gradle.kts`:

| Coordinate | Why |
|---|---|
| `androidx.compose.ui:ui-test-junit4-android` | Drives `createComposeRule` / `createAndroidComposeRule` Robolectric-backed test environments. |
| `androidx.compose.ui:ui-test-manifest` (debugImplementation only) | Provides the `<activity>` declaration that `createAndroidComposeRule` needs at test time. Debug-only so it does NOT ship in release builds. |
| `androidx.compose.ui:ui-tooling` (debugImplementation only) | Required by `ui-test` for the inspectable composition tree. Debug-only. |
| `androidx.test:core-ktx` | `ApplicationProvider.getApplicationContext<Context>()` for Compose rule init. |

Pin versions to whatever the existing `compose-bom` resolves (currently `2024.10.01` per `libs.versions.toml`). The BOM constrains the ui-test artifacts to the same Compose version as ui/ui-foundation, eliminating version-drift risk.

## Pattern for tests written against this framework

Reference pattern (to be created when the first test lands):

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhaseEditorCardNightDoseBannerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `night-dose warning banner renders with correct text and TalkBack semantics`() {
        val draft = phaseDraft().copy(
            doseTimes = listOf(LocalTime(3, 0)),
            nightDoseWarning = true,
        )
        var affirmedCalls = 0
        rule.setContent {
            PhaseEditorCard(
                index = 0, draft = draft, isOnlyPhase = true,
                onChange = {}, onRemove = {},
                onAffirmNightDose = { affirmedCalls += 1 },
            )
        }
        rule.onNodeWithText("This dose fires between midnight and 6am. Confirm you want to be woken up.")
            .assertIsDisplayed()
        rule.onNodeWithText("Yes, that's intentional").performClick()
        assertEquals(1, affirmedCalls)
    }
}
```

Three tests of this shape would close the F4 gap from the 2026-05-17 review (one each for B7 delete dialog, B8 formError banner, B9 night-dose banner).

## Consequences

### Positive

- Each Compose surface gets a test that pins the user-observable contract, not just the state machine.
- TalkBack-semantics regressions get caught at unit-test time, before they become accessibility bugs in the field.
- The night-dose banner's affirm-button wiring becomes mechanically verifiable instead of manually smoke-tested.
- All three test deps are first-party AndroidX; no new supply-chain surface.

### Negative

- Four new test-classpath deps (debugImplementation + testImplementation surface).
- ~30s added to `:androidApp:testDebugUnitTest` runtime per Compose rule init (one-time per test class).
- Robolectric Compose tests are known to be flakier than pure JVM tests under heavy parallelism; mitigation is `--max-parallel-forks=1` for Compose test classes if needed (defer until evidence of flake).

### Rejected alternatives

- **Paparazzi** (Cash App's snapshot framework). Rejected: requires LayoutLib bundling, opens an image-diff workflow we don't have CI infrastructure for, and produces brittle pixel-equality tests for what we actually need (semantic-equality).
- **Roborazzi** (Robolectric-based snapshot). Same image-diff problem as Paparazzi, smaller user base.
- **Continue VM-level tests only.** Rejected: cannot catch the bug class where the VM contract is met but the Composable wiring is wrong (e.g., a callback never connected to the button click handler).
- **Manual smoke tests only.** Rejected: not reproducible, not catchable by CI, requires a human runner.

## Acceptance criteria for moving to Accepted

1. Human reviewer approves the four dep additions individually (per AGENTS.md vibe-dangerous review gate).
2. A single test in `PhaseEditorCardNightDoseBannerTest` is written and passes locally on Wei's machine.
3. CI's `build-and-test` job's `:androidApp:testDebugUnitTest` step passes with the new test included.
4. Calibration log entry for the dep addition scores ≥95 with `test_verif=20` (the test that this ADR proposes is the test that verifies the dep addition).

## Notes for the implementer

- Compose ui-test on Robolectric requires `sdk = [33]` or higher in `@Config`; 29/30 has known incompatibilities.
- `createComposeRule` is preferred over `createAndroidComposeRule` for our use case (no Activity needed unless lifecycle-specific behavior is under test).
- The `ui-test-manifest` artifact must be `debugImplementation`, not `testImplementation`, because it injects a `<activity>` into the test apk manifest. If accidentally placed in `testImplementation`, the rule will throw "no activity found" at test time.

## Cross-references

- `AGENTS.md` § Vibe-safety tiers (Gradle dep additions: vibe-dangerous ≥95).
- 2026-05-17 adversarial review F4 (zero Compose UI tests; framework not on classpath).
- v0.1-followups #1 (B9 deferred Compose snapshot test, deferral now traceable to this ADR).
- `gradle/libs.versions.toml` (where the version aliases would land).
