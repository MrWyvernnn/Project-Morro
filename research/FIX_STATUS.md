# FIX_STATUS — reliability bug-fix pass

One row per finding. Updated as each phase lands.

| Phase | Finding | State | Files touched | Tests | Gaps |
|---|---|---|---|---|---|
| 0 | Recon | **done** | `research/FIX_RECON.md` | — | Finding 1's user-visible outcome needs a device |
| 1 | Lock existing helpers | **done** | `MissedAlarmLogTest.kt` (+3 cases) | `testDebugUnitTest` green | — |
| 2 | F1 locked-boot catch-up | **done (build + unit only)** | `BootReceiver.kt`, `AlarmScheduler.kt`, `AlarmReceiver.kt`, `MissedAlarmLog.kt`, `CatchUpTest.kt` | 13 catch-up/duplicate tests green | the locked-boot race itself still needs the phone |
| 3 | F2 mirror-write guard | **done** | `AlarmBootMirror.kt` KDoc, `AlarmSchedulerContractTest.kt` | 3 contract tests green | absence is asserted at source level; there is no Android runtime in JVM tests |
| 4 | F3 late vs never-fired | **done** | `MissedAlarmLog.kt`, `MainActivity.kt` card copy, `MissedAlarmLogTest.kt` (+6) | 20 log tests green | card copy not seen on a device yet |
| 5 | F4 restore window | **done** | `WakeUpCheck.kt`, `WakeUpCheckTest.kt` (+8) | 13 wake-check tests green | reboot timing still needs the phone |
| 6 | F5 icon · F6A ring buffer | **done** | `res/drawable/ic_morro_notification.xml`, 3 builders, `MissedAlarmLog.kt`, `MissedAlarmLogTest.kt` (+5) | 25 log tests green | 7 writes/event verified by construction and tests, not by measuring the ring path |
| 7 | F6B/F6C report | **reported, nothing deleted** | `research/FIX_STATUS.md` | — | deletion needs your go-ahead |
| 8 | Build + device pass | **build done, device pending** | — | `assembleDebug` + `testDebugUnitTest` + `lintDebug` all green (0 lint errors) | the phone checklist below is unrun |

## Phase 0 verdicts

1. **F1** — invariant violation confirmed in `BootReceiver.rearmAfterUnlock` (rolls to
   tomorrow without checking whether the due occurrence rang). "No ring at all" unverified.
2. **F2** — hazard confirmed; not a live bug. `armWithoutPersisting` does not mirror today.
3. **F3** — confirmed, and `LATE_FIRE` is never displayed by either selection path.
4. **F4** — confirmed; no question-shown key exists.
5. **F5** — confirmed; three framework icons, no `res/drawable/` at all.
6. **F6A** — confirmed; 50 prefs ops per event on the ring path.
7. **F6B** — confirmed dead code, compiled into release, no entry point.
8. **F6C** — confirmed; the Python/JS share is two duplicated skill-pack trees.

## Notes

- The repo gained a `chess/sudoku` gate subsystem after the brief was written. Untouched.
- The competitor research documents named in §1 do not exist in this repo.
- No new dependency was added; `junit:junit:4.13.2` remains the only test dependency.

## 6B — `BatteryOptimizationDebugActivity` is dead (nothing deleted)

Verified again by search, not from the earlier report. Seven references in the whole repo: the
manifest entry (`AndroidManifest.xml:65`), the class KDoc and declaration
(`BatteryOptimizationDebugActivity.kt:26,30`), and four lines in `FIX_RECON.md`. **No
`startActivity`, no `PendingIntent`, no click handler, no reflection, no alias.** It sits in
`src/main`, so it ships in release, and no `BuildConfig.DEBUG` guard touches it.

The real, reachable screen is `OemGuidanceActivity` (`MainActivity.kt:454`, `MainActivity.kt:577`,
`OnboardingActivity.kt:138`), whose card lives in `ui/components/OemGuidanceCard.kt:38`. The debug
activity duplicates the same `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` flow that `OnboardingActivity`
and `OemGuidance` already own.

**Left in place deliberately.** Deleting it is two lines of manifest plus one file, one revert away,
but the instruction was to ask before deleting. Waiting on your call.

## 6C — repo composition

Counted again, excluding build output:

| Ext | Count | Where |
|---|---|---|
| `.kt` | 93 | 67 `app/src/main`, 20 `app/src/test`, 6 inside skill packs |
| `.xml` | 16 | 11 `.idea/`, 5 `app/src/main/res` |
| `.md` | 664 | 329 `.claude/skills`, 329 `.freebuff/skills`, 2 `research/`, 2 `design/`, 2 root |
| `.py` | 66 | 32 + 32 skill packs, 2 `tools/` |
| `.js` | 7 | 3 + 3 skill packs, 1 `workflows/scripts` |
| `.csv` | 106 | 53 + 53 skill packs |

`.claude/skills` and `.freebuff/skills` are byte-identical 24-skill trees, which is where ~98% of the
`.md`/`.py`/`.csv`/`.js` count comes from. Real app source is 93 `.kt` + 5 `.xml` + 3 `.kts` + 2 `.gz`.
No root `README.md`. `mcp.json` is 0 bytes. `.claude/worktrees/` is empty.
`workflows/scripts/ultracode-morro-hotfix-wf.js` is referenced by nothing.

**Nothing deleted.** The Python in `tools/` and the JS in `workflows/` are yours; say the word and
they go, otherwise they stay.

## 8 — device checklist (nothing here is claimed as passing)

Run on the Nothing Phone with the screen off and the phone locked. `adb devices` was empty here, so
every item below is **unverified**.

### A. Finding 1 — locked boot, late unlock (the one that matters)

1. Set the alarm ~10 minutes out. `adb shell dumpsys alarm | findstr /i morro` → one `RTC_WAKEUP`
   alarm with `alarmClock=`.
2. `adb reboot`. **Do not unlock.** Wait until at least 3 minutes past the alarm time.
3. Unlock. Expect the alarm to ring within ~2 s of the unlock, full screen over the lock screen.
   If it does not ring at all, the platform dropped the deferred broadcast and the catch-up did not
   fire — that is the case this fix exists for, and it would be a real failure.
4. Solve the mission and dismiss. Then `dumpsys alarm` must show tomorrow's alarm armed.
5. Repeat, but unlock **before** the alarm time. Expect exactly one ring at the set time, and no
   second ring a couple of seconds later. Logcat must not show
   `Duplicate delivery ... not ringing again` at the normal ring.
6. Repeat with the phone left locked for a whole day so two occurrences are missed. Expect **one**
   catch-up ring, not two: after the first catch-up the occurrence identity is written, so the
   second pass must stay quiet.

### B. Finding 1 — duplicate guard (indirect only)

The receiver is `exported="false"`, so a shell broadcast cannot reach it; there is no adb test for
this. What to watch instead: during A.5 and A.6, logcat must not show two
`(2) Broadcast received` sequences that both reach `AlarmActivity`. The identity logic itself is
covered by `CatchUpTest`.

### C. Finding 3 — "rang late" vs "didn't ring"

1. Late: set an alarm ~3 minutes out, then
   `adb shell dumpsys deviceidle force-idle` so Doze holds the ring past the minute threshold.
   Dismiss it, open Morro. The card must read **"Your 7:00 AM alarm rang at 7:04 AM."**
   (`adb shell dumpsys deviceidle unforce` afterwards.)
2. Never fired: set an alarm ~2 minutes out, then `adb shell am force-stop com.morro.alarm` and wait
   past the time. Open Morro. The card must read **"Your 7:00 AM alarm didn't ring on time."**
3. Both cards must keep the same dismiss button and the same "Show me how to fix this" action.

### D. Finding 4 — wake-up check across a reboot

1. Dismiss an alarm, then reboot within the 3-minute wait. After unlock the "Still up?" question must
   **not** appear immediately; it must appear at the original 3-minute mark.
2. Dismiss an alarm, wait for the question, then reboot while it is on screen. After unlock the
   question comes back and the re-ring still happens at the original 90-second deadline — not a fresh
   90 seconds later.
3. Answer "I'm up" after either reboot. No re-ring.

### E. Finding 5 — icon

1. Trigger the alarm: the status bar shows the ghost silhouette, not a system warning triangle.
2. The "Still up?" notification shows the same ghost.
3. Both are legible at status-bar size and are tinted by the system, not drawn with a coloured box.

### F. Finding 6A — storage change on upgrade

Install the new build **over** a build that already has a logged miss, then open Morro. The card must
still be there: the v1 slots are migrated on first read, not dropped. Logcat shows
`Migrated N legacy ring events to the head-pointer layout` exactly once.

### G. Unchanged behaviour that must still hold

1. Reboot before the alarm time, unlock before it → normal ring at the set time.
2. `adb shell dumpsys deviceidle force-idle` with the phone locked → the alarm still rings.
3. Snooze once → rings again ten minutes later, and only once.
4. Force-stop then reopen → the dashboard still shows the armed summary and the `+` builder.


