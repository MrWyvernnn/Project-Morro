# Phase 3: MissionSelectScreen & AlarmBuilderSheet Overhaul

## Current State Analysis

### MissionSelectScreen.kt (ui/screens/)
- Uses old dark theme tokens (`MorroTokens.CanvasRoot`, `MorroTokens.TextPrimary`, etc.)
- Uses `MaterialTheme.typography` instead of `MorroTypography`
- Bento grid structure exists but with wrong spacing (6dp gap is correct per spec)
- Missing proper MissionSelect chips using `MorroOptionChip`
- FAB uses Material `FloatingActionButton` instead of `MorroPrimaryButton`
- Sheet content uses old dark theme colors

### AlarmBuilderSheet.kt (ui/components/)
- Uses `morroColors()` but the sheet background uses `colors.surface.copy(alpha = 0.92f)` which is good
- Anchored to FAB center with spring animation (good)
- Uses `MorroSpring.FloatSpringMomentum` for enter/exit (good)
- Missing proper content structure - currently empty slot
- Need to add proper mission selection using `MorroOptionChip`
- Need to add time picker, QR config, Photo pool config
- Need to add save/arm button using `MorroPrimaryButton`

## Design Spec Compliance

### Bubbly Sunrise Tokens (MorroTokens.kt)
- Canvas: `#FFF8F0` (warm white root)
- Surface: `#FFFFFF` (pure white cards)
- SurfaceVariant: `#FFF0E0` (4% warmer)
- SurfaceLow: `#FFFFEBE0` (subtle variant)
- TextPrimary: `#1A1A2E` (deep brown)
- TextSecondary: `#6B5B4F` (warm brown)
- TextDisabled: `#A89B8F` (muted warm)
- AccentTactical: `#E85D2A` (tactical orange - CTA/focus/icons ≥24sp)
- AccentBubbly: `#FF6B9D` (bubbly pink - GLOW ONLY)
- AccentBubblyDeep: `#D63A6B` (pink for text/icons needing bubbly hue)
- BorderSubtle: `#E8DCC8` (decorative)
- BorderActive: `#E85D2A` (focus/armed)

### Physics (MorroSpring.kt)
- Default: damping 1.0, stiffness MediumLow (~0.35s)
- Momentum: damping 0.82, stiffness Medium (~0.32s) - for sheet enter/exit
- Press: damping 1.0, stiffness High (~0.12s) - for button press

### Geometry
- Cards: 16dp true squircle (RoundedCornerShape(16.dp))
- Buttons: 14dp radius
- Chips: 12dp radius
- Sheet: 24dp top-only radius
- Focus ring: component radius + 2dp (concentric)
- Bento grid: 6dp fixed gaps (NOT 4dp, NOT 8dp)

## Implementation Plan

### 1. MissionSelectScreen.kt (ui/screens/MissionSelectScreen.kt)
**Replace entirely with new implementation:**
- Root: `MorroTheme` with `colors.canvas` background (`#FFF8F0`)
- Bento Grid: Template C (2-column vertical, 6dp gaps, 5 cards exactly)
  - Hero card (spans 2 cols): Armed mission summary with time + mission badge
  - Voice stat card (1 col): "VOICE" tag, "Voice Memo", "15s personal commitment"
  - QR stat card (1 col): "QR" tag, "QR / Barcode", "Scan to dismiss"
  - Photo stat card (1 col): "PHOTO" tag, "Object Hunt", "30-item household pool"
  - Quote card (spans 2 cols): Mission statement quote on `surfaceVariant` background
- All cards use `MorroCard` atom
- Stat cards use accent colors per mission (Tactical for Voice/Photo, BubblyDeep for QR)
- FAB: `MorroPrimaryButton` with "+" text, 56dp, bottom-right
- Sheet trigger: Anchored `AlarmBuilderSheet` with proper content

### 2. AlarmBuilderSheet.kt (ui/components/AlarmBuilderSheet.kt)
**Enhance existing implementation:**
- Keep anchored spring animation from FAB center (Momentum spec)
- Surface at 92% alpha, no blur
- Content structure:
  - Drag handle (40dp × 4dp, BorderSubtle)
  - Title: "Configure Alarm" (HeadlineLarge, Bold)
  - Subtitle: "Choose your wake-up mission and set the time." (BodyMedium)
  - Time picker card (MorroCard): Large time display, tap to open TimePickerDialog
  - Section label: "WAKE-UP MISSION" (LabelLarge, monospace, tracking)
  - Three `MorroOptionChip` for Voice/QR/Photo selection
  - Conditional expanded content per mission:
    - Voice: Record/Preview/Re-record (using existing MemoRecorder/MemoPlayer)
    - QR: Optional barcode registration dialog
    - Photo: Configure object pool (30-item bento grid modal)
  - Save button: `MorroPrimaryButton` "SAVE & ARM ALARM"
  - Bottom padding for safe area

## Files to Modify

1. **`app/src/main/kotlin/com/morro/alarm/ui/screens/MissionSelectScreen.kt`** - Complete rewrite
2. **`app/src/main/kotlin/com/morro/alarm/ui/components/AlarmBuilderSheet.kt`** - Enhance with proper content

## Verification Steps

1. Build: `./gradlew assembleDebug`
2. Install: `./gradlew installDebug`
3. Test on device:
   - Launch app, complete onboarding if needed
   - Main screen shows MissionSelectScreen with Bento grid
   - Tap "+" FAB → AlarmBuilderSheet springs from FAB center
   - Select missions, configure time, save
   - Verify alarm arms correctly
   - Test sheet dismiss (tap backdrop, swipe down)