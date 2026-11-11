# Implementation Plan: Slice 2a - Memo Core & Test Harness

## Overview
Implement the memo recording, playback, and storage layer for the Morro alarm app. This includes three core components (`MemoStorage`, `MemoRecorder`, `MemoPlayer`) in `com.morro.alarm`, the `RECORD_AUDIO` permission in `AndroidManifest.xml`, runtime permission handling, and temporary test controls in `MainActivity` for manual verification. All code follows the existing patterns: singleton objects where appropriate, SharedPreferences key `morro_prefs`, MediaRecorder/MediaPlayer with proper resource cleanup, and 100% offline operation.

## Types
No new type definitions. Uses existing `String` paths, `SharedPreferences`, and SAM-compatible lambdas for callbacks.

## Files

### New Files
1. **`app/src/main/kotlin/com/morro/alarm/MemoStorage.kt`**
   - Singleton `object MemoStorage`
   - Constants: `PREFS_NAME = "morro_prefs"`, `KEY_LATEST_MEMO_PATH = "latest_memo_path"`, `FILENAME_LATEST = "latest_memo.m4a"`, `FILENAME_TEMP = "temp_memo.m4a"`
   - `fun getLatestMemoPath(context: Context): String?`
   - `fun saveMemo(context: Context): Boolean` — single-operation replacement using java.nio.file.Files.move with StandardCopyOption.REPLACE_EXISTING without deleting latest first, and synchronous .commit()
   - `fun clearTemp(context: Context)` — deletes temp_memo.m4a if exists
   - `fun hasSavedMemo(context: Context): Boolean`

2. **`app/src/main/kotlin/com/morro/alarm/MemoRecorder.kt`**
   - Singleton `object MemoRecorder`
   - Constants: `MAX_DURATION_MS = 15_000`, `OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4`, `AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC`, `AUDIO_SOURCE = MediaRecorder.AudioSource.MIC`
   - `fun startRecording(context: Context, onError: (String) -> Unit, onMaxDurationReached: () -> Unit)`
   - `fun stopRecording(context: Context): Boolean`
   - `fun cancelRecording(context: Context)` — calls .stop(), .release(), and nullifies the MediaRecorder before clearing the temp file via `MemoStorage.clearTemp(context)`
   - `fun isRecording(): Boolean`

3. **`app/src/main/kotlin/com/morro/alarm/MemoPlayer.kt`**
   - Singleton `object MemoPlayer`
   - Constants: `AUDIO_USAGE = AudioAttributes.USAGE_ALARM`, `CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_SPEECH`
   - `fun startPlayback(context: Context, filePath: String, onCompletion: () -> Unit, onError: (String) -> Unit)`
   - `fun stopPlayback()`
   - `fun isPlaying(): Boolean`

### Modified Files
4. **`app/src/main/AndroidManifest.xml`**
   - Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` before `<application>`

5. **`app/src/main/kotlin/com/morro/alarm/MainActivity.kt`**
   - Add `ActivityResultContracts.RequestPermission()` launcher for `RECORD_AUDIO`
   - Add `requestRecordAudioPermission()` called strictly on first Record button tap, never in `onCreate`
   - Replace `MainScreen` with test harness Composable containing:
     - Record button (starts recording, shows countdown 15→0)
     - Stop button (stops early, enables Preview/Re-record/Save)
     - Preview button (plays `temp_memo.m4a` via `MemoPlayer`)
     - Re-record button (clears temp, returns to Record state)
     - Save button (calls `MemoStorage.saveMemo`, shows success toast, returns to Record state)
     - Status text showing current state (Idle / Recording / Stopped / Playing)
   - State variables: `recording`, `recorded`, `playing`, `countdownSeconds`
   - Countdown timer using `LaunchedEffect` updating every second while recording

### Unchanged Files
- `AlarmActivity.kt`, `AlarmReceiver.kt`, `AlarmScheduler.kt`, `AlarmSoundPlayer.kt`, `BootReceiver.kt` — no changes
- `build.gradle.kts` (root and app) — no version upgrades per constraints
- `strings.xml`, `themes.xml`, `colors.xml` — may add new strings for test UI

## Functions

### MemoStorage
| Function | Signature | Purpose |
|----------|-----------|---------|
| `getLatestMemoPath` | `fun getLatestMemoPath(context: Context): String?` | Retrieve saved memo path from prefs; validate file exists |
| `saveMemo` | `fun saveMemo(context: Context): Boolean` | Single-operation replacement (NIO Files.move with REPLACE_EXISTING); synchronous .commit() |
| `clearTemp` | `fun clearTemp(context: Context)` | Delete temp file on cancel/error |
| `hasSavedMemo` | `fun hasSavedMemo(context: Context): Boolean` | Quick existence check |

### MemoRecorder
| Function | Signature | Purpose |
|----------|-----------|---------|
| `startRecording` | `fun startRecording(context: Context, onError: (String) -> Unit, onMaxDurationReached: () -> Unit)` | Begin recording to temp file; auto-stop at 15s |
| `stopRecording` | `fun stopRecording(context: Context): Boolean` | Stop cleanly; return success |
| `cancelRecording` | `fun cancelRecording(context: Context)` | Stop, release, and nullify recorder before clearing temp file |
| `isRecording` | `fun isRecording(): Boolean` | State query |

### MemoPlayer
| Function | Signature | Purpose |
|----------|-----------|---------|
| `startPlayback` | `fun startPlayback(context: Context, filePath: String, onCompletion: () -> Unit, onError: (String) -> Unit)` | Play memo on alarm stream; callbacks for done/error |
| `stopPlayback` | `fun stopPlayback()` | Stop and release |
| `isPlaying` | `fun isPlaying(): Boolean` | State query |

### MainActivity (new/modified)
| Function | Signature | Purpose |
|----------|-----------|---------|
| `requestRecordAudioPermission` | `private fun requestRecordAudioPermission()` | Runtime permission request with rationale; called only on first Record button tap, never in onCreate |
| `MainScreen` | `@Composable fun MainScreen(...)` | Replaced with test harness UI |

## Classes

### MemoStorage (object)
- Key methods: `getLatestMemoPath`, `saveMemo`, `clearTemp`, `hasSavedMemo`
- No inheritance; uses `context.filesDir` and `SharedPreferences`

### MemoRecorder (object)
- Key methods: `startRecording`, `stopRecording`, `cancelRecording`, `isRecording`
- Wraps `MediaRecorder`; enforces 15s cap via `OnInfoListener`

### MemoPlayer (object)
- Key methods: `startPlayback`, `stopPlayback`, `isPlaying`
- Wraps `MediaPlayer`; audio attributes: `USAGE_ALARM`, `CONTENT_TYPE_SPEECH`

## Dependencies
- No new dependencies. Uses only Android SDK (`MediaRecorder`, `MediaPlayer`, `AudioAttributes`, `SharedPreferences`) and existing Compose/AndroidX deps.

## Testing
- **Manual verification on device** (per CLAUDE.md: "Test on my real Android phone. Never declare an alarm feature working from an emulator run alone.")
- Test scenarios:
  1. On first Record tap, grant RECORD_AUDIO permission → Record 5s → Stop → Preview plays → Save → Verify `latest_memo.m4a` exists in app files dir
  2. Record → Cancel → Verify `latest_memo.m4a` unchanged (not deleted)
  3. Record full 15s → Auto-stop → Preview → Save
  4. Record → Stop → Re-record → Verify temp replaced
  5. Deny permission → UI shows explanation
- No unit tests added (project has no test infrastructure yet; `./gradlew test` runs empty).

## Implementation Order
1. **Add RECORD_AUDIO permission** to `AndroidManifest.xml`
2. **Create `MemoStorage.kt`** — storage layer first (no dependencies on other new files)
3. **Create `MemoRecorder.kt`** — depends on `MemoStorage.clearTemp` for cancel
4. **Create `MemoPlayer.kt`** — independent playback
5. **Update `MainActivity.kt`** — add permission launcher, replace `MainScreen` with test harness, wire all three components
6. **Verify build** — `./gradlew assembleDebug`
7. **Install and test on device** — `./gradlew installDebug`

---

**Refer to this document during implementation. All file paths are absolute from project root `c:\Users\marha\claude.md\`.**