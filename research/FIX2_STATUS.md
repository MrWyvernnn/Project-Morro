# FIX2_STATUS — gate audio, theme recolour, contrast, stale APIs

One row per phase. A phase is only "done" when its tests pass; device items are listed in §Device
and are unverified until the founder runs them.

| Phase | Finding | State | Files touched | Tests | Gaps |
|---|---|---|---|---|---|
| 0 | Recon | **done** | `research/FIX2_RECON.md` | — | F3 has no call-site list to fix; it is refuted as described |
| 1 | F1 restore on exit | **done (build + unit only)** | `GateSilenceController.kt`, `AlarmActivity.kt`, `GateSilenceControllerTest.kt` (+5, 1 rewritten) | 11 gate tests green | the throwing-callback exit needs the phone; no JVM Android runtime |
| 2 | F2 duck + hard deadline | **done (build + unit only)** | `GateSilenceController.kt`, `AlarmActivity.kt`, `GateSilenceControllerTest.kt` (+8) | 19 gate tests green | rotation/process-death persistence is wired through the activity bundle but only the pure restore path is tested |
| 3 | F5 contrast + test | **done** | `Theme.kt` KDoc, `MorroPaletteTest.kt` (+1) | 8 palette tests green | Autumn 11sp text not yet read on the phone; value is 5.59:1 on canvas, 5.02:1 on a card |
| 4 | F3 recolour pass | **not needed — refuted** | none | — | no cached colour site exists; live switch still needs the phone |
| 5 | F4 guarded palette write | **done** | `Theme.kt`, `MorroPaletteTest.kt` (+1) | 9 palette tests green | first-frame timing needs the phone |
| 6 | F6 stale APIs | **done** | `Theme.kt`, `MorroCard.kt`, `design/MORRO_VISUAL_SYSTEM.md`, `design/design.md` | `testDebugUnitTest` green | no behaviour change expected; build only |
| 7 | Scope audit | **done (in recon)** | `research/FIX2_RECON.md` §8 | — | — |
| 8 | Build + device pass | pending | — | — | needs the phone |

## Decisions

- **F1** is fixed in the controller (`resetImmediate` and `checkWatchdog(false)` emit full volume
  before clearing, guarded so a never-silenced controller emits nothing), plus a
  `DisposableEffect(Unit) { onDispose { restoreVolumeUnconditional() } }` in `AlarmScreen` so a
  gate that exits by exception still restores. The existing `onPause`/`onDestroy` restores stay.
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

## Device

See §8 of this file once written. Nothing there is claimed as passing.
