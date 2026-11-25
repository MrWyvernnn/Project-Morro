# FIX2_RECON — gate audio, theme recolour, contrast, stale APIs

Phase 0 of the second reliability pass. No code was changed before this file was written.

Every claim below is from the working tree at `0b11e40`, verified by reading the file or by
`rg`; nothing is quoted from the research `.md` documents.

---

## 1 · `GateSilenceController.kt` — the whole file

```kotlin
package com.morro.alarm

/**
 * Pure, testable controller that silences Morro's alarm player (`0.0f`) the moment
 * the user starts interacting with a cognitive gate, and restores full volume (`1.0f`)
 * if they go idle for [inactivityTimeoutMs] (15 seconds) so they cannot fall back asleep.
 *
 * Never touches system audio streams — only drives Morro's own MediaPlayer volume.
 */
internal class GateSilenceController(
    val inactivityTimeoutMs: Long = 15_000L,
    val silencedVolume: Float = 0.0f,
    val fullVolume: Float = 1.0f,
    private val silenceRampMs: Long = 200L,
    private val restoreRampMs: Long = 600L,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val onTargetVolume: (targetVolume: Float, durationMs: Long) -> Unit
) {
    var isSilenced: Boolean = false
        private set

    var lastInputAtMs: Long = 0L
        private set

    fun onUserAction() {
        lastInputAtMs = clock()
        if (!isSilenced) {
            isSilenced = true
            onTargetVolume(silencedVolume, silenceRampMs)
        }
    }

    /**
     * Evaluates elapsed idle time while in the puzzle gate.
     * Returns the remaining milliseconds to wait if still silenced, or `null` when
     * the watchdog has restored full volume or the gate is no longer active.
     */
    fun checkWatchdog(isPuzzleActive: Boolean): Long? {
        if (!isPuzzleActive) {
            isSilenced = false
            return null
        }
        if (!isSilenced) return null
        val idleMs = (clock() - lastInputAtMs).coerceAtLeast(0L)
        if (idleMs >= inactivityTimeoutMs) {
            isSilenced = false
            onTargetVolume(fullVolume, restoreRampMs)
            return null
        }
        return (inactivityTimeoutMs - idleMs).coerceIn(50L, inactivityTimeoutMs)
    }

    fun resetImmediate() {
        isSilenced = false
    }
}
```

Facts that matter:

- `silencedVolume` defaults to **`0.0f`** — a total mute.
- `resetImmediate()` and the `!isPuzzleActive` branch of `checkWatchdog()` clear the flag and
  emit **nothing**. If a caller relies on them to un-mute, the player stays at whatever the last
  emitted target was.
- The 15 s idle timer is restarted by every `onUserAction()`, so input can renew the silence
  forever.

## 2 · Every call site of `resetImmediate()` / `checkWatchdog()`

Search: `rg -n "resetImmediate|checkWatchdog" app/src`.

| File:line | Call | Does the caller restore the player volume itself? |
|---|---|---|
| `AlarmActivity.kt:305` | `checkWatchdog(gateState == PUZZLE)` inside the `duckJob` loop | Yes in practice: the loop can only be woken with `false` after `completePuzzle()` has already run `restoreVolumeUnconditional()` (line 387) and cancelled `duckJob`. The controller itself emits nothing on this path. |
| `AlarmActivity.kt:343` | `resetImmediate()` inside `restoreVolumeUnconditional()` | **Yes** — line 344 calls `AlarmSoundPlayer.setVolume(1.0f)` immediately after. |
| `GateSilenceControllerTest.kt:119` | test | n/a |
| `GateSilenceControllerTest.kt:38,46,52,68,88,104` | tests | n/a |

`restoreVolumeUnconditional()` is the only production caller of `resetImmediate()`, and it is
called from exactly three places:

| File:line | Trigger |
|---|---|
| `AlarmActivity.kt:387` | `completePuzzle()` — gate solved |
| `AlarmActivity.kt:638` | `onPause()` |
| `AlarmActivity.kt:643` | `onDestroy()` |

**Verdict for F1:** the defect in the class is **real and confirmed** (`resetImmediate` and
`checkWatchdog(false)` can clear the flag without emitting full volume), but **no production path
currently leaves the alarm muted today** — the single caller restores volume one line later, and
the two Android teardown callbacks also restore. It is held shut by call-site discipline, not by
the controller. That is exactly the structural risk F1 describes. It becomes a live mute the
first time a second caller uses `resetImmediate()` or the `false` branch without the follow-up
line.

**One genuinely unguarded exit exists:** an exception thrown inside a gate callback (a tap that
ducks via `onGateUserAction()` and then throws before any teardown) runs no restore at all — no
`try/finally`, no `DisposableEffect`. `onPause`/`onDestroy` may still run if Android tears the
activity down normally, but a process crash skips them.

## 3 · `AlarmSoundPlayer.kt` — the instance the callback drives

- `onTargetVolume` in `AlarmActivity.kt:124` is
  `{ target, durationMs -> rampPlayerVolume(target = target, durationMs = durationMs) }`.
- `rampPlayerVolume()` (`AlarmActivity.kt:316-337`) ends in `AlarmSoundPlayer.setVolume(target)`.
- `AlarmSoundPlayer` owns **one** `MediaPlayer` (`private var mediaPlayer: MediaPlayer?`), created
  with `AudioAttributes.USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`.
- `setVolume()` (`AlarmSoundPlayer.kt:116-125`) clamps `0.0..1.0`, stores `currentVolume`, and
  calls `mediaPlayer?.setVolume(clamped, clamped)`. `getVolume()` returns `currentVolume`.
- `MemoPlayer` is a **separate** object with its own `MediaPlayer`; the gate controller never
  touches it.
- There is **no** `AudioManager.setStreamVolume` anywhere. The only `STREAM_ALARM` references are
  reads in `MainActivity.kt:240,248` for the zero-volume warning banner.

So the gate duck is a per-player volume (`MediaPlayer.setVolume`), not a system stream change, and
the callback has a real setVolume path behind it.

## 4 · `AlarmActivity.kt` — gate start, gate end, every exit

Gate start:

- `PUZZLE` is the gate for `VOICE_MEMO`/`VOICE_COMMITMENT` (`onCreate`, line 225-229), and is the
  fallback challenge surface selected by `CognitivePrefs` (MATH / CHESS / SUDOKU).
- Taps enter through `onUserInteraction()` (line 288) and through the chess/sudoku callbacks
  (`onGateUserAction` at lines 833 and 900); `submitAnswer()` also calls it (line 370).
- The first tap calls `silenceController.onUserAction()` → ramps the player to `0.0f` over 200 ms
  and starts the `duckJob` watchdog loop.

Exit paths:

| Exit | What runs | Volume outcome |
|---|---|---|
| Gate solved (math/chess/sudoku) | `completePuzzle()` → `restoreVolumeUnconditional()`, then `AlarmSoundPlayer.stop()` | full, then silent because the ring is over |
| Wrong answer | `onGateUserAction()`, puzzle stays | ducked by design |
| QR / photo mission solved | `onMissionComplete()` → `AlarmSoundPlayer.stop()` | silent because the ring is over; controller not involved |
| QR / photo grace expiry | `startGraceCountdown()` → `setVolume(1.0f)` at 30 s | full |
| `onPause` | `restoreVolumeUnconditional()` | full |
| `onDestroy` | `restoreVolumeUnconditional()` then `stop()` | full |
| Back press | `onBackPressed` no-op + `BackHandler` no-op | no exit |
| Exception inside a gate callback | nothing | **stays ducked until pause/idle** |

The device kill case (process death while ducked) is covered only indirectly:
`AlarmSoundPlayer.currentVolume` is an in-process field, re-initialised to `1.0f` in a new process
(`AlarmSoundPlayer.kt:110`).

## 5 · The palette file and both palettes

The real file is **`app/src/main/kotlin/com/morro/alarm/ui/theme/MorroPalette.kt`** (not
`MorroPalettes.kt`). `MorroThemeChoice` + `ThemePrefs` live in `MorroThemePrefs.kt`.

`MorroColorPalette` defaults (= Midnight):

```
canvas        0xFF000000
surface       0xFF0B0A08
surfaceCard   0xFF121110
surfaceVariant 0xFF121110
surfaceElevated 0xFF1A1714
surfaceHigh   0xFF231F19
textPrimary   0xFFEFE7D6
textSecondary 0xFFA79E8D
textTertiary  0xFF877F70
textDisabled  0xFF6B6458
honey         0xFFE8C58F
borderSubtle  0x14EFE7D6
borderStrong  0x2EEFE7D6
borderActive  0x59EFE7D6
success       0xFFB9C4A6
error         0xFFD8C1A4
```

`MorroPalettes.Autumn`:

```
canvas        0xFF000000
surface       0xFF0D0906
surfaceCard   0xFF16100A
surfaceVariant 0xFF16100A
surfaceElevated 0xFF201710
surfaceHigh   0xFF2B1F15
textPrimary   0xFFF5E8D6
textSecondary 0xFFB9A78D
textTertiary  0xFF93816A
textDisabled  0xFF75654F
honey         0xFFF2A25C
borderSubtle  0x14F5E8D6
borderStrong  0x2EF5E8D6
borderActive  0x59F5E8D6
success       0xFFB7BE9B
error         0xFFD0787E
```

Measured contrast (WCAG 2.1 relative luminance, computed by script, not estimated):

| Pair | Ratio |
|---|---|
| Midnight textPrimary / canvas | 17.07:1 |
| Midnight textSecondary / canvas | 7.92:1 |
| Midnight textTertiary / canvas | **5.30:1** |
| Midnight textTertiary / card | 4.76:1 |
| Midnight textDisabled / canvas | 3.59:1 |
| Autumn textPrimary / canvas | 17.40:1 |
| Autumn textSecondary / canvas | 8.97:1 |
| Autumn textTertiary / canvas | **5.59:1** |
| Autumn textTertiary / card | 5.02:1 |
| Autumn textDisabled / canvas | 3.73:1 |
| Autumn honey / canvas | 10.10:1 |

**Verdict for F5:** the *value* already passes — Autumn `textTertiary` is 5.59:1 on the canvas,
not below 4.5. What is missing is the paperwork: `Theme.kt:97-99` still says "check the new value
before calling this done", and there is no test that names the requirement. The hex does **not**
need changing. A test already walks every role at ≥4.5:1
(`MorroPaletteTest.everyTextRoleClearsAaOnTheCanvas`), so the permanent guard exists; it will be
made explicit for tertiary with the recorded ratio.

Note reported, not fixed: `textDisabled` is 3.59:1 (Midnight) / 3.73:1 (Autumn). WCAG exempts
disabled controls, but `AlarmActivity.kt:1127` and `:1248` use `textDisabled` for **active** 11sp
copy ("Alarm is sounding at full volume", "Skip (Use yesterday's memo)"). That is a real
legibility concern outside F5's "change TextTertiary only" scope.

## 6 · F3 — the cached-colour work list

Searches run over `app/src/main/kotlin`:

- `remember()/remember {}` blocks whose body reads `MorroColors.` or a palette value: **none**.
  A multiline `remember\s*\{[\s\S]{0,400}MorroColors\.` sweep returns only unrelated
  `MutableInteractionSource` blocks.
- `Animatable(` initialised from a `MorroColors` value: **none**. Every `Animatable` is numeric
  (`0f`/`1f`).
- Top-level `val x = MorroColors.Y`: **none**.
- `lerpColor(` call sites: `MorroPrimaryButton.kt:61` (endpoint `colors.textPrimary`, read from
  `morroColors()` in composition) and `FallingLeaves.kt:163` (a fixed `tint` argument).
  `springToColor(`: `MorroCard.kt:56` with `colors.borderSubtle`/`colors.borderActive` from
  `morroColors()`.
- `animateColorAsState(targetValue = MorroColors…)`: `MorroSwitch.kt:63`, `MorroStatusDot.kt:49`,
  plus `AlarmBuilderSheet.kt:648,653` via palette locals. These re-read the target every
  composition, so they recolour (with an animation) rather than sticking.
- Palette reads inside `Canvas`/`drawBehind`/`drawWithCache` (`MorroAnalogClock.kt`,
  `MorroGhostMascot.kt`, `MorroSkyScrim.kt`, `MorroSkeleton.kt`, `PaywallScreen.kt:219-229`,
  `MorroCard.kt:63-80`): draw-scope snapshot reads are tracked by Compose's draw observer, and
  every captured value (`haloInner`, `borderColor`, the skeleton gradient) is recomputed in
  composition, so the modifier/lambda changes on a theme switch.
- `MorroColors.CloudWhite` and `MorroColors.EyeRose` are hardcoded, deliberately non-themed
  tokens. `MorroGhostMascot`'s `BodyLight/BodyWarm/BodyShade` are private fixed values; only its
  accent rim reads `MorroColors.Honey` (tracked).

**Verdict for F3: refuted as described.** There is no `remember`-cached, `Animatable`-seeded, or
top-level captured palette value in the tree, so there is no mechanical call-site list to fix
without inventing one. The only cache construct that holds a palette-derived colour is the
`drawWithCache` halo in `PaywallScreen.kt:224`, and it is invalidated because the cached lambda is
rebuilt on recomposition. The honest action is to leave call sites alone, add nothing, and verify
a live switch on the phone. If a stale surface is seen on hardware, the file:line will come from
that observation, not from a guess.

## 7 · `MorroColors` publishing and F4

`Theme.kt:355-374`:

```kotlin
val context = LocalContext.current
var choice by remember { mutableStateOf(ThemePrefs.get(context)) }

DisposableEffect(context) {
    val stop = ThemePrefs.observe(context) { choice = it }
    onDispose(stop)
}

val palette = MorroPalettes.of(choice)

// Publish the palette before anything below reads a colour, so a cold start on Autumn paints
// correctly on its very first frame instead of flashing Midnight for one frame. The write is
// idempotent: the same palette instance is assigned on every later pass, and SnapshotMutableState
// compares by value, so it stops invalidating anything after the first composition.
MorroColors.use(choice)
```

`MorroColors.use` is:

```kotlin
private val active = mutableStateOf(MorroPalettes.of(MorroThemeChoice.MIDNIGHT))

internal fun use(choice: MorroThemeChoice) {
    active.value = MorroPalettes.of(choice)
}
```

There is **no `Application` subclass** and no `android:name` on `<application>`
(`AndroidManifest.xml:37-41`). Creating one to pre-seed the palette is a manifest change plus a new
class for a cold-start nicety; F4's guard is enough. Decision recorded in `FIX2_STATUS.md`: keep
the composition-body write (it is what prevents the first-frame flash), make the no-op explicit
with an internal `currentChoice` check, and do not add an Application.

Note also: `MorroColors` is a process-wide singleton, so both activities writing the same
`ThemePrefs` value cannot disagree.

## 8 · Scope audit — `e0d1d07`

`git show e0d1d07 --stat` — 39 files, 6941 insertions. Relevant observations:

- **Gradle dependency:** `git show e0d1d07 -- app/build.gradle.kts` is **empty** — the commit did
  not touch the build file. No new dependency.
- **Room / SQLite / DB in `puzzle/`:** `rg -i "androidx.room|sqlite|okhttp|retrofit|kotlinx.serialization|Room\b"`
  over `puzzle/` and `ui/puzzle/` → **no matches**. Puzzles are parsed from assets.
- **Asset form:** gzipped CSV, not a DB:
  `app/src/main/assets/puzzles/mate_in_1.csv.gz` (3,229 bytes) and
  `mate_in_2.csv.gz` (3,261 bytes).
- **Attribution:** `app/src/main/assets/puzzles/LICENSE.txt` exists (119 bytes): "Lichess Puzzle
  Database / Source: https://database.lichess.org/#puzzles / License: Creative Commons CC0 (Public
  Domain)". Nothing to add.
- **Cognitive preference default:** `CognitivePrefs.getChallenge()` returns
  `CognitiveChallenge.MATH` when the key is absent or unparseable
  (`ui/puzzle/CognitiveChallenge.kt:56-62`). Existing users keep Math. No silent behaviour change.
- **MissionType:** `e0d1d07` touched no file under `mission/`, so the 6 values in 3 alias pairs are
  unchanged.

## 9 · Tests present

`app/src/test/kotlin/com/morro/alarm/`: `AlarmSchedulerContractTest`, `AlarmSchedulerTest`,
`CatchUpTest`, `GateSilenceControllerTest`, `MathDifficultyTest`, `MathEngineTest`,
`MemoStorageTest`, `MissedAlarmLogTest`, `OemGuidanceTest`, `WakeUpCheckTest`;
`mission/PhotoTargetPoolTest`; `puzzle/chess/ChessEngineTest`, `PuzzleRepositoryTest`;
`puzzle/sudoku/SudokuTest`; `ui/motion/MotionMathTest`; `ui/onboarding/IntroSequenceTest`,
`OnboardingQuestionsTest`, `PaywallPlansTest`; `ui/puzzle/ChessBoardUiTest`,
`CognitiveChallengeTest`, `SudokuUiTest`; `ui/theme/MorroPaletteTest`.

Pure helpers already covered include: the gate controller (clock injected, recording volume
lambda), math generation, palette contrast, missed-ring inference, wake-check restore plans, OEM
mapping, memo promotion, chess/sudoku engines and repository. There is **no** Robolectric and no
Android runtime in JVM tests; anything touching `Context` is either source-read (`AlarmSchedulerContractTest`)
or stays pure.

## 10 · Finding verdicts

| # | Verdict | Evidence |
|---|---|---|
| F1 | **Confirmed in the class, latent in production** | `resetImmediate`/`checkWatchdog(false)` clear without emitting; the only caller restores one line later; no `try/finally`/`onDispose` around a throwing gate |
| F2 | **Confirmed, exploitable** | `silencedVolume = 0.0f`, the 15 s timer is renewed by every tap, and `onUserInteraction` fires on any touch |
| F3 | **Refuted as described** | no `remember`/`Animatable`/top-level colour captures exist; see §6 |
| F4 | **Confirmed as a legibility issue, not a bug** | the unguarded write is intentional and idempotent by value; no Application class exists |
| F5 | **Value already passes; the TODO is stale** | Autumn tertiary 5.59:1 on canvas, 5.02:1 on card; `Theme.kt:97` still says "check" |
| F6a | **Confirmed** | `rememberMorroSpring(initialValue, spec)` ignores `spec`; one caller passes it |
| F6b | **Confirmed** | `Theme.kt:284` and `design/MORRO_VISUAL_SYSTEM.md:78` claim a momentum spring that does not exist |
