# GATE_INTEGRATION.md — Phase 0 Recon Report

## 1. AlarmActivity Gate Dispatch

### How the gate is dispatched

`AlarmActivity.onCreate()` reads the configured mission via `MissionPrefs.getMissionType(this)`.
For the **Voice** path (`VOICE_MEMO` / `VOICE_COMMITMENT`), the gate starts at:

```kotlin
gateState = AlarmGateState.PUZZLE
AlarmSoundPlayer.start(this)
AlarmSoundPlayer.setVolume(1.0f)
```

The `AlarmGateState` enum drives which Composable branch renders in `AlarmScreen()`:

```kotlin
enum class AlarmGateState {
    PUZZLE,           // ← Math (and future Chess/Sudoku) lives here
    MISSION_QR,
    MISSION_PHOTO,
    PLAYING_MEMO,
    RECORDING_TOMORROW
}
```

### How a gate signals completion

The math gate calls `completePuzzle()` when `problemsSolved >= totalProblems`:

```kotlin
private fun completePuzzle() {
    gateState = AlarmGateState.PLAYING_MEMO
    AlarmSoundPlayer.stop()
    // → plays memo, then promptForTomorrowMemo()
}
```

The QR and Photo gates call `onMissionComplete()`:

```kotlin
private fun onMissionComplete() {
    graceJob?.cancel()
    AlarmSoundPlayer.stop()
    promptForTomorrowMemo()
}
```

**Integration contract for new gates:** Chess and Sudoku gates must call `completePuzzle()`
exactly as Math does. They replace the `PUZZLE` composable branch, never create a new
`AlarmGateState` value. The `completePuzzle()` function transitions to `PLAYING_MEMO` and
handles everything downstream.

### Transition to memo playback and capture-tomorrow

```
completePuzzle() → gateState = PLAYING_MEMO → AlarmSoundPlayer.stop()
  → MemoPlayer.startPlayback(onCompletion = promptForTomorrowMemo)
  → promptForTomorrowMemo() → gateState = RECORDING_TOMORROW
  → finishAlarm() → rearmNextWakeUp() → finishAndRemoveTask()
```

### Configuration change survival

`onSaveInstanceState` saves `KEY_GATE_STATE`, `KEY_PROBLEMS_SOLVED`, and the current
question text/answer. `restoreGateState` rebuilds from that Bundle.

New gates must save their puzzle id/seed in the Bundle (or via `rememberSaveable`),
then regenerate deterministically on restore. Never re-pick a new puzzle on rotation.

---

## 2. MathEngine — Public API & Difficulty Model

```kotlin
object MathEngine {
    enum class Difficulty(val label: String, val detail: String) {
        EASY("Easy", "Single digits, small sums"),
        MEDIUM("Medium", "Times tables, two-digit sums"),
        HARD("Hard", "Two-digit products, three-digit sums"),
    }

    data class Question(
        val text: String,
        val answer: Int,
        val options: List<Int> = emptyList(),
    )

    fun generateQuestion(difficulty: Difficulty = Difficulty.MEDIUM): Question
    fun optionsFor(answer: Int): List<Int>
    fun verify(userAnswer: String, question: Question): Boolean
    fun isCorrect(choice: Int, question: Question): Boolean
}
```

### How the UI consumes it

`AlarmActivity` holds:
- `currentQuestion: MathEngine.Question?` — the current puzzle
- `problemsSolved: Int` — counter up to `totalProblems = 3`
- `wrongChoice: Int` — tints the missed button

`nextQuestion()` reads difficulty from `MathPrefs.getDifficulty(this)` and calls
`MathEngine.generateQuestion(difficulty)`.

`submitAnswer(choice)` → `MathEngine.isCorrect(choice, question)` → if correct,
`problemsSolved++`, if `>= totalProblems` → `completePuzzle()`.

### MathPrefs — the accessor pattern

```kotlin
object MathPrefs {
    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_DIFFICULTY = "math_difficulty"

    fun getDifficulty(context: Context): MathEngine.Difficulty
    fun setDifficulty(context: Context, difficulty: MathEngine.Difficulty)
}
```

**New cognitive prefs must follow this exact pattern:**
- Same `PREFS_NAME = "morro_prefs"`
- Typed getters/setters with safe defaults
- `try/valueOf` with fallback
- Context passed per call, never stored

---

## 3. Mission Screen Composable Conventions

### QrMissionScreen signature

```kotlin
@Composable
fun QrMissionScreen(
    registeredCode: String? = null,
    isRegistrationMode: Boolean = false,
    onCodeScanned: (String) -> Unit,
    onCancel: (() -> Unit)? = null
)
```

### PhotoMissionScreen signature

```kotlin
@Composable
fun PhotoMissionScreen(
    targetItem: PhotoTargetItem,
    onMissionSuccess: () -> Unit,
    onSwapItem: (() -> Unit)? = null
)
```

**Convention:** Each gate is a top-level `@Composable fun` that takes a success callback
(`onMissionSuccess` or `onCodeScanned`) and renders its own full-screen content. The gate
does NOT control the alarm sound — `AlarmActivity` owns that.

**New gate composable contract:**

```kotlin
@Composable
fun ChessGateScreen(
    onGateSolved: () -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun SudokuGateScreen(
    onGateSolved: () -> Unit,
    modifier: Modifier = Modifier
)
```

Both must call `onGateSolved()` exactly once on success. Both must handle their own
loading states internally. Neither touches `AlarmSoundPlayer` — the integrator does that.

---

## 4. AlarmBuilderSheet — Voice Expanded Content

### Where the cognitive sub-setting goes

Inside `VoiceExpandedContent()` (line 783–1083), after the voice recording UI, there is
already a "MATH PUZZLE" section with a `DifficultySelector`:

```kotlin
// Line 1058–1082
Text(text = "MATH PUZZLE", ...)
Text(text = "Three quick sums open the alarm before your promise plays. Pick how hard.", ...)
DifficultySelector(selected = difficulty, onSelect = { level -> ... })
```

**The new cognitive challenge selector replaces the "MATH PUZZLE" label and difficulty
selector with:**
1. A "CHALLENGE" label
2. Three chips: Math / Chess / Sudoku (reusing the DifficultySelector pill pattern)
3. A Standard / Hard toggle beneath

The existing `DifficultySelector` becomes the Standard/Hard row for math. Chess and Sudoku
get their own Standard/Hard with different semantics (mate-in-1 vs mate-in-2; 4×4 vs 9×9).

The `MorroOptionChip` signature supports `expandedContent`:

```kotlin
@Composable
private fun MorroOptionChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    staggerIndex: Int = 0,
    showExpanded: Boolean = false,
    expandedContent: @Composable (() -> Unit)? = null
)
```

No new mission row is added. The three top-level chips remain Voice, QR, Photo.

---

## 5. Audio Players — Ducking Path

### AlarmSoundPlayer

```kotlin
object AlarmSoundPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun start(context: Context, onError: ((String) -> Unit)? = null)
    fun setVolume(volume: Float)  // ← This is the ducking API
    fun stop()
    fun isPlaying(): Boolean
}
```

`setVolume(volume: Float)` calls `mediaPlayer?.setVolume(clamped, clamped)`.
This is the **only** method the gate uses for ducking. It operates on Morro's own
`MediaPlayer` instance. It never calls `AudioManager.setStreamVolume`.

**Already used by:**
- `startGraceCountdown()` → `setVolume(0.2f)` for QR/Photo grace window
- `restoreFullVolume()` → `setVolume(1.0f)`
- `onResume()` → `setVolume(1.0f)` or `setVolume(0.2f)` depending on grace state

### MemoPlayer

```kotlin
object MemoPlayer {
    fun startPlayback(context, filePath, onCompletion, onError)
    fun stopPlayback()
    fun release()
    fun isPlaying(): Boolean
}
```

MemoPlayer does NOT have a `setVolume`. It plays after the gate is solved, so it is
never ducked.

**Ducking integration contract:**
- On first valid tap in Chess/Sudoku gate: `AlarmSoundPlayer.setVolume(0.35f)`
- On 15s inactivity timeout: `AlarmSoundPlayer.setVolume(1.0f)` (ramp over 600ms)
- On gate solved / failed / activity paused / destroyed: `AlarmSoundPlayer.setVolume(1.0f)`
- Ramp uses a coroutine with `animate()` on the volume float, calling `setVolume` per frame
- Watchdog: `try/finally` around the entire gate scope ensures volume restoration

---

## 6. MissionPrefs — Accessor Style to Extend

```kotlin
object MissionPrefs {
    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_MISSION_TYPE = "mission_type"

    fun getMissionType(context: Context): MissionType {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_MISSION_TYPE, null) ?: return MissionType.VOICE_COMMITMENT
        return try { MissionType.valueOf(name) } catch (e: IllegalArgumentException) { MissionType.VOICE_COMMITMENT }
    }

    fun setMissionType(context: Context, type: MissionType) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MISSION_TYPE, type.name).apply()
    }
}
```

**New accessors to add (following this exact pattern):**

```kotlin
// New keys in MissionPrefs or a new CognitivePrefs object:
private const val KEY_COGNITIVE_CHALLENGE = "cognitive_challenge"   // default: "MATH"
private const val KEY_COGNITIVE_DIFFICULTY = "cognitive_difficulty"  // default: "STANDARD"

fun getCognitiveChallenge(context: Context): CognitiveChallenge
fun setCognitiveChallenge(context: Context, challenge: CognitiveChallenge)
fun getCognitiveDifficulty(context: Context): CognitiveDifficulty
fun setCognitiveDifficulty(context: Context, difficulty: CognitiveDifficulty)
```

---

## Integration Contract — Summary

1. **New gates are cognitive-layer variants, not new mission types.** No new values in
   `MissionType`. No new rows in the builder. Chess and Sudoku sit beside Math as a
   sub-setting of the Voice Commitment mission.

2. **Gate composable shape:** `@Composable fun XxxGateScreen(onGateSolved: () -> Unit, modifier: Modifier)`.
   Calls `onGateSolved()` exactly once. Never touches `AlarmSoundPlayer`.

3. **Dispatch in AlarmActivity:** Inside the `PUZZLE` branch of `AlarmScreen`, read
   `getCognitiveChallenge()` and render `MathPuzzleUI`, `ChessGateScreen`, or
   `SudokuGateScreen`. The solved callback calls `completePuzzle()`.

4. **Audio ducking:** Use `AlarmSoundPlayer.setVolume()`. Never `AudioManager`. Restore
   to 1.0f unconditionally in `onPause`, `onDestroy`, and gate completion. Watchdog via
   `try/finally`.

5. **Configuration change:** Puzzle id/seed saved via `rememberSaveable`. Deterministic
   replay on restore. Never re-pick a new puzzle.

6. **Prefs:** Same `"morro_prefs"` file, typed enum getters/setters with safe defaults.

7. **Builder:** Add challenge picker and difficulty toggle below the voice recording UI
   in `VoiceExpandedContent`, replacing the "MATH PUZZLE" label section.

8. **Emergency fallback:** If Chess/Sudoku engine throws or puzzle fails to load, fall
   back to `MathEngine` automatically. Log to `MissedAlarmLog`. An alarm must never become
   unsilenceable.
