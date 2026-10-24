package com.morro.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * OnboardingActivity: Strict first-run onboarding wizard.
 *
 * Enforces the core requirements of Slice 3:
 *   1. The Permission Waterfall (POST_NOTIFICATIONS, RECORD_AUDIO, SCHEDULE_EXACT_ALARM).
 *   2. OEM Battery Hardening (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
 *   3. The First Promise (recording the initial 15-second commitment memo via MemoStorage).
 *
 * Users cannot access MainActivity until this flow is completed.
 */
class OnboardingActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "morro_prefs"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        /** Checks if onboarding is finished and a valid initial memo exists. */
        fun isOnboardingComplete(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val completed = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
            return completed && MemoStorage.hasSavedMemo(context)
        }
    }

    private val TAG = "OnboardingActivity"

    /** Steps of the onboarding wizard */
    enum class Step {
        NOTIFICATIONS,
        MICROPHONE,
        EXACT_ALARMS,
        BATTERY_HARDENING,
        FIRST_PROMISE
    }

    // Wizard navigation state
    private var currentStep by mutableStateOf(Step.NOTIFICATIONS)

    // Permission and system state tracked in Compose
    private var hasNotificationPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var hasExactAlarmPermission by mutableStateOf(false)
    private var isBatteryOptimizationsIgnored by mutableStateOf(false)

    // First Promise recording state
    private var isRecording by mutableStateOf(false)
    private var recordingCountdown by mutableStateOf(15)
    private var hasTempRecorded by mutableStateOf(false)
    private var isPlayingPreview by mutableStateOf(false)

    // Permission launchers
    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            advanceToNextStep()
        }
    }

    private val audioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            advanceToNextStep()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemStates()

        // Auto-advance to the first uncompleted step
        determineInitialStep()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF9E1B),   // Warm Amber
                    secondary = Color(0xFFE65100), // Muted Orange
                    background = Color(0xFF000000),// AMOLED Black
                    surface = Color(0xFF1A1200),   // Dark Amber Surface
                    onPrimary = Color.Black,
                    onSecondary = Color.Black,
                    onBackground = Color(0xFFFFE0B2), // Warm Cream
                    onSurface = Color(0xFFFFCC80)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF000000)
                ) {
                    OnboardingScreen(
                        currentStep = currentStep,
                        hasNotificationPermission = hasNotificationPermission,
                        hasAudioPermission = hasAudioPermission,
                        hasExactAlarmPermission = hasExactAlarmPermission,
                        isBatteryOptimizationsIgnored = isBatteryOptimizationsIgnored,
                        isRecording = isRecording,
                        recordingCountdown = recordingCountdown,
                        hasTempRecorded = hasTempRecorded,
                        isPlayingPreview = isPlayingPreview,
                        onRequestNotification = { requestNotificationPermission() },
                        onRequestAudio = { requestAudioPermission() },
                        onRequestExactAlarm = { requestExactAlarmPermission() },
                        onRequestIgnoreBattery = { requestIgnoreBatteryOptimizations() },
                        onStartRecording = { startFirstPromiseRecording() },
                        onStopRecording = { stopFirstPromiseRecording() },
                        onCancelRecording = { cancelFirstPromiseRecording() },
                        onPlayPreview = { playPreview() },
                        onSavePromise = { saveFirstPromise() },
                        onNextStep = { advanceToNextStep() },
                        onOpenAppSettings = { openAppSettings() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions and battery status when returning from system dialogs/settings
        updateSystemStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        MemoPlayer.stopPlayback()
        if (MemoRecorder.isRecording()) {
            MemoRecorder.cancelRecording(this)
        }
    }

    private fun updateSystemStates() {
        // Notification permission check (API 33+)
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Auto-granted on API < 33
        }

        // Microphone permission check
        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Exact alarm check (API 31+)
        hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        // Battery optimization exemption check (API 23+)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptimizationsIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)

        // Check uncommitted temp memo status
        hasTempRecorded = MemoStorage.hasTempMemo(this)
    }

    private fun determineInitialStep() {
        currentStep = when {
            !hasNotificationPermission -> Step.NOTIFICATIONS
            !hasAudioPermission -> Step.MICROPHONE
            !hasExactAlarmPermission -> Step.EXACT_ALARMS
            !isBatteryOptimizationsIgnored -> Step.BATTERY_HARDENING
            else -> Step.FIRST_PROMISE
        }
    }

    private fun advanceToNextStep() {
        currentStep = when (currentStep) {
            Step.NOTIFICATIONS -> Step.MICROPHONE
            Step.MICROPHONE -> Step.EXACT_ALARMS
            Step.EXACT_ALARMS -> Step.BATTERY_HARDENING
            Step.BATTERY_HARDENING -> Step.FIRST_PROMISE
            Step.FIRST_PROMISE -> Step.FIRST_PROMISE
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
            advanceToNextStep()
        }
    }

    private fun requestAudioPermission() {
        audioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch exact alarm settings directly: ${e.message}")
                openAppSettings()
            }
        } else {
            hasExactAlarmPermission = true
            advanceToNextStep()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct battery optimization intent failed: ${e.message}")
            try {
                // Fallback to system battery optimization settings
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun startFirstPromiseRecording() {
        if (!hasAudioPermission) {
            Toast.makeText(this, "Microphone permission is required to record.", Toast.LENGTH_SHORT).show()
            currentStep = Step.MICROPHONE
            return
        }

        isRecording = true
        recordingCountdown = 15
        hasTempRecorded = false

        MemoRecorder.startRecording(
            this,
            onError = { err ->
                Log.e(TAG, "First promise recording error: $err")
                Toast.makeText(this, "Recording error: $err", Toast.LENGTH_SHORT).show()
                isRecording = false
            },
            onMaxDurationReached = {
                Log.d(TAG, "First promise recording 15s cap reached")
                isRecording = false
                hasTempRecorded = true
            }
        )

        lifecycleScope.launch {
            while (isRecording && recordingCountdown > 0) {
                delay(1000)
                if (!isRecording) break
                recordingCountdown--
            }
            if (isRecording) {
                stopFirstPromiseRecording()
            }
        }
    }

    private fun stopFirstPromiseRecording() {
        isRecording = false
        val success = MemoRecorder.stopRecording(this)
        hasTempRecorded = success
        if (!success) {
            Toast.makeText(this, "Recording was too short. Speak your promise and tap Stop.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelFirstPromiseRecording() {
        isRecording = false
        MemoPlayer.stopPlayback()
        isPlayingPreview = false
        MemoRecorder.cancelRecording(this)
        hasTempRecorded = false
    }

    private fun playPreview() {
        val tempPath = MemoStorage.getTempPath(this)
        isPlayingPreview = true
        MemoPlayer.startPlayback(
            this,
            tempPath,
            onCompletion = { isPlayingPreview = false },
            onError = { err ->
                isPlayingPreview = false
                Toast.makeText(this, "Playback error: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun saveFirstPromise() {
        MemoPlayer.stopPlayback()
        isPlayingPreview = false

        val saved = MemoStorage.saveMemo(this)
        if (saved) {
            // Mark onboarding completed in preferences
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                .apply()

            Toast.makeText(this, "First promise recorded!", Toast.LENGTH_SHORT).show()

            // Navigate to MainActivity and clear onboarding from backstack
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(mainIntent)
            finish()
        } else {
            Toast.makeText(this, "Please record a valid promise before proceeding.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // Allow going back to a previous onboarding step, or exit app if on first step
        when (currentStep) {
            Step.NOTIFICATIONS -> finish()
            Step.MICROPHONE -> currentStep = Step.NOTIFICATIONS
            Step.EXACT_ALARMS -> currentStep = Step.MICROPHONE
            Step.BATTERY_HARDENING -> currentStep = Step.EXACT_ALARMS
            Step.FIRST_PROMISE -> currentStep = Step.BATTERY_HARDENING
        }
    }
}

@Composable
fun OnboardingScreen(
    currentStep: OnboardingActivity.Step,
    hasNotificationPermission: Boolean,
    hasAudioPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    isBatteryOptimizationsIgnored: Boolean,
    isRecording: Boolean,
    recordingCountdown: Int,
    hasTempRecorded: Boolean,
    isPlayingPreview: Boolean,
    onRequestNotification: () -> Unit,
    onRequestAudio: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestIgnoreBattery: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayPreview: () -> Unit,
    onSavePromise: () -> Unit,
    onNextStep: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "MORRO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = Color(0xFFFF9E1B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Commitment-Driven Wake-Up",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFE0B2)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step Progress Indicator
            StepProgressBar(currentStep = currentStep)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Step Content Card
        AnimatedContent(
            targetState = currentStep,
            label = "StepTransition"
        ) { step ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (step) {
                        OnboardingActivity.Step.NOTIFICATIONS -> {
                            StepNotificationContent(
                                isGranted = hasNotificationPermission,
                                onRequest = onRequestNotification,
                                onNext = onNextStep,
                                onOpenSettings = onOpenAppSettings
                            )
                        }
                        OnboardingActivity.Step.MICROPHONE -> {
                            StepMicrophoneContent(
                                isGranted = hasAudioPermission,
                                onRequest = onRequestAudio,
                                onNext = onNextStep,
                                onOpenSettings = onOpenAppSettings
                            )
                        }
                        OnboardingActivity.Step.EXACT_ALARMS -> {
                            StepExactAlarmContent(
                                isGranted = hasExactAlarmPermission,
                                onRequest = onRequestExactAlarm,
                                onNext = onNextStep
                            )
                        }
                        OnboardingActivity.Step.BATTERY_HARDENING -> {
                            StepBatteryContent(
                                isIgnored = isBatteryOptimizationsIgnored,
                                onRequest = onRequestIgnoreBattery,
                                onNext = onNextStep
                            )
                        }
                        OnboardingActivity.Step.FIRST_PROMISE -> {
                            StepFirstPromiseContent(
                                isRecording = isRecording,
                                countdown = recordingCountdown,
                                hasRecorded = hasTempRecorded,
                                isPlayingPreview = isPlayingPreview,
                                onStart = onStartRecording,
                                onStop = onStopRecording,
                                onCancel = onCancelRecording,
                                onPlay = onPlayPreview,
                                onSave = onSavePromise
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer Note
        Text(
            text = "100% offline • No accounts • Local storage only",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StepProgressBar(currentStep: OnboardingActivity.Step) {
    val totalSteps = OnboardingActivity.Step.values().size
    val currentIndex = currentStep.ordinal

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index <= currentIndex) Color(0xFFFF9E1B) else Color(0xFF332200),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 1: NOTIFICATIONS
// -------------------------------------------------------------
@Composable
fun StepNotificationContent(
    isGranted: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Text(
        text = "1. Notifications",
        style = MaterialTheme.typography.titleLarge,
        color = Color(0xFFFF9E1B),
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Morro wakes you with a full-screen alarm over your lock screen. Android requires notification access to display this window and alert you when it's time to wake.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFFFCC80),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (isGranted) {
        Text(
            text = "✓ Notification access granted",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Microphone")
        }
    } else {
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Allow Notifications")
        }

        OutlinedButton(
            onClick = onOpenSettings,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open App Settings")
        }
    }
}

// -------------------------------------------------------------
// STEP 2: MICROPHONE
// -------------------------------------------------------------
@Composable
fun StepMicrophoneContent(
    isGranted: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Text(
        text = "2. Microphone Access",
        style = MaterialTheme.typography.titleLarge,
        color = Color(0xFFFF9E1B),
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Morro requires microphone permission to record your daily 15-second commitments. Your audio never leaves this device — there are zero network calls and no cloud storage.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFFFCC80),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (isGranted) {
        Text(
            text = "✓ Microphone access granted",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Exact Alarms")
        }
    } else {
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Allow Microphone")
        }

        OutlinedButton(
            onClick = onOpenSettings,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open App Settings")
        }
    }
}

// -------------------------------------------------------------
// STEP 3: EXACT ALARMS
// -------------------------------------------------------------
@Composable
fun StepExactAlarmContent(
    isGranted: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "3. Exact Timing",
        style = MaterialTheme.typography.titleLarge,
        color = Color(0xFFFF9E1B),
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "To preserve battery, Android delays ordinary timers by up to 15 minutes. Morro needs exact alarm permission to wake you at the precise minute you set.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFFFCC80),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (isGranted) {
        Text(
            text = "✓ Exact timing enabled",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Battery Hardening")
        }
    } else {
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Allow Exact Alarms")
        }
    }
}

// -------------------------------------------------------------
// STEP 4: BATTERY HARDENING (CRITICAL)
// -------------------------------------------------------------
@Composable
fun StepBatteryContent(
    isIgnored: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        text = "4. OEM Battery Hardening",
        style = MaterialTheme.typography.titleLarge,
        color = Color(0xFFFF9E1B),
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Android and device manufacturers (Samsung, Xiaomi, Nothing, Pixel) aggressively terminate background alarms overnight to save power.\n\nTo ensure your alarm reliably fires while your phone is asleep, Morro must be exempted from battery optimizations.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFFFCC80),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (isIgnored) {
        Text(
            text = "✓ Battery optimization disabled",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Record First Promise")
        }
    } else {
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disable Battery Optimization")
        }

        TextButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Continue anyway (Not Recommended)",
                color = Color(0xFF757575),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 5: THE FIRST PROMISE
// -------------------------------------------------------------
@Composable
fun StepFirstPromiseContent(
    isRecording: Boolean,
    countdown: Int,
    hasRecorded: Boolean,
    isPlayingPreview: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit
) {
    Text(
        text = "5. Your First Promise",
        style = MaterialTheme.typography.titleLarge,
        color = Color(0xFFFF9E1B),
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Morro requires an existing commitment to play when tomorrow's alarm rings.\n\nSpeak out loud: what is the ONE thing you commit to doing tomorrow morning? (Max 15 seconds)",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFFFCC80),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    when {
        isRecording -> {
            Text(
                text = "Recording: $countdown s",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFF9E1B),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Recording")
            }
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
        hasRecorded -> {
            Text(
                text = if (isPlayingPreview) "Playing preview…" else "Commitment recorded",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFFCC80)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = !isPlayingPreview,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Preview")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFCC80)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Re-record")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Enter Morro", fontWeight = FontWeight.Bold)
            }
        }
        else -> {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Recording (15s)")
            }
            Text(
                text = "You must record your first promise to finish setup.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )
        }
    }
}
