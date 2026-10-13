# BootReceiver docs match the code

**Source:** PR #53 (`c5799da44f75`), BootReceiver / README consistency.

## Steps

1. From the repo root, search README.md and docs/ROADMAP.md for the phrases `stub path`, `still landing`, and `not yet wired`.
2. Read the top KDoc of `androidApp/src/main/kotlin/app/toebeans/android/notifications/BootReceiver.kt`.
3. Optional: read `loadPendingRemindersInHorizon` in `ToebeansApp.kt`.

## Expected result

- The search finds no stale BootReceiver rehydration claims.
- The BootReceiver KDoc describes `ToebeansApp.rehydrateBootAlarms` / SQLDelight, not a stub path.
