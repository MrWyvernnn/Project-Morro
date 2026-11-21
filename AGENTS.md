# Morro — Project Briefing

Read this first. Then `design/MORRO_VISUAL_SYSTEM.md` for anything visual.
This file is the product truth. That file is the design truth. Code wins over both
when they disagree — report the drift, don't silently "fix" working UI.

## 1. What Morro is

A native Android alarm app, built solo.

**Pitch:** "An alarm that won't shut off until you hear yourself promise what you're
doing today."

Two layers, two jobs: a **mission** wakes the body, a **voice memo** in the user's own
voice wakes the intention. Mechanism is implementation intentions + self-consistency.
It is a commitment device for the first task of the day. It is **not** a cure for
procrastination — never claim that in code, copy, or UI.

**Character:** calm, warm, a little stubborn. Never guilt, never shame.

## 2. Where the project is right now

**App — Slice 3, in progress.**
- Alarm engine complete: `setAlarmClock`, full-screen takeover over lock screen,
  boot reschedule, foreground service, alarm-stream audio.
- Three selectable missions: **Voice Commitment**, **QR / Barcode**, **Object Photo Hunt**.
  Math runs *inside* the voice mission — it is not a fourth pickable gate.
- Voice memo: 15s cap, `MediaRecorder`, MPEG_4/AAC, 16kHz mono, app-private storage.
  Fallback replays the last clip if the user skips recording.
- Onboarding: ghost splash → questions → personalizing → permissions → dashboard.
- UI: warm monochrome design system fully applied. Dial, mascot, clouds, bento dock,
  anchored sheets, motion tokens.

**Website — live.**
- Repo: `MrWyvernnn/webdev-landing-page-morro` (separate from this one).
- URL: https://morrolandingpage.marhaanansari.workers.dev
- Vite + React + Tailwind v4 + Motion + Lenis, served as Cloudflare Workers static assets.
- Waitlist: Cloudflare Worker + D1 + Resend, double opt-in, rate limited, honeypot.
- Copy is aligned with the app: three gates, no fabricated pricing, no free-app claim.

**Monetization — decided, not yet built.**
- Going paid via **Google Play Billing**.
- **No custom accounts.** Play Billing ties entitlement to the Google account already
  on the device. A sign-in screen buys nothing and costs an account-deletion flow
  under Play policy. `SignInScreen` should not be routed.
- Voice memos, alarms and mission data stay **local-only**. Purchases are the only
  network touch, and Google handles them.

## 3. Hard rules

- **Reliability beats polish.** An alarm that fails to fire is a fatal bug.
- **Never merge the gates.** Mission and voice memo are separate layers.
- **Don't touch working UI.** If it renders and behaves, leave it. Propose, don't refactor.
- **Stay in phase.** No scaffolding later-phase work without explicit instruction.
- **Stop and ask** before touching anything out of scope below.
- Don't edit the "Current phase" line in §7. The founder updates it.

### Out of scope — do not build, scaffold, or suggest unless i myself tell you to do it
- Custom accounts, auth, your own backend, or a user table
- Third-party payment SDKs (Play Billing only)
- Cloud sync of voice memos, alarms, or mission data
- Analytics, tracking SDKs, ads
- Streaks, stats, history screens, check-ins, self-reporting
- App blocker / app locking / screen-time features
- Multiple commitments per day (exactly one)
- iOS or any non-Android platform

### Permitted network
Play Billing only. Everything else in the app is offline and local.

## 4. Tech stack

- Kotlin, Jetpack Compose only. No XML layouts.
- minSdk 26 (Android 8.0), target 34.
- `AlarmManager.setAlarmClock()`, reschedule on `BOOT_COMPLETED`.
- Full-screen `Activity` from a `BroadcastReceiver` via `PendingIntent`;
  `setShowWhenLocked(true)`, `setTurnScreenOn(true)`.
- `MediaPlayer` with `AudioAttributes.USAGE_ALARM`.
- `MediaRecorder` for capture; files in `context.filesDir` only.
- `SharedPreferences` only. No Room, no SQLite.
- Deps: Compose/AndroidX + CameraX + ML Kit (barcode scanning, image labeling).
  **No new dependencies without asking and justifying.**
- System fonts only. No font assets ship, nothing is fetched.

### Permissions
`SCHEDULE_EXACT_ALARM` (≤32), `USE_EXACT_ALARM` (33+), `RECORD_AUDIO` (runtime, explained
at first record), `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`POST_NOTIFICATIONS`. If a denied permission breaks the loop, say so plainly. Never fail silently.

## 5. Where things live

```
app/src/main/kotlin/com/morro/alarm/
├── MainActivity.kt              intro routing + dashboard + alarm dock
├── AlarmScheduler.kt            setAlarmClock, boot reschedule
├── AlarmReceiver.kt             broadcast → full-screen activity
├── AlarmForegroundService.kt    keeps the alarm alive
├── AlarmSoundPlayer.kt          alarm-stream ringtone
├── MemoRecorder.kt              15s capture state machine
├── MemoStorage.kt               temp → saved promotion, fallback
├── mission/
│   ├── MissionModels.kt         MissionType, PhotoTargetPool (30 items)
│   └── MissionPrefs.kt          persisted mission selection
└── ui/
    ├── theme/Theme.kt           MorroColors, MorroShapes, MorroTypography, MorroSpring
    ├── motion/MorroMotion.kt    durations, easings, stagger, rubber-band
    ├── components/              MorroCard, MorroPrimaryButton, MorroAnalogClock,
    │                            MorroGhostMascot, DriftingClouds, AlarmBuilderSheet,
    │                            MorroSwitch, MorroStatusDot, sheets
    ├── onboarding/              splash, questions, personalizing, permissions
    └── splash/MorroGhostSplash.kt

design/MORRO_VISUAL_SYSTEM.md    the design authority
design/design.md                 skill routing — read before any UI work
```

## 6. Commands

```bash
./gradlew assembleDebug     # build
./gradlew installDebug      # install on connected phone
./gradlew lint
./gradlew test
```

Reliability checks:
```bash
adb shell dumpsys deviceidle force-idle   # force Doze
adb shell dumpsys alarm                   # inspect scheduled alarms
```

## 7. Definition of done

A feature is done only when it works with **the screen off and the phone locked**, on a
real device. An emulator run alone never counts. Before moving on, state exactly how the
founder should verify it on their phone.

**Current phase:** Slice 3 (first-run flow, OEM hardening, UI overhaul)

- Slice 1 — alarm engine, lock-screen takeover, reboot reschedule ✅
- Slice 2 — missions, memo record/playback gate, capture, fallback ✅
- Slice 3 — onboarding, permissions, OEM guidance, warm-monochrome UI 🔄
- Slice 4 — Play Billing, release build, Play listing, closed test ⏳

## 8. Working with the founder

- Beginner in Android/Kotlin, learning by building. Explain what code does, why it
  exists, and where it goes.
- When there's more than one approach, give the tradeoff in 2–3 lines, then choose.
- Boring and well-documented over clever.
- Small steps. One working piece at a time. No large multi-file dumps.
- Don't refactor files that weren't asked about.
- Small logical commits, imperative messages.

## 9. Known risks

- **OEM battery optimization** (Samsung, Xiaomi) killing scheduled alarms. Biggest
  technical risk. Mitigated by `setAlarmClock` + foreground service + OEM guidance.
- **Skipped recordings** → fallback to last clip.
- **Habituation to a repeated clip** → do not solve in v1.
- **Doc drift** → `AGENTS.md` still describes an orange/pink palette that was abandoned.
  `MORRO_VISUAL_SYSTEM.md` and `Theme.kt` are correct. Follow those.

## 10. Open decisions — ask, don't guess

- OEM battery guidance wording and flow
- Final app icon and branding assets
- Snooze in v1 (default assumption: none)
- Play Billing model: one-time purchase vs subscription
- Price point
