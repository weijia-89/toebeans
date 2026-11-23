# ADR-0023: Decompose navigation forcing constraint

> Note (2026-11-22): The agent contract files were removed from this public repository. References in this document to those files mean the local working copies kept in a contributor's checkout.

Date: 2026-11-23
Status: Proposed (operator decision pending). No approval is claimed here.
Decider: Wei Jia (pending)
Tier: vibe-careful (ADR). The dependency addition and the navigation migration this ADR would authorise are vibe-dangerous. Each needs one >=95 human-reviewed change per AGENTS.md.

## Question

May the repo cross its three-layer limit for navigation by adopting Decompose?

This ADR tests the refusal item only. It does not add a dependency, change a build file, or change product code. ADR-0022 researched the options and recommended do nothing at that time. This ADR records what changed and puts a decision in front of Wei Jia.

## The rule under test

AGENTS.md refuses more than three abstraction layers at project start. Today the navigation path has three layers: a ViewModel, a repository, and SQLDelight. Decompose adds a fourth layer. It brings a component tree with its own lifecycle, child stacks, state keeper, and back handler, and that tree sits above the ViewModel. This is a real breach of the refusal item, not a naming quibble. It needs a forcing constraint and a named human reviewer before any code lands.

## Forcing user-visible problem

Two product facts make the bespoke shell expensive to keep:

- Tablet and wide-window navigation is now a product requirement (operator decision D2). The current shell is a fixed bottom NavigationBar in androidApp/src/main/kotlin/app/toebeans/android/ui/ToebeansAppShell.kt, 336 lines long. It has no window-size-aware layout. Supporting a rail or drawer by hand means rewriting selection state, back handling, and per-tab state save and restore in that one file.
- Navigation must be shareable with iOS at milestone 5 (operator decision D3). All navigation code lives in androidApp today. Keeping it there means each platform writes its own stack, back handling, and state restoration.

ADR-0022 records D2 in section 7 as tablet, foldable, or navigation-rail support as a product requirement. It records D3 in section 7 as shared navigation with iOS at milestone 5. ADR-0022 section 3 rejected Decompose then because there was no sharing payoff. D2 and D3 are that payoff.

The user harm stays the same as ADR-0022 names it: a navigation regression can hide the dose-logging route or the tab strip from an owner who is trying to log a dose. The migration is allowed only if the three behaviours in the contract plan below stay intact.

## Why the Android-only adaptive suite was rejected

The first-party adaptive stack, Navigation 3 plus Material 3 adaptive navigation suite, solves D2. ADR-0022 section 2 records the blockers: compileSdk 36 or higher versus 35 here, a forced Lifecycle 2.8.6 versus an alpha requirement of 2.10.0, and Compose forced to 1.11.4. That stack is also Android-only. It cannot satisfy D3 without a second navigation implementation for iOS. Paying a compileSdk bump and a forced-pin conflict for a platform-locked answer fails the iOS-sharing goal. Decompose is the candidate this ADR puts forward for that reason.

## Dependency facts already verified read-only

The orchestrator checked these facts without adding the dependency. Re-check the first-seen date at adoption time.

- Coordinate: com.arkivanov.decompose:decompose:3.6.2
- Publisher: Arkadii Ivanov
- License: Apache-2.0
- Source: github.com/arkivanov/Decompose
- First seen: 2021-03-28, which passes the >=30-day first-seen rule
- Release published: 2026-11-10T22:35:21Z
- POM dependencies: Essenty lifecycle, state-keeper, instance-keeper, and back-handler 2.7.1; kotlinx-serialization-core 1.6.3; kotlin-stdlib managed 2.3.21
- No network, analytics, AndroidX, or AI artifact in the declared set
- Repo toolchain: Kotlin 2.4.21, kotlinx-serialization 1.11.0

The declared serialization 1.6.3 is older than the repo's 1.11.0. Forward resolution is not yet proven. The implementation slice must run ./gradlew --refresh-dependencies and compile both source sets before the gauntlet. A resolution failure returns this ADR to the operator with no commit.

## API level

Decompose is pure Kotlin Multiplatform. It declares no Android compileSdk requirement and pulls no AndroidX artifact. The build level stays at compileSdk 35 and targetSdk stays at 34, from gradle/libs.versions.toml lines 14 and 16. No manifest permission is added. A compileSdk or targetSdk change would need its own ADR.

## Contract plan (Kotlin test, not added in this slice)

The migration must preserve three behaviours. The implementation slice adds a JUnit contract test at androidApp/src/test/kotlin/app/toebeans/android/ui/nav/DecomposeNavigationContractTest.kt with these named cases:

- allFiveTabsAreReachable: the component root exposes Today, Med List, Pets, Settings, and History, and selecting each returns a distinct child stack.
- doseLoggingRouteReachableFromToday: from the Today child, opening a dose reaches the medication edit or schedule create destination, matching resolvePetDetailMedicationDestination in ui/nav/PetDetailMedicationDestination.kt.
- backStackAndTabStateAreRestored: popping the dose route returns to Today, and leaving then returning to a tab restores that tab's child stack and scroll state.

This Kotlin test is not added now. A test of these three behaviours against the current shell would pass today, because the shell already has five tabs, dose routing, and save and restore. It would be a GREEN regression guard, not the required RED. A test that fails today has to name Decompose component classes that do not exist yet, so it would not compile, and a non-compiling test file breaks compileDebugUnitTestKotlin for everyone. The RED produced in this slice is a migration-gate script, described in the evidence note below. It goes GREEN when the migration lands and the three named cases exist.

## Gates still owed

- Wei Jia is the named human reviewer for both the failing test and the implementation. Get review of the test signature and assertions before implementation, per the test-as-spec rule in AGENTS.md.
- Failing test first, committed alone, then implement.
- Record a calibration entry at >=95 for the dependency addition and for the navigation change. No score bump without new evidence. Name any local test-verification gap in the notes.
- Re-run the resolved-graph checks on adoption: bash scripts/test_no_network.sh ., the no-analytics gate, and an AI-artifact check. Reject any resolved coordinate that pulls network, analytics, or inference code.
- Re-run the >=30-day first-seen check at adoption, not only from this ADR.
- Run ./gradlew --refresh-dependencies plus the compile tasks, then the full verify script and the CI Gradle tasks.
- Re-run the navigation manual QA cases on a device with the full cold-start block. Leave unchecked boxes for the operator.

## Consequences if approved

- The three-layer refusal item is breached for navigation only, with this ADR as the recorded exception.
- A component lifecycle layer replaces the bespoke NavController wiring in ToebeansAppShell.kt.
- Navigation state moves toward shared code, which is the only path to D3.
- Medication-critical tests and manual cases must be re-run in full.

## Decision

Proposed. Wei Jia decides. Until then no dependency is added and no product code changes.

### Failing test evidence

Kind of RED: a structural migration gate, not a compiled Kotlin behavioural assertion. The gate fails because the migration has not landed. The three behaviours in the contract plan are already present in the current shell, so a test of them against today's code would be GREEN and cannot serve as the required RED. The Kotlin contract test is a named plan above, not a file in this tree.

Command, run from the worktree root:

```
bash scripts/test_decompose_nav_migration.sh .
```

Exit code: 1

Output:

```
PASS forcing-constraint ADR 0023 exists
FAIL version catalog does not declare the com.arkivanov.decompose group
FAIL Decompose version is <missing>, expected 3.6.2
FAIL no build file consumes the Decompose dependency
FAIL no navigation model under shared/src/commonMain (iOS sharing goal unmet)
FAIL Decompose navigation contract test is missing
PASS compileSdk is unchanged at 35
PASS targetSdk is unchanged at 34
PASS the catalog contains no analytics, network, or AI artifact

5 Decompose navigation migration check(s) failed.
```

Self-test command and result:

```
bash scripts/test_test_decompose_nav_migration.sh .
```

Exit code: 0

Output:

```
PASS test_test_decompose_nav_migration: all fixtures behaved as documented
```

The self-test proves the gate is RED on a tree without the migration, GREEN on a migrated tree, and RED again if compileSdk is bumped to 36. No Gradle task was run in this slice. No dependency was added, so there is no new classpath to prove or fail.

## Implementation note (2026-11-23)

This note records what the implementation slice consumed. It does not change the decision state: Status stays Proposed and Wei Jia stays the pending decider. The planning sentences above describe the original proposal and are superseded where this note differs.

Exact coordinate consumed: `com.arkivanov.decompose:decompose:3.6.2`, declared in `gradle/libs.versions.toml`, consumed by `:shared/commonMain` as `api` and by `:androidApp` as `implementation`. No Compose extension coordinate was added, so no second coordinate needed vetting.

Adoption vetting: Maven Central POM read in full. Publisher Arkadii Ivanov, license Apache-2.0, declared dependencies Essenty lifecycle, state-keeper, instance-keeper and back-handler 2.7.1, kotlinx-serialization-core 1.6.3, and kotlin-stdlib managed to 2.3.21. The artifact line starts at 0.2.0, first seen 2021-03-28, which passes the >=30-day rule at adoption.

Resolved graph result from `./gradlew --refresh-dependencies` plus `:shared:dependencies --configuration jvmCompileClasspath` and `:androidApp:dependencies --configuration debugCompileClasspath`:

- `com.arkivanov.decompose:decompose:3.6.2` resolves with its `-jvm` and `-android` variants. Essenty modules resolve at 2.7.1.
- Forward resolution is proven by the graph and by compilation: `kotlinx-serialization-core:1.6.3 -> 1.11.0` and `kotlin-stdlib:2.3.21 -> 2.4.21`.
- The no-network and no-analytics gates pass on the source tree, and a literal scan of both resolved graphs found no network, analytics, or inference artifact.
- compileSdk stays 35 and targetSdk stays 34. No manifest permission was added.

Migration shape: the child-stack model lives in `shared/src/commonMain/kotlin/app/toebeans/core/navigation/`. `ToebeansAppShell.kt` reads the shared component for tab identity, dose destination, and back-state handling, while the existing NavHost still renders every screen. This keeps the medication-critical composables on their existing path.

The two limits first recorded here were closed in phase 29. The shell now holds one remembered `LazyListState` per scrolling tab and passes it into Today, Med List, Pets and History through an optional parameter. Leaving a tab saves the first-visible index and pixel offset; entering it replays both. Hardware back and the saved callbacks route through `handleBackEvent`, which updates the shared model only when the NavHost actually popped. `TabScrollRestorationTest` proves a restored offset reaches a real `LazyColumn`.

Residual limits after phase 29: the shared model stores one Int offset, and the first-visible item index lives in shell-local remembered state, so a full position survives tab switches but not process death. The model tracks one dose config, while stacked routes such as MedicationEdit to ScheduleEdit can put two destinations on the NavHost; back presses converge to a consistent state, but the model clears its dose config on the first pop of that pair. Neither limit is hidden by this note.
