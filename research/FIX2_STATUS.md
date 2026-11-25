# FIX2_STATUS — gate audio, theme recolour, contrast, stale APIs

One row per phase. A phase is only "done" when its tests pass; device items are listed in §Device
and are unverified until the founder runs them.

| Phase | Finding | State | Files touched | Tests | Gaps |
|---|---|---|---|---|---|
| 0 | Recon | **done** | `research/FIX2_RECON.md` | — | F3 has no call-site list to fix; it is refuted as described |
| 1 | F1 restore on exit | **done (build + unit only)** | `GateSilenceController.kt`, `AlarmActivity.kt`, `GateSilenceControllerTest.kt` (+5, 1 rewritten) | 11 gate tests green | the throwing-callback exit needs the phone; no JVM Android runtime |
| 2 | F2 duck + hard deadline | **done (build + unit only)** | `GateSilenceController.kt`, `AlarmActivity.kt`, `GateSilenceControllerTest.kt` (+8) | 19 gate tests green | rotation/process-death persistence is wired through the activity bundle but only the pure restore path is tested |
| 3 | F5 contrast + test | **done** | `Theme.kt` KDoc, `MorroPaletteTest.kt` (+1) | 8 palette tests green | Autumn 11sp text not yet read on the phone; value is 5.59:1 on canvas, 5.02:1 on a card |
| 4 | F3 recolour pass | **closed — zero sites** | none | — | re-verified: no `remember`/`Animatable`/top-level `val` captures a palette colour; nothing to fix |
| 5 | F4 guarded palette write | **done** | `Theme.kt`, `MorroPaletteTest.kt` (+1) | 9 palette tests green | first-frame timing needs the phone |
| 6 | F6 stale APIs | **done** | `Theme.kt`, `MorroCard.kt`, `design/MORRO_VISUAL_SYSTEM.md`, `design/design.md` | `testDebugUnitTest` green | no behaviour change expected; build only |
| 7 | Scope audit | **done (in recon)** | `research/FIX2_RECON.md` §8 | — | — |
| 8 | Build + device pass | **build done, device pending** | — | `assembleDebug` + `testDebugUnitTest` (233 tests) + `lintDebug` (0 errors, 66 warnings) all green | the checklist below is unrun |
| 9 | P0 reattach reconciliation | **done** | `GateSilenceController.kt`, `AlarmActivity.kt`, `GateSilenceControllerTest.kt` (+4) | 23 gate tests green | a real rotation also passes through `onPause`, so the stuck-ducked path was already masked; the controller no longer depends on that |
| 10 | One duck level | **done** | `GraceWindow.kt`, `GraceWindowTest.kt` (+1), `AlarmActivity.kt` copy | grace + gate tests green | 30% chosen over 20%; needs a listen on the phone |

## Decisions

- **F1** is fixed in the controller (`resetImmediate` and `checkWatchdog(false)` emit full volume
  before clearing, guarded so a never-silenced controller emits nothing), plus a
  `DisposableEffect(Unit) { onDispose { restoreVolumeUnconditional() } }` in `AlarmScreen` so a
  gate that exits by exception still restores. The existing `onPause`/`onDestroy` restores stay.
  `isTearingDown` stops that dispose-time restore from taking the "player stopped unexpectedly"
  branch in `rampPlayerVolume` and restarting the alarm after `onDestroy` has stopped it.
- **F2** persists `hardRestoreAtMs` and the spent flag in `onSaveInstanceState`, next to the puzzle
  seed/question, because that is the state that survives rotation and process death. The controller
  takes them as constructor input plus a `restoreBudget(...)` call, and `startSession()` computes
  the deadline once (guarded on `0`).
- **F4** keeps the composition-body publish — moving it to `SideEffect` reintroduces the
  first-frame Midnight flash — and guards it with
  `if (MorroColors.currentChoice != choice) MorroColors.use(choice)`. No `Application` subclass was
  added because none exists and a manifest entry plus a class is a poor trade for a cold-start
  nicety that the composition-body write already solves.
- **F3** was refuted by recon: no `remember`-cached, `Animatable`-seeded, or top-level palette
  values exist. Nothing was changed speculatively.
- **F5** does not change a hex: Autumn tertiary already measures 5.59:1 on the canvas and 5.02:1 on
  a card. The stale TODO is replaced with the measured numbers and a named test.
- **P0 reattach** saves `isSilenced` with the deadline and spent flag, and `restoreBudget` emits the
  matching target **unconditionally** so a fresh controller and the surviving player agree. The
  `isSilenced` guard stays on steady-state restores. Note: a real rotation also runs `onPause`,
  which restores full volume before the bundle is written, so the saved flag is normally `false`;
  the fix removes the controller's reliance on that ordering rather than changing today's path.
- **One duck level**: `GateSilenceController.DUCKED_VOLUME` (0.30) is the only value either writer
  uses; `GraceWindow.VOLUME` references it and the banner renders the percentage from the constant.
  The two authorities are strictly exclusive — grace runs only in `MISSION_QR`/`MISSION_PHOTO`,
  the controller only in `PUZZLE`, and `onMissionComplete` cancels the grace job before
  `PLAYING_MEMO` — so no ownership refactor was needed, only one shared number.

## Device

See §8 below. Nothing there is claimed as passing. `adb devices` was empty here.

---

## 7 · Scope audit — result

Reported in `FIX2_RECON.md` §8. Short version:

- No Gradle dependency was added by `e0d1d07` (the commit does not touch `app/build.gradle.kts`).
- No Room, SQLite, OkHttp, Retrofit or kotlinx.serialization in `puzzle/`.
- Puzzles are two gzipped CSVs (`mate_in_1.csv.gz` 3,229 B, `mate_in_2.csv.gz` 3,261 B), with a
  Lichess CC0 `LICENSE.txt` already beside them.
- `CognitivePrefs.getChallenge()` defaults to **MATH**, so existing users' gates did not change
  behaviour.
- `MissionType` still has exactly six values in three alias pairs; `mission/` was untouched.

## 8 · device checklist (nothing here is claimed as passing)

Run on the Nothing Phone. Install with `./gradlew installDebug`.

### A. F2 — the duck is audible and the exploit fails

1. Ring the alarm on the MATH gate (`TEST 5S` in a debug build, or set an alarm 1 min out).
2. Tap one answer. The alarm must become **noticeably quieter but still audible** — a duck, not
   silence. Before this pass it went fully silent.
3. Stop touching the screen. At **15 s** the alarm ramps back to full.
4. The exploit test: ring again, tap once, then tap one answer roughly every 14 s for 3 minutes.
   At the **60 s** mark the alarm must jump to full volume and **stay there** while you keep
   tapping. If it gets quiet again, the deadline is broken.
5. Rotate the phone once during step 4 (around 30 s). After rotation the alarm may be loud (the
   pause path restores it), but the 60 s deadline must still be the original one — it must go loud
   by 60 s from the gate opening, not 60 s after the rotation.
6. `adb shell dumpsys audio` before and after the whole sequence. `STREAM_ALARM`'s index must be
   identical. Nothing in this pass may change the system stream.

### B. F1 — every exit is audible

1. Ring, tap so the alarm ducks, then pull the notification shade (or press Home) and come back.
   On return the alarm must be at full volume.
2. Ring with the CHESS gate, tap a piece so it ducks, then answer wrongly. The alarm keeps
   ringing at the ducked level (by design) and still ramps to full after 15 s idle.
3. Ring, duck, then solve the gate. The commitment memo must play normally and the next
   `TEST 5S` ring must start at full volume — no residual mute.
4. Ring, duck, and force-stop the app (`adb shell am force-stop com.morro.alarm`). Reopen and
   trigger `TEST 5S`: it must ring at full volume. (A new process resets the player volume; this
   checks there is no persisted mute.)

### C. F3 / F4 — live theme switch and cold start

1. On the dashboard, open Settings and switch **Midnight → Autumn**. The dial arc, the mascot's
   accent, the sky decoration (clouds → leaves), the dock and any animation caught mid-flight
   must all recolour **without** leaving the screen. If any surface keeps the old honey colour,
   note which one — recon found no cached colour site, so that would be new information.
2. Switch back to Midnight. Same checks.
3. Force-stop, then cold-start the app while Autumn is stored. The first frame must be Autumn —
   no one-frame Midnight flash on the splash or dashboard.
4. Repeat the live switch with the settings sheet open and with the alarm builder open.

### D. F5 — Autumn small text

1. On Autumn, read the 11–12sp metadata: the dashboard's "Problem 1 of 3"-style labels, the
   settings sheet's helper lines, the alarm builder's captions.
2. Measured ratios: tertiary 5.59:1 on black, 5.02:1 on a card. Confirm it is comfortably legible
   at arm's length in a dim room.
3. Ring on the QR mission and read both banner states: "Complete mission before volume increases"
   and, after 30 s, "Alarm is sounding at full volume". Both are now secondary-weight copy.
4. On the voice-commitment gate, read the "Skip (Use yesterday's memo)" link. It should be quieter
   than the honey record button and still readable.

### E. F6 — no visible change

1. Tap a card: the focus/press ring still animates in and out. The parameter removed in F6 was
   never applied, so nothing should look different.

### F. Still outstanding from the previous pass (`0b11e40`)

The locked-boot catch-up ring cannot be verified any other way:

1. Set the alarm ~3 min out. `adb shell dumpsys alarm | findstr /i morro` → one `alarmClock=`.
2. `adb reboot`. **Do not unlock.** Wait past the trigger +2 min.
3. Unlock. It must ring **exactly once**, full screen. Then `dumpsys alarm` shows tomorrow armed.
4. Unlock before the trigger instead: exactly one ring at the set time, no second ring 2 s later.

### G. Grace window — deadline, not ticks (after the review)

1. Ring on the QR or Photo mission. The banner counts 30 → 0 and the alarm is ducked to 30% (the
   same level the cognitive gate uses), then it jumps to full at the 30 s mark. Time it with a
   stopwatch: it must be 30 s, not 31 or 32.
2. Lock the phone for 20 s during the window, unlock, and confirm the remaining seconds are roughly
   where the clock says they should be — the window is real time, not screen-on time.
3. Rotate during the window. Expected current behaviour: the banner restarts at 30 s even though
   `onPause` restored full volume. That is the known rotation gap in §9, not a regression.
4. P0 reattach, on the cognitive gate: ring, tap once so it ducks, rotate, then stop touching the
   screen. The alarm must come back to full (15 s idle or 60 s hard at the latest) — it must never
   stay at 30% with a fresh controller that thinks it is loud.

## 9 · drift

### Fixed after the review (commit follows §1–8)

- `AlarmActivity.startGraceCountdown()` no longer counts with `delay(1000L)`. The window is an
  absolute `SystemClock.elapsedRealtime()` deadline (`GraceWindow.kt`), the loop wakes at most once
  a second and never past the deadline, and a late tick shortens the next sleep instead of
  stretching the window. Covered by `GraceWindowTest` (7 tests).
- The two live strings that used `textDisabled` are now legible: the grace banner's status line is
  `textSecondary` (6.18:1 Midnight / 6.85:1 Autumn on `surfaceHigh`; 5.60:1 / 7.22:1 on the 20%
  error wash) and the Skip button is `textTertiary` (5.30:1 / 5.59:1 on the canvas, still quieter
  than the record button). `MorroPaletteTest.statusCopyClearsAaOnTheGraceBanner` pins the banner
  grounds. `textDisabled` itself is unchanged and still measures 3.59:1 / 3.73:1 — fine for its
  name, and no longer used for live copy.

### Still open, reported only

- The grace window does not survive a rotation. `onPause` restores the ring to full volume and the
  recreated activity starts with `missionGraceSeconds = 30` and no running `graceJob`, so the banner
  can claim a muted window that is not muted. The deadline would need to travel in
  `onSaveInstanceState`, the way the gate's silence budget now does. Not changed — it predates this
  pass and was not part of the requested fix.
- Lint still warns `Wakelock` (released in `onDestroy` rather than `onPause`) and `ObsoleteSdkInt`
  in `AlarmActivity`. Both predate this pass.

