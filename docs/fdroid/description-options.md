# Description options (suggestions for the operator to edit)

Decision for this wave: Option A (short) is the selected base. Its words fill the slots in docs/fdroid/listing-draft.md. Options B and C remain as alternatives and are not used here.

These are three starting points, not final copy. None is published. The operator writes the words that ship.

Each option covers three points. Records stay on the device. Alarms cover a 72-hour window and return after a reboot. The app does not act as a vet. Read the options beside docs/fdroid/listing-draft.md. Facts come from README.md and CHANGELOG.md. The metadata draft is docs/fdroid/app.toebeans.android.yml.

## Option A: short

Use when the listing needs a few plain sentences. This is the closest match to the current one-line metadata Summary.

```text
toebeans is a medication reminder for pets. It runs on your Android phone and keeps every record there. Pets, medications, schedules, and dose history live in app-private storage. The app has no network access and sends nothing anywhere.

Add a pet, add a medication, then set when the doses are due. toebeans raises an alarm at each dose time. Alarms cover the next 72 hours, and a reboot does not clear them.

toebeans does not give medical advice. It has no symptom checker, no dose-safety check, and no drug warnings. It is a reminder, not a vet. Ask a veterinary professional what to give your pet.
```

## Option B: medium

Use when the listing can carry a few paragraphs and name the main screens.

```text
toebeans helps you remember pet medications. You enter your pet, the medication, and the times each dose is due. When a dose comes up, the app posts an alarm. It keeps firing alarms for the next 72 hours and re-arms them after a phone restart, so a reboot does not leave a gap.

Every record stays on the device. Pets, medications, schedules, and the dose log sit in app-private storage. There is no account, no cloud sync, and no analytics. toebeans has no network access at all. You can export a plain JSON backup to a place you choose and import it back on the same device.

On the Today screen you tap Log dose when you give a dose. The History screen shows the last 30 days, including doses you gave, skipped, or missed. The Reminders screen lists active schedules and shows their dose times. Settings holds the theme picker and the backup tools.

toebeans is not a vet and does not give medical advice. It has no symptom checker, no diagnosis, no drug-interaction warnings, and no dose-safety checks. The emergency card lists poison-control phone numbers and tells you to call a veterinary professional. Use toebeans to carry out a plan you already have with your vet.
```

## Option C: long

Use when the listing has room for headings and the operator wants the permissions explained.

```text
toebeans is a local-only medication reminder for pets. It runs on Android phones. The app holds your records on your phone and does not connect to the internet.

How reminders work

You add a pet, add a medication under that pet, then set a schedule. A schedule names the start date and the times of day each dose is due. toebeans uses the phone alarm system to raise a notification at each time. It arms alarms for a 72-hour window. After a reboot, the app reloads any pending doses in that window and arms their alarms again. If notifications are turned off, the app says so on the Today screen and points you to Settings.

When you give a dose, open Today and tap Log dose. The row changes to Given. The History screen keeps a 30-day record of every dose and its status: given, skipped, missed, or still pending. You can filter it to one pet. The Reminders screen lists active schedules, and tapping one shows its dose times and date range. Editing a schedule keeps the dose history already recorded.

Where data lives

Pets, medications, schedules, and dose events are stored in app-private storage on the device. There is no account and no server. The app has no network permission, no analytics, no crash reporting, and no advertising SDK. A build check fails if a network library appears in the code. If you want a copy of your records, Settings exports a plain JSON file to a location you pick. That file is not encrypted, so store it with the same care you would give a photo or a note.

The app asks for four permissions: notifications to post dose alarms, two exact-alarm permissions for timing, and boot access to restore alarms after a restart. It asks for no network permission.

What the app will not do

toebeans does not give medical advice and is not a vet. It has no symptom checker, no diagnostic content, no drug-interaction warnings, and no dose-safety check. It will not tell you whether a dose is right for your pet. The emergency card in the app lists two animal poison-control numbers and tells you to call a veterinary professional in an emergency. toebeans is for carrying out a medication plan you already agreed with your vet.
```

## What to check before choosing

- Each option states local-only storage, the 72-hour alarm window with reboot recovery, and the refusal to act as a vet. If the operator cuts a sentence, keep those three points.
- Check every claim against README.md and the current build before shipping. The operator's final words carry the risk.
- Do not add dose amounts, drug advice, or interaction claims to any option.
- "No AI feature" is a factual statement about this app. Do not turn it into a selling point or a comparison.
- The screenshots show demo drugs with doses. If the operator keeps them, keep the welcome dialog's note that demo data is not a medical recommendation. See docs/fdroid/screenshot-inventory.md.
