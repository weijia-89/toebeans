# toebeans soak-test protocol — v0.1.0-internal-1

Hand this document to every internal-testing-track tester (yourself
included) before they install. It defines what "successful soak"
looks like, what data to capture, and how to report back.

The protocol is intentionally 30 days. Shorter runs miss the failure
modes that matter most for a reminder app: weekend-vs-weekday behavior
drift, DST transitions (if one happens during the window), battery-
optimization timeouts, and the "is this user still opening the app
in week 3?" retention question that gates M2 per ROADMAP M1.2.

---

## TL;DR for testers

1. Install via the internal-testing link.
2. Add at least **one real pet** with **one real medication** and a
   **real schedule** that matters to you.
3. Use the app every day for 30 days. Tap the right buttons when your
   pet gets their medication.
4. Fill in the daily log (one row per day, takes ~30 seconds).
5. If something feels broken, hit **Settings → Export crash log** and
   send the file. If there's no log, that's also a useful data point.
6. On day 30, fill in the structured report and send it back.

The protocol exists to make the report-back useful, not to be
homework. Skipping a daily log entry is fine; what matters is the
weekly snapshot and the day-30 report.

---

## What "successful soak" means

A successful soak means: across 30 days, every scheduled dose either
fired on time OR is accounted for (intentional skip, deliberate
medication change, etc.). "On time" means the notification surfaced
within 5 minutes of the scheduled time on a phone that was not in
explicit Do Not Disturb.

If a dose silently failed to fire (no notification, no record, the
user only noticed because they checked the app manually) — **that is
a soak failure** even if it happened once in 30 days. Medication-
critical apps don't get to grade themselves on a curve.

---

## Pre-flight (day 0)

Set this up before the soak window opens.

### Hardware

- A real Android phone, ideally Pixel a-series (3a or later), running
  Android 14 or 15. If a tester has a Samsung / Xiaomi / OnePlus /
  Motorola, document the OEM + Android version + the One-UI /
  MIUI / etc. layer version. OEM battery-optimization quirks are a
  major source of medication-app failure and we WANT to see them.
- Phone should be the tester's daily-driver, not a test device. The
  whole point is to soak under realistic battery state, doze, app-
  standby, and notification volume.
- Do NOT disable battery optimization for toebeans yet — we want to
  see whether the default Android scheduling gets the job done. If
  it doesn't, we'll know that's a real-world problem, not a Pixel-
  pristine fiction.

### Settings

- Notifications enabled at the OS level for toebeans.
- Notification channel sound + vibration on (default).
- Do Not Disturb schedule documented: when does it fire on your
  device? toebeans does NOT currently bypass DND (that's an M1
  decision), so it matters whether DND eats a 10pm dose.
- App not pinned, not foregrounded — toebeans should work when
  backgrounded for hours.

### Data

- Add at least one **real pet**. Don't soak with the seeded Luna and
  Rufus alone — they don't generate alarms.
- Add at least one **real medication** with a **real schedule** that
  matters in your life. If you don't have a pet on medication, soak
  with a pretend-vitamin schedule, but commit to actually acting on
  it for the full 30 days. A schedule you ignore tells us nothing.
- Confirm the schedule's first scheduled dose is in the future, not
  the past, so it'll fire normally.

### Baseline

On day 0, record:

- Phone make + model.
- Android version + security patch level.
- OEM software version (One UI 6.1, MIUI 14, etc.).
- Battery health percentage if available.
- toebeans version: `0.1.0` (visible in Settings → About).
- Number of other notification-emitting apps you have installed (rough
  count is fine; this matters because Android can throttle if you have
  100+ apps competing for the alarm budget).
- Any custom Do Not Disturb schedule (start/end times, days).
- Whether you've explicitly added toebeans to "Not optimized" in
  battery settings (default: NO — we want to see real-world default).

Record this in the day-0 row of your daily log.

---

## The daily log

A spreadsheet, a notes app, a sticky-note next to your bed — anywhere
you'll keep it for 30 days.

One row per day. Columns:

| # | Column | Format | Example |
|---|---|---|---|
| 1 | Date | YYYY-MM-DD | 2026-05-20 |
| 2 | Doses scheduled today (count) | int | 2 |
| 3 | Doses fired on time (count) | int | 2 |
| 4 | Doses fired late (count) | int | 0 |
| 5 | Doses silently missed (no notification) | int | 0 |
| 6 | Doses I logged via Today screen "Given" | int | 1 |
| 7 | Doses I logged via Pet detail "Log dose now" | int | 1 |
| 8 | App opened today? | y/n | y |
| 9 | Crash? | y/n | n |
| 10 | Anything weird | free text | "Notification took 12 minutes to surface after schedule time" |

"Fired late" = the notification did surface but more than 5 minutes
past the scheduled time on a phone NOT in DND. Be honest. Latency in
a medication reminder is itself a bug, even if the reminder
eventually arrived.

"Silently missed" is the worst case. If you notice it (because you
checked the app or the dose box), record it. If you didn't notice
and you just kept moving — that's exactly what we're trying to
prevent in production, but we can't capture what you didn't see. The
crash log won't catch it either; the alarm just doesn't fire. This
is part of why ROADMAP M1 has a "crash-on-render-of-stale-event
safety net" item.

---

## Weekly snapshot (day 7, 14, 21, 28)

Once a week, take 5 minutes and answer:

1. **Reliability.** How many doses across the week silently missed?
   If non-zero: what was happening on the phone at that time (in DND?
   low battery? force-stopped? travelling across a timezone?).
2. **Friction.** Was there a moment this week where the app made
   medication tracking *harder* than no app would have been? What
   was it?
3. **Trust.** Right now, would you trust toebeans alone — without a
   second backup reminder, without a paper calendar — to keep your
   pet on schedule? Why or why not?
4. **Retention check (day 14 + 28 only).** Did you open the app at
   least once on each of the last 7 days? If you went a stretch
   without opening it, why?

The retention check at day 14 is the M1.2 gate per ROADMAP. If no
tester reaches "yes" at day 14, M2 cannot proceed regardless of
feature progress.

---

## When something goes wrong

### A dose silently missed

1. Open toebeans. Note whether the dose appears in the "Logged today"
   card (it shouldn't — we didn't log it). Note whether it appears in
   the Today worklist (it should, as a still-pending row).
2. Note time, expected dose, actual time of discovery.
3. Note phone state at the expected fire time if you can reconstruct
   it: was the phone in your pocket? In DND? On the charger?
   Force-stopped recently? Reboot recently?
4. Add to the day's "Anything weird" column.

### The app crashed

1. **Don't** uninstall or clear app data. The crash log we just shipped
   needs to be readable.
2. Open toebeans → Settings → Diagnostics → "Export crash log".
3. Share the file directly with the developer (Signal, email, GitHub
   issue, whatever channel was agreed).
4. Continue using the app. The handler delegates to the OS so the
   process will have restarted cleanly.

### A weird notification arrived

Take a screenshot of the notification + the toebeans Home screen at
the same moment if you can. Add to "Anything weird".

### Travelling, daylight saving, time zone change

ROADMAP M1.5 covers timezone-aware behavior. v0.1 does NOT — schedules
are stored in local time and fire at local times. If you travel
across timezones during the soak, document:

- The transition date + direction.
- Whether the app fired alarms at the OLD local time, NEW local time,
  or stopped firing.

This is exactly the data ADR-0007 wants for its G3 acceptance gate.

---

## Day-30 structured report

Send this back. Markdown or plain text both fine.

```
# toebeans soak-test report — <your name or initials> — <date range>

## Setup
- Phone: <make model>
- Android: <version + security patch>
- OEM layer: <One UI 6.1, etc.>
- Battery health: <% or "unknown">
- Custom DND schedule: <y/n + details>
- Battery optimization for toebeans: <"not optimized" / "optimized" / "default">

## Pets and meds soaked
- <pet 1>: <medication, schedule shape — e.g. "BID 8am/8pm">
- <pet 2 if any>: <...>

## Reliability tally (across the whole 30 days)
- Total doses scheduled: <int>
- Doses fired on time (within 5 min): <int>
- Doses fired late: <int>
- Doses silently missed: <int>
- Crashes: <int> (attach crash logs separately)

## Top three issues
1. <one-paragraph description of the most important problem>
2. <second most important problem>
3. <third most important problem>

## Top three wins
1. <what worked well that you didn't expect>
2. <what worked well that you DID expect (worth validating)>
3. <what surprised you positively>

## Trust
On day 30, would you trust toebeans alone for your pet's medication
schedule? <yes / no / qualified yes>. Why?

## Retention
Did you open the app at least once on most days past day 14?
<yes / no>. Why or why not?

## Feature ideas (low priority — keep these short)
- <idea 1>
- <idea 2>

## Crash logs
Attached separately, or "no crashes".
```

That's it. Five minutes if you've kept the daily log; 20 minutes if
you have to reconstruct from memory.

---

## What the developer does with this

Per ROADMAP M1.2 definition-of-done:

- Aggregate into `docs/soak-reports/v0.1.0-internal-1.md`.
- Decide whether M1.2 closes (1+ tester at day 30 without trust loss).
- Decide whether crash log export is field-proven (1+ tester used it).
- Use the OEM data to scope M1 OEM-specific notification work.
- Use the timezone observations (if any traveled) as evidence for
  ADR-0007 G3.
- Use the silently-missed count to size the M1 "crash-on-render-of-
  stale-event safety net" work — if it's 0, the safety net is
  insurance; if it's >0, it's a P0 fix.

Thank you. This is real-world data on a medication app and it is
the most useful thing you can give the project at this stage.
