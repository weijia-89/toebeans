### Trainer notes

Program notes
- M1.2 Today-surface polish delivered in one focused PR from `origin/main`: vertical scroll on Home, in-page pet filter behavior, and due-row Edit affordance wired to existing Medication Edit + Schedule Detail surfaces.
- Filter behavior is intentionally in-page and stateful with bottom-nav save/restore semantics, avoiding unintended navigation away from Today.
- Edit flow intentionally remains transitional until the unified medication+schedule modal lands.

Your form
- Verification command completed successfully: `./gradlew ktlintCheck detekt :shared:jvmTest :androidApp:testDebugUnitTest`.
- Added regression coverage for filter projection (`HomeViewModelApplyPetFilterTest`) alongside existing due/join tests.
- Kept changes constrained to Home UI/viewmodel, app shell route wiring, and a targeted unit test.

Next session
- Execute manual QA install + launch flow:
  - `cd ~/Projects/toebeans && ./gradlew :androidApp:installDebug`
  - `adb shell am start -n app.toebeans.android/.MainActivity`
- Validate touch ergonomics of Edit vs Log dose spacing on physical device.
- Confirm Today filter state restore behavior after tab switches and process resume.

Manual QA
- `cd ~/Projects/toebeans && ./gradlew :androidApp:installDebug`
- `adb shell am start -n app.toebeans.android/.MainActivity`
