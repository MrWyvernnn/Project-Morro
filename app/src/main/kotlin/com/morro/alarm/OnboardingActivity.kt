package com.morro.alarm

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.DriftingClouds
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroHairline

/**
 * Permissions onboarding — one screen, two rows, one optional hardening card.
 *
 * The old four-step wizard is gone. Each row requests its own permission inline and flips to an
 * "Allowed" check the moment the system returns. Battery optimisation and exact-alarm access live
 * in a compact reliability card: they matter for reliability, so they are surfaced clearly, but
 * they can never block the person from reaching the dashboard.
 *
 * Writing `onboarding_completed` happens only on Continue, so a returning user who finished the
 * intro earlier lands on this screen once and never again after that.
 */
class OnboardingActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "morro_prefs"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        /** True when permissions onboarding finished (the memo may still be recorded later). */
        fun isOnboardingComplete(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }
    }

    private val tag = "OnboardingActivity"

    private var hasNotificationPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var hasExactAlarmPermission by mutableStateOf(false)
    private var isBatteryIgnored by mutableStateOf(false)
    private var notificationDenied by mutableStateOf(false)
    private var audioDenied by mutableStateOf(false)

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshSystemState()
        notificationDenied = !granted && !hasNotificationPermission
    }

    private val audioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshSystemState()
        audioDenied = !granted && !hasAudioPermission
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSystemState()

        setContent {
            MorroTheme {
                PermissionsScreen(
                    hasNotificationPermission = hasNotificationPermission,
                    hasAudioPermission = hasAudioPermission,
                    hasExactAlarmPermission = hasExactAlarmPermission,
                    isBatteryIgnored = isBatteryIgnored,
                    notificationDenied = notificationDenied,
                    audioDenied = audioDenied,
                    onRequestNotifications = { requestNotifications() },
                    onRequestAudio = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onRequestExactAlarm = { requestExactAlarm() },
                    onRequestBattery = { requestIgnoreBattery() },
                    onContinue = { finishOnboarding() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemState()
    }

    private fun refreshSystemState() {
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Toast.makeText(this, "Notifications are already allowed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e: Exception) {
                Log.w(tag, "Exact alarm settings unavailable: ${e.message}")
                openAppSettings()
            }
        } else {
            hasExactAlarmPermission = true
        }
    }

    private fun requestIgnoreBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (e: Exception) {
            Log.w(tag, "Battery exemption intent failed: ${e.message}")
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    /** Continue works regardless of what was granted; the dashboard explains anything missing. */
    private fun finishOnboarding() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .commit()

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}

@Composable
fun PermissionsScreen(
    hasNotificationPermission: Boolean,
    hasAudioPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    isBatteryIgnored: Boolean,
    notificationDenied: Boolean,
    audioDenied: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestAudio: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestBattery: () -> Unit,
    onContinue: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        DriftingClouds(intensity = 0.75f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Two permissions",
                color = MorroColors.TextPrimary,
                fontSize = 30.sp,
                lineHeight = 37.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "One wakes you, one hears your promise.",
                color = MorroColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            PermissionRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                why = "Shows the alarm full-screen over your lock screen.",
                granted = hasNotificationPermission,
                onAllow = onRequestNotifications
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionRow(
                icon = Icons.Filled.Mic,
                title = "Microphone",
                why = "Records the promise Morro plays back each morning.",
                granted = hasAudioPermission,
                onAllow = onRequestAudio
            )

            if (notificationDenied || audioDenied) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (notificationDenied && audioDenied) {
                        "Both were declined. Morro will still open; you can allow them in Settings whenever you're ready."
                    } else if (notificationDenied) {
                        "Notifications were declined. Without them the alarm can't take over the screen."
                    } else {
                        "The microphone was declined. Without it Morro can't record a promise."
                    },
                    color = MorroColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SystemAccessCard(
                hasExactAlarmPermission = hasExactAlarmPermission,
                isBatteryIgnored = isBatteryIgnored,
                onRequestExactAlarm = onRequestExactAlarm,
                onRequestBattery = onRequestBattery
            )

            Spacer(modifier = Modifier.height(28.dp))

            MorroPrimaryButton(
                text = "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** One permission, one row: icon badge, what it does, and its live status on the right. */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    why: String,
    granted: Boolean,
    onAllow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorroShapes.Card)
            .background(MorroColors.SurfaceCard)
            .morroHairline(
                radius = 16.dp,
                color = if (granted) MorroColors.BorderStrong else MorroColors.BorderSubtle
            )
            .clickable(enabled = !granted, onClick = onAllow)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MorroColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MorroColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = why,
                color = MorroColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MorroColors.Honey,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Allowed",
                    color = MorroColors.Honey,
                    fontSize = 12.sp
                )
            }
        } else {
            Text(
                text = "Allow",
                color = MorroColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MorroColors.BorderStrong, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

/**
 * The whole reliability story in one row: what it is, how many settings are left, and the single
 * action that resolves the next one. Optional, and Continue never depends on it.
 */
@Composable
private fun SystemAccessCard(
    hasExactAlarmPermission: Boolean,
    isBatteryIgnored: Boolean,
    onRequestExactAlarm: () -> Unit,
    onRequestBattery: () -> Unit
) {
    val missing = (if (hasExactAlarmPermission) 0 else 1) + (if (isBatteryIgnored) 0 else 1)
    val ready = missing == 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorroShapes.Card)
            .background(MorroColors.SurfaceCard)
            .morroHairline(radius = 16.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Alarm reliability",
                color = MorroColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            if (ready) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MorroColors.Honey,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // One line on what the card is for, before the status sentence that says where you stand.
        Text(
            text = "Keeps your alarm exact and lets it ring through sleep mode.",
            color = MorroColors.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (missing) {
                    0 -> "Exact timing and battery are set."
                    1 -> "One setting left to allow."
                    else -> "Two settings left to allow."
                },
                color = MorroColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )

            if (!ready) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Allow",
                    color = MorroColors.Honey,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MorroColors.BorderStrong, RoundedCornerShape(50))
                        .clickable {
                            if (!hasExactAlarmPermission) onRequestExactAlarm() else onRequestBattery()
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
    }
}
