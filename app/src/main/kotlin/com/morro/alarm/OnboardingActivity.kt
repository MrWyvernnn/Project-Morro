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
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroColors
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * OnboardingActivity: Strict 4-step onboarding wizard.
 *
 * State Machine:
 *   Step 1: Request POST_NOTIFICATIONS and RECORD_AUDIO.
 *   Step 2: Request SCHEDULE_EXACT_ALARM via ACTION_REQUEST_SCHEDULE_EXACT_ALARM.
 *   Step 3: Trigger Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS for our package.
 *   Step 4: Force the user to record their first 15-second memo using MemoRecorder and save it to MemoStorage.
 *
 * Upon Step 4 completion, saves is_onboarding_complete = true in SharedPreferences and navigates to MainActivity.
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

    /** Strict 4-step onboarding state machine */
    enum class Step {
        PERMISSIONS,       // Step 1: POST_NOTIFICATIONS & RECORD_AUDIO
        EXACT_ALARMS,      // Step 2: SCHEDULE_EXACT_ALARM
        BATTERY_HARDENING, // Step 3: IGNORE_BATTERY_OPTIMIZATIONS
        FIRST_PROMISE      // Step 4: First 15s commitment memo
    }

    private var currentStep by mutableStateOf(Step.PERMISSIONS)

    // System & Permission states
    private var hasNotificationPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var hasExactAlarmPermission by mutableStateOf(false)
    private var isBatteryOptimizationsIgnored by mutableStateOf(false)

    // First Promise recording state
    private var isRecording by mutableStateOf(false)
    private var recordingCountdown by mutableStateOf(15)
    private var hasTempRecorded by mutableStateOf(false)
    private var isPlayingPreview by mutableStateOf(false)

    // Permission launchers for Step 1
    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        updateSystemStates()
        if (hasRequiredPermissions()) {
            advanceToNextStep()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemStates()
        determineInitialStep()

        setContent {
            MorroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = morroColors().canvas
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
                        onRequestPermissions = { requestStep1Permissions() },
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
        updateSystemStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        MemoPlayer.release()
        MemoRecorder.release()
    }

    private fun updateSystemStates() {
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptimizationsIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)

        hasTempRecorded = MemoStorage.hasTempMemo(this)
    }

    private fun hasRequiredPermissions(): Boolean {
        return hasNotificationPermission && hasAudioPermission
    }

    private fun determineInitialStep() {
        currentStep = when {
            !hasRequiredPermissions() -> Step.PERMISSIONS
            !hasExactAlarmPermission -> Step.EXACT_ALARMS
            !isBatteryOptimizationsIgnored -> Step.BATTERY_HARDENING
            else -> Step.FIRST_PROMISE
        }
    }

    private fun advanceToNextStep() {
        currentStep = when (currentStep) {
            Step.PERMISSIONS -> Step.EXACT_ALARMS
            Step.EXACT_ALARMS -> Step.BATTERY_HARDENING
            Step.BATTERY_HARDENING -> Step.FIRST_PROMISE
            Step.FIRST_PROMISE -> Step.FIRST_PROMISE
        }
    }

    private fun requestStep1Permissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!hasAudioPermission) {
            permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        }

        if (permissionsToRequest.isNotEmpty()) {
            multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            advanceToNextStep()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch exact alarm settings: ${e.message}")
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
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            currentStep = Step.PERMISSIONS
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
                isRecording = MemoRecorder.isRecording()
            },
            onMaxDurationReached = {
                Log.d(TAG, "First promise recording 15s cap reached")
                isRecording = MemoRecorder.isRecording()
                hasTempRecorded = true
            }
        )

        lifecycleScope.launch {
            while (MemoRecorder.isRecording() && recordingCountdown > 0) {
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
            // Synchronous commit: MainActivity.isOnboardingComplete() reads this flag
            // immediately after we launch MainActivity; it must be on disk before the
            // Intent fires.
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONBOARDING_COMPLETED, true)
                .commit()

            Toast.makeText(this, "First promise recorded!", Toast.LENGTH_SHORT).show()

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
        when (currentStep) {
            Step.PERMISSIONS -> finish()
            Step.EXACT_ALARMS -> currentStep = Step.PERMISSIONS
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
    onRequestPermissions: () -> Unit,
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

    val colors = morroColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
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
                color = colors.accentTactical
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Commitment-Driven Wake-Up",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            StepProgressBar(currentStep = currentStep, colors = colors)
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = currentStep,
            label = "StepTransition"
        ) { step ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
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
                        OnboardingActivity.Step.PERMISSIONS -> {
                            StepPermissionsContent(
                                hasNotification = hasNotificationPermission,
                                hasAudio = hasAudioPermission,
                                onRequest = onRequestPermissions,
                                onNext = onNextStep,
                                onOpenSettings = onOpenAppSettings,
                                colors = colors
                            )
                        }
                        OnboardingActivity.Step.EXACT_ALARMS -> {
                            StepExactAlarmContent(
                                isGranted = hasExactAlarmPermission,
                                onRequest = onRequestExactAlarm,
                                onNext = onNextStep,
                                colors = colors
                            )
                        }
                        OnboardingActivity.Step.BATTERY_HARDENING -> {
                            StepBatteryContent(
                                isIgnored = isBatteryOptimizationsIgnored,
                                onRequest = onRequestIgnoreBattery,
                                onNext = onNextStep,
                                colors = colors
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
                                onSave = onSavePromise,
                                colors = colors
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "100% offline • No accounts • Local storage only",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDisabled,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StepProgressBar(currentStep: OnboardingActivity.Step, colors: com.morro.alarm.ui.theme.MorroColorPalette) {
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
                        color = if (index <= currentIndex) colors.accentTactical else colors.borderSubtle,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 1: PERMISSIONS (Notifications & Microphone)
// -------------------------------------------------------------
@Composable
fun StepPermissionsContent(
    hasNotification: Boolean,
    hasAudio: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
    colors: com.morro.alarm.ui.theme.MorroColorPalette
) {
    val bothGranted = hasNotification && hasAudio

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Friendly smiling ghost mascot centered, with subtle pink/orange ambient glow.
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(colors.canvas.copy(alpha = 0f)),
            contentAlignment = Alignment.Center
        ) {
            MorroGhostMascot(
                modifier = Modifier.size(96.dp, 120.dp),
                happy = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "1. Required Permissions",
            style = MaterialTheme.typography.titleLarge,
            color = colors.accentTactical,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Morro requires two permissions to function:\n\n• Notifications: To display full-screen alarm alerts over your lock screen.\n• Microphone: To record your daily commitment memos.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (bothGranted) {
            Text(
                text = "✓ Notifications & Microphone granted",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.success,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentTactical, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next: Exact Timing")
            }
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentTactical, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permissions")
            }

            OutlinedButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App Settings")
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 2: EXACT ALARMS
// -------------------------------------------------------------
@Composable
fun StepExactAlarmContent(
    isGranted: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit,
    colors: com.morro.alarm.ui.theme.MorroColorPalette
) {
    Text(
        text = "2. Exact Timing",
        style = MaterialTheme.typography.titleLarge,
        color = colors.accentTactical,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "To preserve battery, Android delays ordinary timers by up to 15 minutes. Morro needs exact alarm permission to wake you at the precise minute you set.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (isGranted) {
        Text(
            text = "✓ Exact timing enabled",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.success,
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentTactical, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Battery Hardening")
        }
    } else {
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentTactical, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Allow Exact Alarms")
        }
    }
}

// -------------------------------------------------------------
// STEP 3: BATTERY HARDENING
// -------------------------------------------------------------
@Composable
fun StepBatteryContent(
    isIgnored: Boolean,
    onRequest: () -> Unit,
    onNext: () -> Unit,
    colors: com.morro.alarm.ui.theme.MorroColorPalette
) {
    Text(
        text = "3. OEM Battery Hardening",
        style = MaterialTheme.typography.titleLarge,
        color = colors.accentTactical,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Android and device manufacturers (Samsung, Xiaomi, Nothing, Pixel) aggressively terminate background alarms overnight to save power.\n\nTo ensure your alarm reliably fires while your phone is asleep, Morro must be exempted from battery optimizations.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (isIgnored) {
        Text(
            text = "✓ Battery optimization disabled",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.success,
            fontWeight = FontWeight.Bold
        )
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentTactical, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next: Record First Promise")
        }
    } else {
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = Color.White),
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
                color = colors.textDisabled,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 4: THE FIRST PROMISE
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
    onSave: () -> Unit,
    colors: com.morro.alarm.ui.theme.MorroColorPalette
) {
    Text(
        text = "4. Your First Promise",
        style = MaterialTheme.typography.titleLarge,
        color = colors.accentTactical,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Morro requires an existing commitment to play when tomorrow's alarm rings.\n\nSpeak out loud: what is the ONE thing you commit to doing tomorrow morning? (Max 15 seconds)",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    when {
        isRecording -> {
            Text(
                text = "Recording: $countdown s",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.accentTactical,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = colors.success, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Recording")
            }
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
        hasRecorded -> {
            Text(
                text = if (isPlayingPreview) "Playing preview…" else "Commitment recorded",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = !isPlayingPreview,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentTactical, contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Preview")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Re-record")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = colors.success, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Enter Morro", fontWeight = FontWeight.Bold)
            }
        }
        else -> {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentTactical, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Recording (15s)")
            }
            Text(
                text = "You must record your first promise to finish setup.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textDisabled,
                textAlign = TextAlign.Center
            )
        }
    }
}
