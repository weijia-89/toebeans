# Internal-testing install checks

**Source:** commit `037337b4484d` (`docs/play-store-internal-testing-walkthrough.md`, phase 7).

## Steps

1. After Play Console says the release is available to testers, install on your own device from the tester link.
2. Open the app.
3. Force-stop and relaunch.
4. Wait until an alarm is due.
5. Open Settings and look at Export crash log.

## Expected result

- The app opens.
- Force-stop and relaunch show no crash (the walkthrough's check that the crash log handler is registered). An active proof uses a debug build with a deliberate crash, not this release.
- Notifications fire when an alarm is due.
- Settings, Export crash log is disabled when this install has had no crashes.
