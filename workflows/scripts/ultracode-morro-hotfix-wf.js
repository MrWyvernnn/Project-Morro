export const meta = {
  name: 'ultracode-morro-hotfix',
  description: '3 parallel subagents: Fix alarm pipeline, Live Voice Studio, Flagship UI Overhaul',
  phases: [
    { title: 'Parallel Subagents' },
    { title: 'Compiler Verification' }
  ]
};

phase('Parallel Subagents');

log('Launching 3 Parallel Subagents for Morro Hotfix...');

const results = await parallel([
  // SUBAGENT 1: Broken Alarm Pipeline Fix
  () => agent(
"You are SUBAGENT 1: Fix Broken Alarm Pipeline (5s Test Alarm & Scheduled Alarm).\n\n" +
"CRITICAL RULES:\n" +
"- Edit existing files IN PLACE using Read then Edit/Write. Do NOT create duplicate files.\n" +
"- Keep AGP 8.3.0 and Kotlin 2.0.0. Do not alter Gradle wrapper or plugin versions.\n" +
"- NO ASCII SLOP: DELETE any // MISSION_0X label comments, NEVER use text like [X] or [ ].\n\n" +
"FILES TO MODIFY:\n" +
"1. app/src/main/kotlin/com/morro/alarm/AlarmScheduler.kt\n" +
"   - Update scheduleAlarm() to always use setAlarmClock() with AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent) where showPendingIntent opens MainActivity.\n" +
"2. app/src/main/kotlin/com/morro/alarm/MainActivity.kt\n" +
"   - Update scheduleTestAlarmIn5Seconds(context) to use setAlarmClock() with AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent).\n" +
"   - Show immediate Toast when TEST 5S is tapped: 'Test alarm armed — firing in 5 seconds'.\n" +
"3. app/src/main/kotlin/com/morro/alarm/AlarmReceiver.kt\n" +
"   - In onReceive: Acquire PARTIAL_WAKE_LOCK immediately with 60s timeout.\n" +
"   - Wrap startForegroundService in try/catch.\n" +
"   - Launch AlarmActivity (or AlarmRingingActivity if renamed) directly with flags: Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP.\n" +
"4. app/src/main/kotlin/com/morro/alarm/AlarmForegroundService.kt\n" +
"   - When calling startForeground on SDK >= 29, pass ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK in try/catch with fallback to standard startForeground.\n" +
"   - Build notification with .setFullScreenIntent(fullScreenPendingIntent, true) and .setCategory(NotificationCompat.CATEGORY_ALARM).\n" +
"5. app/src/main/AndroidManifest.xml\n" +
"   - Ensure uses-permission USE_FULL_SCREEN_INTENT and FOREGROUND_SERVICE_MEDIA_PLAYBACK are declared.\n" +
"   - Ensure AlarmReceiver has android:exported='true'.\n" +
"   - Ensure AlarmForegroundService has android:foregroundServiceType='mediaPlayback' and android:exported='false'.\n",
    { label: 'subagent:alarm-pipeline', phase: 'Parallel Subagents' }
  ),

  // SUBAGENT 2: Live Voice Commitment Studio
  () => agent(
"You are SUBAGENT 2: Interactive Voice Commitment Studio (Fix Dead Recording Flow).\n\n" +
"CRITICAL RULES:\n" +
"- Edit existing files IN PLACE using Read then Edit/Write. Do NOT create duplicate files.\n" +
"- Keep AGP 8.3.0 and Kotlin 2.0.0. No ASCII slop ([X], [ ]) or mission labels.\n\n" +
"FILES TO MODIFY:\n" +
"1. app/src/main/kotlin/com/morro/alarm/ui/components/AlarmBuilderModal.kt\n" +
"   - Add permission request for Manifest.permission.RECORD_AUDIO with rememberLauncherForActivityResult.\n" +
"   - When user selects Voice Commitment card (or taps record):\n" +
"     Check ContextCompat.checkSelfPermission. If not granted, launch permission launcher.\n" +
"   - State Machine inside Voice Commitment Card:\n" +
"     * State IDLE: Prompt 'Speak clearly: \"I am waking up at [Time] tomorrow to protect my [Mission]. No excuses.\"'. Large pill button in #FFD54F with Mic icon: 'Tap to Record (15s)'.\n" +
"     * State RECORDING: Call MemoRecorder.startRecording(context). Display 12 animated pulsing vertical waveform bars in #FFD54F + countdown timer (15s down to 00:00). Button: 'Stop & Lock Clip' (#FF5252 coral-red or #FFD54F).\n" +
"     * State RECORDED_READY: Automatically call MemoStorage.saveMemo(context). Show glowing #FFD54F status pill: '✓ Voice Promise Locked'. Secondary buttons side-by-side: '▶ Preview Voice' (calls MemoPlayer.startPlayback) and '↻ Re-record' (cancels and resets to IDLE).\n" +
"   - Hard Validation Gate on 'SAVE & ARM ALARM':\n" +
"     If selectedMission is VOICE_MEMO (or VOICE_COMMITMENT) and !MemoStorage.hasSavedMemo(context), clicking 'SAVE & ARM ALARM' MUST NOT close! Trigger Toast 'Please record your 15-second voice promise first' and launch recording flow.\n",
    { label: 'subagent:voice-studio', phase: 'Parallel Subagents' }
  ),

  // SUBAGENT 3: Flagship Consumer UI Overhaul
  () => agent(
"You are SUBAGENT 3: Flagship Consumer UI Overhaul (Configure Alarm & 30-Item Bento Grid).\n\n" +
"CRITICAL RULES:\n" +
"- Edit existing files IN PLACE using Read then Edit/Write. Do NOT create duplicate files.\n" +
"- Keep AGP 8.3.0 and Kotlin 2.0.0. ZERO ASCII SLOP — delete any // MISSION_0X comments, NEVER render text like [X] or [ ].\n\n" +
"FILES TO MODIFY:\n" +
"1. app/src/main/kotlin/com/morro/alarm/ui/components/AlarmBuilderModal.kt\n" +
"   Redesign Configure Alarm Screen:\n" +
"   3.1 Wake-Up Time Card:\n" +
"       - Elevated obsidian card (#0D0D14), 20.dp rounded corners, 1.dp border (Color.White.copy(alpha=0.08f)).\n" +
"       - Top pill badge: 'WAKE-UP TARGET' in 11.sp tracking-wide #999999.\n" +
"       - Center: Massive 72.sp bold time (06:30) in #FFD54F with radial glow behind digits.\n" +
"       - Bottom hint pill: 'Tap digits to adjust time' in #999999. Tapping opens TimePickerDialog.\n" +
"   3.2 Mission Selector Cards (Purge // MISSION_0X and [X]):\n" +
"       - 3 mission cards (Voice Commitment, QR / Barcode Scan, Object Photo Hunt).\n" +
"       - Left: 48.dp squircle (14.dp radius) icon container (#FFD54F.copy(alpha=0.12f) selected, #161622 unselected) with Material Vector Icon (Mic, QrCodeScanner, PhotoCamera) tinted #FFD54F selected, #888899 unselected.\n" +
"       - Center Column: Title in 17.sp SemiBold Warm White, Subtitle in 13.sp #999999.\n" +
"       - Right: Custom 24.dp Circular Radio Indicator (unselected = 24.dp circle with 1.5.dp #333344 stroke; selected = 24.dp solid #FFD54F circle with black Check icon).\n" +
"       - Expanded Action Footer inside Selected Card:\n" +
"         * Voice: Inline Voice Studio Panel.\n" +
"         * QR / Barcode: #161622 pill button 'Scan & Lock Target Barcode (Optional)' + status badge.\n" +
"         * Object Photo: #161622 pill button with #FFD54F border: 'Configure Object Pool (X items active) →'.\n" +
"   3.3 Household Items Pool Sheet -> 2-Column Bento Grid Modal:\n" +
"       - Sheet Container: #0A0A0F background, 28.dp rounded top corners, drag handle (40.dp x 4.dp, #333344).\n" +
"       - Header Block: Title 'Wake-Up Object Pool' (22.sp Bold White) left, glowing counter pill 'X Selected' (#FFD54F.copy(alpha=0.15f) bg, #FFD54F bold text) right.\n" +
"       - Subtitle (13.sp #999999): 'At alarm time, Morro randomly picks one object from your pool. You must photograph it to silence the alarm.'\n" +
"       - Quick-Select Filter Pills (LazyRow with 8.dp spacing, RoundedCornerShape(50)):\n" +
"         '✨ Common 10', 'Select All 30', 'Bathroom', 'Desk & Tech', 'Kitchen', 'Clear'.\n" +
"       - 2-Column Tactile Bento Grid (LazyVerticalGrid with GridCells.Fixed(2), 10.dp spacing):\n" +
"         Each tile (height 76.dp, RoundedCornerShape(16.dp), padding 12.dp):\n" +
"         Unselected: #12121A bg, border 1.dp #1F1F2E.\n" +
"         Selected: #FFD54F.copy(alpha=0.10f) bg, border 1.5.dp #FFD54F.\n" +
"         Tile Content:\n" +
"         Left: 38.dp circular badge with distinct emoji for each of the 30 items:\n" +
"         ☕ Cup/Mug, 🪥 Toothbrush, 🚰 Sink, 🪑 Chair, ⌨️ Keyboard, 🖱️ Mouse, 👟 Shoe, 📖 Book, 🧴 Water Bottle, 👓 Glasses, 🥄 Spoon, 🍴 Fork, 🪴 Houseplant, 🛏️ Pillow, ⌚ Wristwatch, 🎒 Backpack, 🚪 Door, 🖥️ Monitor, 💻 Laptop, 📺 Remote, ✂️ Scissors, 🧺 Towel, 🪵 Desk, 💡 Lamp, 🪞 Mirror, 🖊️ Pen, 🎧 Headphones, 👛 Wallet, 🔑 Keys, 🕰️ Wall Clock.\n" +
"         Center: Item Name in 14.sp SemiBold (#FFFFFF selected, #CCCCDD unselected).\n" +
"         Top-Right Corner: 18.dp circular indicator (solid #FFD54F with black ✓ selected, subtle #2A2A38 ring unselected).\n" +
"       - Sticky Bottom Action Bar: Full-width 56.dp button (#FFD54F bg, Color.Black bold text, 14.dp shape): 'CONFIRM X OBJECTS' (disabled if 0 selected).\n",
    { label: 'subagent:ui-overhaul', phase: 'Parallel Subagents' }
  )
]);

phase('Compiler Verification');
log('Running compiler verification...');

const buildResult = await agent(
"You are the VERIFICATION AGENT.\n" +
"Run: JAVA_HOME=\"/c/Program Files/Android/Android Studio/jbr\" ./gradlew assembleDebug\n" +
"If there are any errors or unresolved references, fix them with Edit and re-run until BUILD SUCCESSFUL.\n" +
"Scan app/src/main/kotlin for any remaining // MISSION_0X comments or [X] text, and remove them.\n" +
"Return final summary of build status and verified fixes.\n",
  { label: 'build-verifier', phase: 'Compiler Verification' }
);

return { results, buildResult };
