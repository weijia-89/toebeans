# Play Store internal-testing track: walkthrough

A step-by-step for getting toebeans onto an internal-testing track so 1–3
trusted testers (including yourself) can soak-test the app on real
hardware. The internal track is the fastest one to set up (review is
usually under 24 hours, sometimes under one) and it does NOT make the
listing publicly searchable.

This document is action-ordered. Don't reorder steps; the Play Console
gates some of them behind earlier completions.

> **Estimated cost:** $25 USD one-time developer registration. No other
> cost for internal testing (no per-install fee; no Play Billing fee
> because we don't charge anything).
>
> **Estimated wall-clock time:** 3–5 hours of focused work split across
> two sessions, plus 1–24h of waiting for the first review.

---

## Phase 0: Prerequisites (do these BEFORE you open Play Console)

You need these artifacts in hand. Building them mid-walkthrough wastes
time because Play Console will time you out.

| Artifact | Where it lives | Format | Notes |
|---|---|---|---|
| Google account dedicated to this dev presence | Gmail | n/a | Strongly recommended: create a fresh `toebeans.app@gmail.com`-style account. Mixing with personal Gmail couples your identity to the app posture forever. |
| ID for identity verification | Driver's license or passport | Photo | Google now mandates ID verification for new developer accounts (since 2023). Plan for 1–3 day verification wait. |
| Payment method | Credit card | n/a | $25 registration. Same card stays on file. |
| Privacy policy URL | A public web page | URL | MUST be publicly reachable. See "Privacy policy" below for the minimum copy. |
| App icon | 512×512 PNG, ≤1 MB | PNG, 32-bit, no alpha border | This is the high-res icon for the listing, not the launcher. |
| Feature graphic | 1024×500 PNG/JPEG | image | Shown at the top of the listing. Plain works. |
| Phone screenshots | 1080×1920 (or similar 16:9 portrait), 2–8 images | PNG/JPEG | The four core toebeans screens: Home (Today), Pet detail, Settings, an Add Medication flow. |
| Signing keystore | `*.jks` or Play App Signing | file | See "App signing" below. |
| Release AAB | `androidApp/build/outputs/bundle/release/androidApp-release.aab` | AAB | NOT an APK. Built via `./gradlew :androidApp:bundleRelease`. |
| Privacy / data-safety answers | This document, "Data safety form" section | n/a | Pre-decide every answer; the form is mechanical once you have them. |
| Content rating questionnaire answers | This document, "Content rating" section | n/a | Same, pre-decide. |
| Tester email list | Plain text, one email per line | text | The Gmail addresses of the 1–3 humans who'll install the app. They must accept the invite link. |

### Privacy policy: minimum viable copy

Host this on any free static host (GitHub Pages is fine):
`https://<your-handle>.github.io/toebeans-privacy/`.

```
# toebeans: Privacy Policy

Last updated: <YYYY-MM-DD>

toebeans is a medication-reminder app for pet caregivers. It is
designed to be local-first: all data about your pets, their
medications, and dose history is stored on your device only.

## Data we collect

We collect nothing automatically. No analytics. No crash reporting.
No advertising IDs. No account creation.

## Data you can share with us

If you encounter a crash, the app stores a local crash log on your
device (the stack trace plus your Android version and device model,
with no pet, medication, or dose information). Under Settings → Export
crash log you can choose to share that file with us; nothing leaves
your device unless you actively tap "share".

## Third-party services

None. We do not use Firebase, Google Analytics, Crashlytics,
Mixpanel, AdMob, or any comparable SDK.

## Permissions

The app requests POST_NOTIFICATIONS (to fire medication reminders),
SCHEDULE_EXACT_ALARM (to fire them on time), and
USE_EXACT_ALARM/USE_FULL_SCREEN_INTENT (to surface high-priority
medication-critical reminders). It does not request internet
permission.

## Contact

<your email or GitHub issue tracker URL>
```

### App signing

Pick **Play App Signing** (recommended). Workflow:

1. Generate your upload key locally (one-time):
   `keytool -genkey -v -keystore toebeans-upload.jks -alias toebeans-upload -keyalg RSA -keysize 2048 -validity 9125`.
   Store passphrases in a password manager. Back up the keystore to
   two separate offline locations.
2. Configure `androidApp/build.gradle.kts` with a `signingConfigs`
   block reading from `local.properties` or env vars. Commit a
   commented-out template; never commit real credentials.
3. When you upload your first release, Play Console will offer to
   generate the app signing key on Google's side. Accept. From then
   on, Google holds the app signing key (which Google Play uses to
   sign user-installed APKs) and you hold the upload key (which only
   Play Console accepts).

Reasoning for Play App Signing over self-managed: if you lose the
upload key you can request a reset; if you lose the app signing key
and you self-manage, you can never publish another update to existing
users. This is a vibe-dangerous bag to be holding at the v0.1 stage.

---

## Phase 1: Developer account

1. Go to `https://play.google.com/console`.
2. Sign in with the dedicated Google account from Phase 0.
3. Accept the Developer Distribution Agreement. **Read it.** It's
   short. Pay attention to the indemnification clause and the
   anti-spam clause.
4. Pay the $25 registration fee.
5. Verify identity. Upload the ID photo. Expect 1–3 business days.
6. While waiting: create the privacy policy URL (Phase 0) and build
   the screenshots (Phase 2). You can't progress through Play Console
   until identity is verified.

---

## Phase 2: Build the release artifact

Do this BEFORE creating the app entry in Play Console; the entry
flow demands an AAB before it'll let you save.

1. Generate the upload keystore (above).
2. Update `androidApp/build.gradle.kts`:
   - Add a `signingConfigs.release` block reading from
     `keystorePropertiesFile` or env vars.
   - Reference it in `buildTypes.release.signingConfig`.
   - Confirm `buildTypes.release.minifyEnabled = true` and a
     `proguard-rules.pro` exists with rules for kotlinx-serialization,
     SQLDelight, Koin (when SQLDelight lands), Compose, kotlinx-datetime,
     and the LocalCrashLog handler class (don't strip the FQN; it
     needs to be referenceable from the `Thread.UncaughtExceptionHandler`
     interface registration at runtime).
   - Bump `versionCode` to 1 and `versionName` to "0.1.0" (matching
     `APP_VERSION_NAME` in `ToebeansApp.kt`).
3. Build: `./gradlew :androidApp:bundleRelease` (NOT
   `assembleRelease`; the .aab is what Play wants).
4. Output is in
   `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
   File size sanity check: a v0.1 toebeans with no images and minify
   on should be well under 5 MB. If you're seeing 25 MB+, R8 isn't
   running.
5. Build a screenshot harness:
   - Install the release AAB on a real device or
     emulator: `./gradlew :androidApp:installRelease` (the bundletool
     trick works with `bundletool build-apks` if needed).
   - Walk through the four screens manually, take screenshots, crop
     to 1080×1920 (or 1080×2400 depending on device aspect ratio;
     Play accepts a range).
   - Make sure the screenshots show STATE, not empty placeholders.
     If your seed pets are still showing (Luna and Rufus), keep them.
     They communicate the app shape better than a fresh empty
     state. (See M1.2 work item: revisit whether seed is on or off
     for public listing.)

---

## Phase 3: Create the app entry

1. Play Console → "Create app".
2. App name: `toebeans` (with the lowercase 't').
3. Default language: `English (United States) – en-US`.
4. App or game: `App`.
5. Free or paid: `Free`.
6. Declarations:
   - "Apps for children": **No**.
   - "Developer Program Policies": Yes (you read them).
   - "US export laws": Yes.
7. Click "Create app". You land on the app dashboard.

The dashboard now shows a long list of "Set up your app" tasks. You
must complete all of them under the **Internal testing** track before
you can ship the first build. Order matters less than the gate at the
bottom, but follow this sequence to avoid re-doing steps.

---

## Phase 4: App content (the policy gate)

This is where most of the friction lives. Each item below maps to a
Play Console form section.

### 4.1 Privacy policy

- Paste the public URL from Phase 0.

### 4.2 App access

- "All functionality is available without restrictions": **Yes**.
- No login required. No paywall. No regional gating.

### 4.3 Ads

- "Does your app contain ads?": **No**.

### 4.4 Content rating

Run the IARC questionnaire. Pre-decided answers:

| Category | Answer | Reason |
|---|---|---|
| Violence | None | n/a |
| Sexuality | None | n/a |
| Language | None | n/a |
| Controlled substances | None | toebeans tracks medications administered to PETS. The IARC questionnaire is about depicting controlled substances to users; this does NOT apply. |
| Gambling | None | n/a |
| User interaction | None | No social features in v0.1. No comments. No messaging. |
| Shares user location | No | We don't collect location. |
| Personal info sharing | No | We don't share anything; we don't collect anything. |
| Digital purchases | No | No IAP. |
| Mature themes | No | n/a |

Expected rating: **Everyone** (or **E** depending on which rating board's questionnaire fires).

### 4.5 Target audience and content

- Target age groups: **18 and over**. (You don't want kid-COPPA scope.)
- "Does your app appeal to children?": **No**.
- "Has any age-appropriate ad content?": n/a (no ads).

### 4.6 News app

- "Is this a news app?": **No**.

### 4.7 COVID-19 contact tracing

- "Public health functionality?": **No**.

### 4.8 Data safety

This is the longest form. The toebeans answers are uniform: we
collect nothing. But you have to answer every section.

| Section | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | **n/a: no data collected or transmitted** |
| Do you provide a way for users to request that their data be deleted? | **Yes, by uninstalling the app; all data is on-device** |

If Play makes you check categories anyway:

- Location: no
- Personal info: no
- Financial info: no
- Health and fitness: **HERE BE DRAGONS.** Pet medication is not
  human health data and is NOT in scope of this form, but if you're
  unsure, answer "no" with the rationale "data stays on-device, never
  collected by developer". Document this decision in `ROADMAP.md`
  M1.2 in case Play Console challenges it on review.
- Messages: no
- Photos and videos: no
- Audio files: no
- Files and docs: no
- Calendar: no
- Contacts: no
- App activity: no
- Web browsing: no
- App info and performance: no (we don't collect crash logs;
  user-initiated export only)
- Device or other IDs: no

### 4.9 Government apps

- "Is this a government app?": **No**.

### 4.10 Financial features

- "Does your app include any of these financial features?": **No**.

---

## Phase 5: Main store listing

This is the public-facing copy. It gets shown to your testers (and,
when you move to closed/open testing later, to real users).

### App details

- **App name:** `toebeans`
- **Short description (≤80 chars):**
  `Local-first medication reminders for the animals you love.`
- **Full description (≤4000 chars):**

```
toebeans helps you keep your pets healthy by reminding you when their
medications are due, and by remembering that you gave them, so you
don't second-guess yourself at 3 AM.

What it does

• Track multiple pets, each with their own medications and schedules.
• Handle tapering schedules with multiple phases (e.g. "10mg twice
  daily for 5 days, then 5mg daily for 14 days, then stop").
• Fire reminders on time using Android exact alarms, even when your
  phone is in Doze.
• Show today's due doses on the home screen with a one-tap "given"
  button.
• Log a quick dose retrospectively from the pet detail screen.
• Stay on your device. No cloud. No account. No analytics.

What it does NOT do

• It does not send any data to any server.
• It does not require an account.
• It does not show ads.
• It does not track you.
• It is not a substitute for veterinary advice. Always consult your
  veterinarian about your pet's medication schedule and dosing.

For caregivers of pets with thyroid conditions, diabetes, seizure
disorders, post-operative pain management, or any other regimen where
"did I already give that pill?" is a real question, toebeans is built
for you.
```

### Graphic assets

Upload the artifacts from Phase 0.

### Categorization

- **App category:** Medical
  - (Why not Health & Fitness? Because Health & Fitness implies user-self
    health tracking, which we explicitly aren't. Medical also has
    less competition for pet-medication-tracking searches.)
- **Tags:** medication, reminder, pet, veterinary, health.

### Contact details

- Email: <your dedicated dev email>
- Phone: optional, skip
- Website: optional, link your privacy-policy host or a project page

---

## Phase 6: Internal testing track

This is the actual gate that gets the app onto a tester's device.

1. Play Console → **Testing → Internal testing**.
2. **Create new release**.
3. Upload the AAB from Phase 2.
4. **Release name** (internal label): `0.1.0-internal-1`.
5. **Release notes:** something like:

```
First internal test build.

Known limitations:
- Export data is intentionally disabled. Backup export ships in M1.
- Schedule delete is intentionally not yet wired (requires Schedule
  detail screen).
- Real SQLDelight persistence ships in M1; v0.1 uses in-memory data
  that resets on app reinstall.

Please use this build for 30 days as documented in the soak-test
protocol I sent you. Tap "Export crash log" from Settings if anything
goes wrong and share that file with me directly. Thank you.
```

6. **Save**, then **Review release**, then **Start rollout to Internal testing**.
7. Play Console runs a review. Internal-testing reviews usually
   complete in under 24h, sometimes under 1h. You'll get an email.

### Tester list

While the review runs:

1. Play Console → **Testing → Internal testing → Testers** tab.
2. **Create email list**.
3. List name: `toebeans-soak-cohort`.
4. Add 1–3 emails (yours + your trusted testers). Save.
5. Check the box on the testers list to associate it with this track.
6. The "Join on the web" link in the Testers tab is the opt-in URL.
   Send it to your testers. They click, accept, then can install
   from the Play Store.

### What testers actually do

- Click the opt-in URL.
- Accept the tester invitation.
- Open the Play Store on their phone.
- Search for `toebeans` (only they will find it; the listing is
  not public).
- Install.
- Use the app per the soak-test protocol (`docs/soak-test-protocol.md`).
- After 30 days, send back the structured report.

---

## Phase 7: After it's live

Once Play Console says "Available to testers":

1. Install on your own device via the tester link. Confirm:
   - App opens.
   - Crash log handler is registered (you can prove this by force-stop
     + relaunch + observing no crash). For an active proof, use a
     debug build with a deliberate crash injected, not a release.
   - Notifications fire when an alarm is due.
   - Settings → Export crash log is disabled (since no crashes
     happened yet on this install).
2. Send testers the opt-in link + the soak-test protocol.
3. Set a calendar reminder for **day 30** to collect the structured
   reports.
4. Set a calendar reminder for **day 14** for a midpoint check-in:
   "are testers still using it?" This is the retention gate per
   ROADMAP M1.2; if no one uses it past day 14, M2 cannot proceed
   regardless of feature progress.

---

## Phase 8: Closing the loop

When all testers have submitted their day-30 reports:

1. Aggregate findings into a new commit on the toebeans repo, under
   `docs/soak-reports/v0.1.0-internal-1.md`. Include missed-alarm
   count per tester, crash-log content (redacted of any identifying
   info), unsolicited UX gripes, feature requests, and the retention
   answer.
2. Update ROADMAP M1.2 "Definition of done" check-marks based on what
   the soak test revealed.
3. **Make the AGPL vs Apache decision** if you haven't already. The
   soak test is the right moment because it has revealed how the
   product is actually used and by whom.
4. **Make the distribution wedge decision.** Same reason.
5. If the soak test surfaced safety-critical bugs, those become the
   M1 priority over feature work. Use the existing vibe-dangerous +
   ADR-0005 framework to address them.

---

## What you DON'T do at this stage

- **Don't open closed-testing or production tracks.** The internal
  track is enough until M1.2's definition-of-done passes.
- **Don't publish the app for public discovery.** That requires
  closed/open testing, a content rating audit, and a much longer
  review cycle.
- **Don't add tester email lists from people you can't reach
  directly.** Internal testing is for people you trust to send you
  structured feedback. Friends-of-friends are M2 work.
- **Don't try to set up Play Billing.** No revenue surface in v0.1
  per the ROADMAP.
- **Don't accept Play Console nudges to enable Firebase / Play
  Analytics / Crashlytics.** The whole point of ADR-0003 +
  ADR-0009 is that we do not collect data automatically. If Play
  Console insists, screenshot the nudge and add it to the AGPL-vs-
  Apache ADR (ADR-0010) as another data point about platform
  pressure.

---

## When something goes wrong

| Symptom | Likely cause | Fix |
|---|---|---|
| "Your release contains permissions that require a declaration" | One of the alarm permissions or the notifications permission needs an in-form declaration in App content → Sensitive permissions | Walk the form; the answers are "core to the feature" and "no alternative method exists for medication-critical reminders" |
| Identity verification rejected | ID didn't match name on the developer account | Re-submit with a clearer photo + matching legal name |
| Build rejected for "missing privacy policy" | Privacy policy URL is not publicly reachable | Confirm the URL renders in an incognito browser; redeploy if needed |
| "App not found" when tester clicks Play Store link | Tester is not signed into the Google account on the tester list, or hasn't accepted the invite | Have them open the opt-in URL while signed into the correct account |
| Notifications don't fire on a tester's device | OEM battery optimization is killing the alarm | This is exactly what the soak test should surface. Document the OEM + Android version in the report. M1 has a work item for OEM-specific guidance (Samsung One UI, Xiaomi MIUI, etc.). |
| Internal testing review takes >24h | Sometimes Play Review is slow during weekends | Wait. Don't open a second app entry. |
