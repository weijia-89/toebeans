# ADR-0001: Kotlin Multiplatform + Compose Multiplatform

Date: 2026-05-14
Status: Accepted
Deciders: Wei Jia (with Cascade)

## Context

We need a mobile app that ships Android first and preserves an iOS path. The product plan envisions iOS arriving at milestone 5 (roughly 16+ weeks after the MVP). The candidates are:

- **Kotlin Multiplatform (KMP) + Compose Multiplatform**: single Kotlin codebase, native UI on both platforms.
- **Kotlin Multiplatform with native UI** (Compose on Android, SwiftUI on iOS): share business logic only.
- **Flutter**: cross-platform UI from day one.
- **React Native + Expo**: cross-platform UI in JS/TS.
- **Native-only Android, native-only iOS later**: separate codebases.

The reminders-first MVP needs reliable Android notifications, AlarmManager, WorkManager, and SQLite. The iOS port needs UNUserNotifications and the same persistence schema. There is no symptom-checker, no on-device ML at v1. The platform-native API surface is moderate.

Compose Multiplatform for iOS was **stable as of v1.8.0 (May 2025)** per JetBrains. KMP itself has been stable since November 2023. Netflix and Cash App use KMP in production.

## Decision

Kotlin Multiplatform with Compose Multiplatform for UI.

- `shared` module hosts the domain models, scheduler, SQLDelight schema, and backup codec.
- `androidApp` module hosts the Android-specific application, AlarmManager and WorkManager glue, NotificationChannel, and the Compose UI host.
- iOS source sets exist in `shared/src/iosMain/` but are gated off behind `gradle.properties: toebeans.enableIosTargets=false` until milestone 5.

## Consequences

### Positive

- Single Kotlin codebase for both platforms.
- SQLDelight gives type-safe SQL across Android (`android-driver`) and iOS (`native-driver`) with one schema source of truth.
- Compose Multiplatform avoids a UI rewrite when we add iOS.
- Hiring is straightforward: Kotlin/Android developers are plentiful.

### Negative

- Compose-iOS is newer than Compose-Android. Edge cases will appear when milestone 5 ships.
- Some Android-specific APIs (AlarmManager, RECEIVE_BOOT_COMPLETED) have no iOS equivalent and will need iOS-specific actuator code (UNUserNotifications calendar-style triggers).
- KMP build times are slower than pure-Android builds (per Guarana Technologies' 2025 production report).

### Rejected alternatives

- **Native-only Android.** Rejected: the iOS path would require a separate codebase and dilutes scarce engineering bandwidth. The product plan explicitly preserves iOS.
- **Flutter.** Rejected: weaker access to Android-specific APIs (AlarmManager, on-device ML Kit) that we will need in milestone 4. Dart is also a smaller hiring pool.
- **React Native + Expo.** Rejected: offline-first, document-heavy, notification-critical apps are not RN's sweet spot. Notification reliability on Android via RN has been a recurring complaint area.
- **KMP with native UI on iOS.** Rejected as overkill at this stage; we can fall back to it if Compose-iOS shows real instability at milestone 5.

## Verification

- `gradle/libs.versions.toml` pins `kotlin = "2.0.21"`, `agp = "8.7.0"`, `compose-multiplatform = "1.7.3"`.
- iOS source sets are excluded until `toebeans.enableIosTargets=true` in `gradle.properties`.
- CI fitness functions run against the Android target only at v0.1.

## References

- JetBrains, *Compose Multiplatform 1.8.0 Released: Compose Multiplatform for iOS is Stable*, May 2025.
- kotlinlang.org/multiplatform: KMP status, production-user list.
- Internal: `research/00-feasibility-dossier.md` §5.1.
