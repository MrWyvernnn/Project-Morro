# Chess & Sudoku Cognitive Gates + Paywall — Status

## Phase 1–4 · Pure Engines, UI, & Integration
- `puzzle/chess/`: `ChessBoard.kt`, `ChessMove.kt`, `ChessRules.kt`, `FenParser.kt`, `PuzzleRecord.kt`, `PuzzleRepository.kt`.
- `puzzle/sudoku/`: `SudokuGrid.kt`, `SudokuGenerator.kt`, `SudokuSolver.kt`.
- `assets/puzzles/`: `mate_in_1.csv.gz` (220 verified puzzles), `mate_in_2.csv.gz` (100 verified forced mate-in-2 puzzles), `LICENSE.txt` (CC0 1.0).
- `ui/puzzle/`: `ChessPieces.kt`, `ChessBoardView.kt`, `SudokuGridView.kt`, `SudokuKeypad.kt`, `CognitiveChallenge.kt`.

## Phase 5 · Adversarial Debate Hardening, Bug Fixes, Dead Code Cleanup & Paywall Upgrade
- **Adversarial Audits & Engine Hardening**:
  - Replaced invalid sample chess CSVs and broken `FALLBACK` with 320 dual-verified puzzles (`PuzzleRepositoryTest` checks 100% of rows with `FenParser` + `ChessRules`).
  - Removed the `AlarmActivity` `onMoveAttempt` validation bypass so real chess legality + checkmate validation governs dismissal.
  - Hardened `FenParser` (king counts, rank 1/8 pawns, consecutive digits, castling-rook consistency, EP target validation, opponent-in-check guard) and `ChessRules` (corner-rook castling check, EP target/enemy-pawn verification, `hasAnyLegalMove`, `hasMateInOne`, `isForcedMateInTwo`).
  - Added Morro-styled 4-piece promotion picker (`Q/R/B/N`), Mate-in-2 immediate mate-in-1 & alternative forced mate-in-2 support, axis-aligned corner-safe tap snapping, and `rememberSaveable` board state across rotation.
  - Hardened `SudokuGrid` bounds (`0..size`, positive dimensions), unified `SudokuSolver` around bitmask + MRV backtracking with pre-filled given validation, replaced dead Pass 2 in `SudokuGenerator` with bounded deterministic retries, wired `onUserAction` ducking + `rememberSaveable` persistence + responsive `verticalScroll` containers in `AlarmActivity`.
- **Integration & Dead Code Cleanup**:
  - Fixed `MathPrefs` vs `CognitivePrefs` mismatch, `PLAYING_MEMO` rotation deadlock, `AlarmSoundPlayer` `prepareAsync()` volume race, `rampPlayerVolume` cancellation race, `WakeLock.isHeld` guard, `AlarmBuilderSheet` recording/dismiss cleanup, `PoolPreset` active states, and `BackHandler` on `AlarmBuilderSheet` / `PhotoTargetSheet`.
  - Removed orphaned `PuzzleGateState.kt` + `PuzzleGateStateTest.kt`, unused `DifficultySelector`, unused constants (`KEY_CHESS_PUZZLE_ID`, `KEY_SUDOKU_SEED`, `KEY_TONE`, `KEY_SLEEP`), shadowed `rememberReducedMotion`, and unused imports.
- **Paywall Upgrade (`PaywallScreen.kt`)**:
  - Added hero ghost stage (`drawWithCache` neutral halo), factual capability spec bar, interactive 2×2 Bento capability grid that animates dynamically per tier, annual savings framing (`Save 44%`), and a sticky bottom CTA dock — preserving `PaywallPlansTest` and all Morro tokens (`#000000`, `MorroShapes`, `<= 2` Honey accents, sentence case, reduced motion).
- **Verification**:
  - `.\gradlew.bat testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL** (165 unit tests ran, 0 failures; debug APK assembled).
