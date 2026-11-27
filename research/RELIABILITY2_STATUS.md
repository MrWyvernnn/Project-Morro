# Reliability 2 + Puzzle Upgrades — Status Tracker

- [x] **Phase 0** — Recon (`research/RELIABILITY2_RECON.md`)
- [x] **Phase 1** — Fix 1: Activity key interception, service floor watchdog, per-device awareness, `floorRestoreTarget` + unit tests (`AlarmVolumeGuardTest`)
- [x] **Phase 2** — Fix 2: Manifest (`stopWithTask=false`, `taskAffinity="com.morro.alarm.ring"`, `excludeFromRecents=true`), `START_REDELIVER_INTENT`, `commit()` ring state, `onTaskRemoved` recovery (`1007`), KDoc honest limit, `recoveryNeeded` + unit tests (`RingRecoveryTest`)
- [x] **Phase 3** — Verify 1F ratio-based quiet-volume warning (`AlarmVolumeGuard.isTooQuiet < 0.4f` + arm-time toast in `AlarmBuilderSheet.kt` + `AlarmVolumeGuardTest`)
- [x] **Phase 4** — Upgrade 3: Verified Chess Lichess filter script & gzipped assets (`1100–1600` 1200 rows / `1300–1800` 800 rows, 0 rejections) + Wooden Chessboard & Morro monochrome chrome + tap-only UX
- [x] **Phase 5** — Upgrade 4: Sudoku 3 wake-calibrated tiers (`Easy`/`Medium`/`Hard`) + 500-seed uniqueness/deducibility & <50ms tests + Classic Morro dark grid/bar/keypad (360×800 fit)
- [x] **Phase 6** — Full build (`assembleDebug`, `lint`, `test`) + ready for device pass verification
