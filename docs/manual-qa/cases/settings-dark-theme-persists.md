# Settings dark theme persists

**Source:** commit `7b2aba93d0cf` (verified on an emulator).

## Steps

1. Open Settings and note the default theme controls.
2. Tap Dark.
3. Force-stop and relaunch.

## Expected result

- Default state: Auto selected, Material You off.
- Tapping Dark flips the app to dark mode (warm dark brown background, cream text, card colors swap).
- The selection persists across force-stop and relaunch.
- Status bar icons swap (light icons on a dark background).
