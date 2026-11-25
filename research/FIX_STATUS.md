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
| 6 | F5 icon · F6A ring buffer | **F5 done** | `res/drawable/ic_morro_notification.xml`, 3 notification builders | `assembleDebug` green | glyph not seen in a real status bar yet |
| 7 | F6B/F6C report | pending | — | — | — |
| 8 | Build + device pass | pending | — | — | needs the phone |

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
