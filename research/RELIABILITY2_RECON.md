# Reliability 2 + Puzzle Upgrades — Phase 0 Recon

Date: 2026-11-27
Scope: Inspection only — no production source code modified in Phase 0.

---

## 1. `app/src/main/AndroidManifest.xml`

### Ringing Service Declaration (`AndroidManifest.xml:130-133`)
```xml
<service
    android:name=".AlarmForegroundService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```
- `android:stopWithTask`: **NOT declared** (missing explicit `android:stopWithTask="false"`).
- `android:foregroundServiceType`: `"mediaPlayback"`.
- `android:exported`: `"false"`.

### Ringing Activity Declaration (`AndroidManifest.xml:76-88`)
```xml
<activity
    android:name=".AlarmActivity"
    android:exported="false"
    android:showWhenLocked="true"
    android:showForAllUsers="true"
    android:turnScreenOn="true"
    android:launchMode="singleTask"
    android:excludeFromRecents="true" />

<activity-alias
    android:name=".AlarmRingingActivity"
    android:targetActivity=".AlarmActivity"
    android:exported="false" />
```
- `android:excludeFromRecents`: `"true"`.
- `android:showWhenLocked`: `"true"`.
- `android:turnScreenOn`: `"true"`.
- `android:launchMode`: `"singleTask"`.
- `android:taskAffinity`: **NOT declared** (inherits package default affinity, meaning `singleTask` can still share affinity with `MainActivity` unless isolated or verified).

### Volume / Foreground / Ringing Permissions (`AndroidManifest.xml:4-34`)
- `android.permission.SCHEDULE_EXACT_ALARM` (`android:maxSdkVersion="32"`)
- `android.permission.USE_EXACT_ALARM`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `android.permission.DISABLE_KEYGUARD`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.VIBRATE`
- `android.permission.USE_FULL_SCREEN_INTENT`
- `android.permission.WAKE_LOCK`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `android.permission.RECORD_AUDIO`, `android.permission.CAMERA`
- No `MODIFY_AUDIO_SETTINGS` permission declared.

---

## 2. `AlarmForegroundService.kt`

- `onStartCommand(intent: Intent?, flags: Int, startId: Int): Int` (`lines 34-68`):
  - Calls `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` on `SDK_INT >= Q` (with plain `startForeground(NOTIFICATION_ID, notification)` retry fallback).
  - Returns **`START_NOT_STICKY`** (`line 67`) when promoted, `START_NOT_STICKY` (`line 64`) on promotion failure. (**Missing `START_REDELIVER_INTENT`**.)
- `onTaskRemoved`: **MISSING** (no override present).
- `onDestroy()` (`lines 70-73`): calls `releaseWakeLock()`, `super.onDestroy()`.
- WakeLock (`lines 115-124`): `PowerManager.PARTIAL_WAKE_LOCK` tagged `"morro:alarm_wakelock"`, `acquire(10 * 60 * 1000L)`.
- Notification (`lines 92-113`): `CHANNEL_ID = "morro_alarm_service"`, `NOTIFICATION_ID = 1002`, `NotificationManager.IMPORTANCE_HIGH`, `setSmallIcon(R.drawable.ic_morro_notification)`, `setFullScreenIntent(fullScreenPendingIntent, true)` (PendingIntent requestCode `2002`), `setOngoing(true)`.
- Volume watchdog / persisted ringing flag (`isAlarmRinging`, `alarmId`, `ringFloorVolume`): **MISSING**.

---

## 3. `AlarmActivity.kt`

- `onCreate` window/lockscreen flags (`lines 181-233`):
  - `enableEdgeToEdge(...)`
  - API `< 27`: `WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or FLAG_TURN_SCREEN_ON or FLAG_KEEP_SCREEN_ON`
  - API `>= 27`: `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)`
  - API `>= 26`: `KeyguardManager.requestDismissKeyguard(...)`
  - `PARTIAL_WAKE_LOCK` tagged `"morro:alarm_active"`, `acquire(10 * 60 * 1000L)`.
- Key interception (`dispatchKeyEvent` / `onKeyDown`): **MISSING** (only `@Deprecated override fun onBackPressed()` at `lines 694-696` and `BackHandler(enabled = true) { ... }` at `line 774`).
- Active ring state (`lines 96-102`, `253-270`, `445-537`):
  - `enum class AlarmGateState { PUZZLE, MISSION_QR, MISSION_PHOTO, PLAYING_MEMO, RECORDING_TOMORROW }`
  - Ring is active while `gateState` is `PUZZLE`, `MISSION_QR`, or `MISSION_PHOTO` (i.e. before `completePuzzle()` or `onMissionComplete()` stops `AlarmSoundPlayer`, and while `!isTearingDown`).
- Gate exit paths:
  - `submitAnswer` / `ChessBoardView(onPuzzleSolved)` / `SudokuGateScreen(onGateSolved)` -> `completePuzzle()` (`line 445`) -> stops `AlarmSoundPlayer`, transitions `gateState = AlarmGateState.PLAYING_MEMO` -> `promptForTomorrowMemo()` -> `RECORDING_TOMORROW` (or `finishAlarm()` directly if `isWakeUpCheckRing`).
  - `QrMissionScreen(onCodeScanned)` / `PhotoMissionScreen(onMissionSuccess)` -> `onMissionComplete()` (`line 481`) -> stops `AlarmSoundPlayer`, calls `promptForTomorrowMemo()`.
  - `stopRecordingTomorrow()` (`line 604`) / `skipTomorrowMemo()` (`line 617`) -> `finishAlarm()` (`line 500`) -> `cancelAlarm()`, `MissedAlarmLog.recordCompleted`, `rearmNextWakeUp()`, `WakeUpCheck.arm`, `finishAndRemoveTask()`.

---

## 4. `AlarmSoundPlayer.kt` & Audio Watchdogs

- Single `private var mediaPlayer: MediaPlayer? = null` (`AlarmSoundPlayer.kt:33`), configured with `AudioAttributes.USAGE_ALARM` / `CONTENT_TYPE_SONIFICATION`.
- `fun setVolume(volume: Float)` (`lines 116-125`) and `fun getVolume(): Float` (`line 130`) apply player-local stereo gain `[0f..1f]` via `mediaPlayer?.setVolume(clamped, clamped)`.
- Both `GraceWindow` (`VOLUME = GateSilenceController.DUCKED_VOLUME = 0.30f`, 30s monotonic `elapsedRealtime` deadline) and `GateSilenceController` (`DUCKED_VOLUME = 0.30f`, 15s inactivity timeout, 60s hard restore deadline) drive `AlarmActivity` -> `AlarmSoundPlayer.setVolume(...)`.
- Repo grep for `setStreamVolume`: **0 occurrences** across `app/src/main/kotlin` (`AlarmActivity.kt:354` explicitly comments `Never calls AudioManager.setStreamVolume.`).

---

## 5. `AlarmScheduler.kt` & Request Codes In Use

| Request / Notification Code | Symbol & File | Purpose |
|---|---|---|
| `1001` | `AlarmScheduler.ALARM_REQUEST_CODE` | Daily alarm, snooze, re-arm, locked-boot `armWithoutPersisting` |
| `1002` | `AlarmForegroundService.NOTIFICATION_ID` | Foreground service notification + fallback notification |
| `1003` | `WakeUpCheck.REQUEST_CHECK` | Wake-up check notification (`ACTION_WAKE_CHECK`) + `"I am up"` action |
| `1004` | `WakeUpCheck.REQUEST_ALERT` | Wake-up escalation re-ring (`ACTION_WAKE_CHECK_ALERT`) |
| `1005` | `WakeUpCheck.NOTIFICATION_ID` | Wake-up check notification |
| `1006` | `AlarmScheduler.CATCH_UP_REQUEST_CODE` | Locked-boot missed-ring catch-up (`ACTION_TRIGGER_ALARM` + `EXTRA_OCCURRENCE`) |
| `2002` | `AlarmForegroundService.kt:98`, `AlarmReceiver.kt:179` | `fullScreenPendingIntent` launching `AlarmActivity` |
| `9999` | `MainActivity.kt:1060` | Debug `"Test alarm in 5s"` (`ACTION_TEST_ALARM`) |
| **`1007`** | **Unused** | Available for Fix 2 `onTaskRemoved` recovery trigger (`AlarmManager.setAlarmClock`) |

---

## 6. Quiet-Volume Check (Spec §1F)

- `AlarmVolumeGuard.kt:13-16`:
  ```kotlin
  fun isTooQuiet(volume: Int, max: Int): Boolean {
      if (max <= 0) return false
      return (volume.coerceAtLeast(0).toFloat() / max.toFloat()) < 0.4f
  }
  ```
- Read in `AlarmVolumeGuard.readAlarmVolume(context: Context): AlarmVolumeSnapshot` (`lines 35-47`) and exposed via `isAlarmTooQuiet(context)`.
- Used on dashboard banner (`MainActivity.kt:476`) AND at arm time (`AlarmBuilderSheet.kt:625-632`, `Toast.makeText` warning without modifying volume).
- Tested in `AlarmVolumeGuardTest.kt` (1 test, multiple cases).
- **Status: ALREADY SHIPPED & VERIFIED.**

---

## 7. `puzzle/chess/` & Chess Board UI

- Files in `app/src/main/kotlin/com/morro/alarm/puzzle/chess/`:
  - `ChessBoard.kt` (`Color`, `PieceType`, `Piece`, `Square`, `CastlingRights`, `ChessBoard`)
  - `ChessMove.kt` (`ChessMove`)
  - `ChessRules.kt` (`ChessRules` — full legal moves, check, checkmate, `isForcedMateInTwo`, `hasMateInOne`)
  - `FenParser.kt` (`FenParser`)
  - `ChessEngine.kt` (`ChessEngine` — alpha-beta + `findForcedMate` + `pickSmartReply` + `generateTacticalPuzzle`)
  - `PuzzleRecord.kt` (`PuzzleRecord`)
  - `PuzzleRepository.kt` (`CognitiveDifficulty`, `PuzzleRepository` with `mate_in_1.csv.gz` & `mate_in_2.csv.gz` + fallback generator)
- Assets (`app/src/main/assets/puzzles/`):
  - `mate_in_1.csv.gz`: **24,999 rows**, columns `['PuzzleId', 'FEN', 'Moves', 'Rating', 'Themes']`, min rating `621`, max rating `1149` (**2,531 rows** in `1100..1600`).
  - `mate_in_2.csv.gz`: **24,999 rows**, same 5 columns, min rating `1003`, max rating `1649` (**14,640 rows** in `1300..1800`).
  - `LICENSE.txt`: Lichess CC0 attribution.
  - `tools/filter_lichess.py`: filters CSV by theme & old rating bounds (`600..1200` and `1000..1500`) **without** programmatic move/checkmate verification.
- UI (`ui/puzzle/ChessBoardView.kt`, `ui/puzzle/ChessPieces.kt`):
  - Pieces are Canvas `Path` vectors (`ChessPieces.drawPiece` / `createPiecePath`, 6 silhouettes, zero font glyphs, zero image assets).
  - **Gap vs §5 spec:** Currently styled in Chess.com Classic Green (`#769656`/`#EEEED2`/`#F6F669`) with `#262421` dark chrome, `#CA3431`/`#81B64C` feedback banners, and drag-and-drop gestures (from an earlier request), whereas spec §1D / §5B–E / §7 requires warm maple (`#E8D5B0`) / walnut (`#8B6242`) with procedural wood grain & `#5C4030` 2dp inset frame, `#E8C58F` honey selection/last-move, `#3A2A1C` legal dots, `#C4553B` check ring, tap-to-select + tap-to-destination only (NO drag-and-drop), Morro monochrome chrome outside the board, and haptic + horizontal shake with no error text / color flash.
  - Also note: spec §5A states Standard passes on ANY legal move that delivers mate (`isCheckmate`), whereas `ChessBoardView.kt:421` currently also accepts `expectedUci` or `isCheckmate` and on Hard validates `Moves[1]` / `isForcedMateInTwo`; verify alignment in Phase 4 and keep `Moves[0]` 600ms setup highlight + one-line helper on screen.

---

## 8. `puzzle/sudoku/` & Sudoku Grid UI

- Files in `app/src/main/kotlin/com/morro/alarm/puzzle/sudoku/`:
  - `SudokuGrid.kt` (`SudokuGrid`, `conflicts`, `isComplete`, `isSolved`)
  - `SudokuSolver.kt` (`countSolutions(grid, limit)`, `solve`, `isLogicallyDeducible`, `candidatesFor`)
  - `SudokuGenerator.kt` (`SudokuMode`, `SudokuPuzzle`, `SudokuGenerator.generate(seed, mode)`)
- Current tier model (`SudokuGenerator.kt:8-13`, `45-48`):
  - Only **2 tiers** (`STANDARD`: 4×4, 2×2 boxes, remove `6..8`; `HARD`: 9×9, 3×3 boxes, remove `5..7`).
  - **Gap vs §6A:** Spec requires **3 tiers** (`Easy`: 4×4, 6 removed; `Medium`: 9×9, 6 removed; `Hard`: 9×9, 10–12 removed), logical-solve verification across 500 seeds/tier, `< 50ms` generation test, and tier routing via `CognitivePrefs` / `AlarmBuilderSheet` / `AlarmActivity`.
- UI (`ui/puzzle/SudokuGridView.kt`, `ui/puzzle/SudokuKeypad.kt`):
  - **Gap vs §6B–E:** Currently styled in blue Easybrain dark palette (`#161B26`, `#5B9BFF`, `#68A8FF`) instead of Morro monochrome (`#121110` surfaceCard grid, `#EFE7D6` givens, `#E8C58F` honey user digits/selection/peers, `#C4553B` digit + `0.14f` fill scoped only to grid, Morro monochrome `Undo`/`Erase`/`Notes` bar + `1..9`/`1..4` + `Clear` keypad with `>= 56dp` touch targets, 20-entry bounded `Undo` stack, zero-scroll fit on 360×800). Also `SudokuGridView.kt` currently allows inspecting given cells on tap (`onInspectGivenCell`); §6B explicitly says "Only EMPTY cells are tappable — givens are inert."

---

## 9. Existing Unit Tests (`app/src/test/kotlin/com/morro/alarm/`)

- **Audio controllers:**
  - `AlarmVolumeGuardTest.kt` (`isTooQuiet` — 1 test)
  - `GraceWindowTest.kt` (deadline math & `VOLUME = 0.30f` — 8 tests)
  - `GateSilenceControllerTest.kt` (ducking, 15s watchdog, 60s hard budget, restore invariants — 23 tests)
- **Scheduler / Reliability:** `AlarmSchedulerTest.kt` (4), `AlarmSchedulerContractTest.kt` (3), `CatchUpTest.kt` (13), `MissedAlarmLogTest.kt` (25), `WakeUpCheckTest.kt` (13), `OemGuidanceTest.kt` (8), `MemoStorageTest.kt` (4).
- **Puzzle engines & UI:**
  - Chess: `ChessEngineTest.kt` (4), `PuzzleRepositoryTest.kt` (12, validates every bundled CSV row with `FenParser` & `ChessRules`), `ChessBoardUiTest.kt` (8).
  - Sudoku: `SudokuTest.kt` (`SudokuGridTest` 9, `SudokuSolverTest` 4, `SudokuGeneratorTest` 15 — tests 500 seeds/tier & <50ms avg), `SudokuUiTest.kt` (9).
  - Cognitive selection & math: `CognitiveChallengeTest.kt` (8), `MathEngineTest.kt` (9), `MathDifficultyTest.kt` (7).

---

## 10. Work-Item Status Summary Before Implementation

1. **Fix 1 (Volume-Down Protection):** **MISSING** — neither `AlarmActivity.dispatchKeyEvent` nor the service floor watchdog + pure helper/tests exist yet.
2. **Fix 2 (Swipe-from-Recents Survival):** **MISSING / PARTIAL** — `excludeFromRecents="true"` and `mediaPlayback` FGS icon/channel exist, but `android:stopWithTask="false"`, `START_REDELIVER_INTENT`, `onTaskRemoved` recovery (requestCode `1007`), `commit()` active-ring state persistence, `taskAffinity` audit, and `recoveryNeeded` tests are missing.
3. **Item 1F (Ratio-Based Quiet-Volume Warning):** **ALREADY DONE & VERIFIED** (`AlarmVolumeGuard.isTooQuiet < 0.4f`, warned on dashboard & arm time in `AlarmBuilderSheet.kt`, unit tested in `AlarmVolumeGuardTest.kt`).
4. **Upgrade 3 (Verified Chess Assets + Wooden Board):** **MISSING / REGRESSIONS TO FIX** — `tools/filter_lichess.py` lacks programmatic move/checkmate verification and targets old rating bands; `mate_in_1.csv.gz` / `mate_in_2.csv.gz` need verified regeneration at `1100–1600` (~1200) and `1300–1800` (~800); `ChessBoardView.kt` / `ChessPieces.kt` currently have Chess.com colors + drag-and-drop instead of Maple/Walnut wood + Honey + Morro chrome + tap-only.
5. **Upgrade 4 (Sudoku 3 Tiers + Classic Morro Dark Grid):** **PARTIAL** — solver (`countSolutions`, `isLogicallyDeducible`) and grid/pad foundations exist, but `SudokuMode` only has 2 tiers (`STANDARD`/`HARD`), lacks `Easy`/`Medium`/`Hard` calibration, and `SudokuGridView`/`SudokuKeypad` use blue Easybrain palette instead of Morro `#121110` / Honey `#E8C58F` / Brick-Red `#C4553B` grid-only tokens + compact 360×800 fit.
