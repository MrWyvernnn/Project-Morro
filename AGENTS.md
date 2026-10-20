# Morro: Project Briefing (CLAUDE.md)

## 1. What Morro Is
Morro is a native Android alarm app built by a solo founder.
Pitch: "An alarm that won't shut off until you hear yourself promise what you're doing today."

Two layers, two jobs: a puzzle wakes the body, a voice memo in the user's own voice wakes the intention.
Mechanism: implementation intentions + self-consistency. It is a commitment device for the first task of the day. It is NOT a cure for procrastination. Never claim that in code, copy, or UI text.

---

## 2. Hard Rules

- **Reliability beats polish.** An alarm that fails to fire is a fatal bug.
- **Tone:** accountability and commitment. NEVER guilt, shame, or guilt-trip language in any user-facing text.
- **Never merge the gates.** The puzzle and the voice memo are separate layers. Do not combine them or make one depend on the other's UX.
- **Stay in phase.** Do not build or scaffold work from a later phase without explicit instruction.
- **Stop and ask** before writing code if a change touches anything in the out-of-scope list below.
- **Do not edit the "Current phase" line** in this file. I update it myself. Tell me when a phase deliverable is done.

### Strictly OUT of scope (do not build, scaffold, or suggest)
- Accounts, login, auth, cloud sync, or any backend
- Network calls of any kind (the app is 100% offline and local)
- Streaks, stats, history screens, check-ins, or self-reporting
- App blocker, app locking, or screen-time features
- Multiple commitments per day (exactly one)
- Settings beyond alarm time and basic toggles
- Analytics, tracking SDKs, ads, subscriptions, in-app purchases, billing
- iOS or any non-Android platform

---

## 3. Core Loop (v1 MVP)
1. **Trigger:** alarm fires as a full-screen takeover `Activity` over the lock screen, not a passive notification.
2. **Puzzle gate:** user solves a simple math puzzle to dismiss. Purpose: wake the body, block sleep-tapping.
3. **Memo gate:** the commitment recorded the previous morning plays in the user's own voice. The alarm does not fully end until playback completes. No skip, seek, or swipe-past.
4. **Capture next promise:** right after dismissal, while the user is awake and holding the phone, prompt them to record tomorrow's one-line commitment (~10s).
5. **Fallback:** if the user skips recording, replay the most recent existing clip next morning. Never degrade to a plain silent alarm.

---

## 4. Tech Stack and Constraints

- **Language:** Kotlin
- **UI:** Jetpack Compose only. No XML layouts.
- **Min SDK:** API 26 (Android 8.0)
- **Scheduling:** `AlarmManager.setExactAndAllowWhileIdle()`. Reschedule on `BOOT_COMPLETED`.
- **Alarm UI:** full-screen `Activity` launched from a `BroadcastReceiver` via `PendingIntent`. Show over lock screen (`setShowWhenLocked(true)`, `setTurnScreenOn(true)`).
- **Audio playback:** `MediaPlayer` with `AudioAttributes` using `USAGE_ALARM` so it plays on the alarm stream.
- **Audio capture:** `MediaRecorder`. Store files only in app-private storage (`context.filesDir`).
- **Storage:** `SharedPreferences` only (alarm time, current memo path, tomorrow memo path). No Room or SQLite.
- **Dependencies:** none beyond the default Compose/AndroidX set unless I approve.
- **Tooling note:** OmniRoute handles my local API routing/keys for Claude Code. Do not modify, reconfigure, or ask about it.

### Permissions (declare only what the loop needs)
- Exact alarms: declare `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"` and `USE_EXACT_ALARM` for API 33+. Check current Android docs and Play policy before finalizing, because these rules vary by API level and have changed between releases.
- `RECORD_AUDIO` (runtime permission; request when the user first records, with a clear explanation)
- `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`
- If the user denies a permission the loop depends on, show a clear explanation of what will not work. Never fail silently.

---

## 5. Working With Me

- I'm a beginner in Android/Kotlin and learning by building. Explain what each piece of code does, why it exists, and where it goes.
- When more than one approach exists, give the tradeoff in 2-3 lines before choosing.
- Prefer boring, well-documented approaches over clever ones.
- Small steps. One working piece at a time. No large multi-file dumps.
- Do not refactor files I didn't ask about.
- Do not add dependencies without asking and justifying first.
- I'm comfortable with git. Make small, logical commits with clear imperative messages.

---

## 6. Commands (standard Gradle; adjust if the project differs)
- Build debug APK: `./gradlew assembleDebug`
- Install on connected phone: `./gradlew installDebug`
- Lint: `./gradlew lint`
- Unit tests: `./gradlew test`

---

## 7. Testing and Definition of Done
- Test on my real Android phone. Never declare an alarm feature working from an emulator run alone.
- A feature counts as done only after it works with the **screen off and the phone locked**, not just with the app open.
- Useful checks for alarm reliability:
  - Force Doze: `adb shell dumpsys deviceidle force-idle`
  - Inspect scheduled alarms: `adb shell dumpsys alarm`
- Before moving on, tell me exactly how I should verify the step on my phone.

---

## 8. Phase Roadmap

**Current phase:** Weeks 1-2

## Roadmap
**Current phase:** Slice 1 (alarm reliability core)
- Slice 1: exact alarm, full-screen over lock screen, reboot reschedule, alarm sound on alarm stream
- Slice 2: math puzzle, memo record/playback gate, capture tomorrow's memo, fallback
- Slice 3: permission flows, first-run flow, OEM battery guidance, hardening
- Slice 4: release build, store listing, closed test
Goal: closed-test beta live by Nov 5. Reliability over polish.
---

## 9. Known Risks
- **OEM battery optimization** (Samsung, Xiaomi, etc.) killing or delaying scheduled alarms. Biggest technical risk.
- **Skipped recordings:** mitigated by the fallback to the last clip.
- **Habituation** to a repeated clip. Do not try to solve this in v1.

---

## 10. Open Decisions (ask me, don't guess)
- What the alarm tone does during the puzzle and memo phases (ring until the memo ends, or stop at puzzle success)
- Snooze behavior (default assumption: none in v1)
- First-run flow when no memo exists yet
- Whether the user can preview or re-record a memo before saving it
- Puzzle generation logic and difficulty limits
- Package name, app icon, branding
- Single daily alarm vs. day-of-week recurrence in v1

