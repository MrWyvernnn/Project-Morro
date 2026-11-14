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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.onboarding.AmbientGlow
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroGradients
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroGradientBorder

/**
 * Permissions onboarding — one screen, two rows, one optional hardening card.
 *
 * The old four-step wizard is gone. Each row requests its own permission inline and flips to a
 * pink "Granted" pill the moment the system returns. Battery optimisation and exact-alarm access
 * live in a compact terminal card: they matter for reliability, so they are surfaced clearly, but
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
        AmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "MORRO • ACCESS",
                color = MorroColors.AccentTactical,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Two quick permissions",
                color = MorroColors.TextPrimary,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "One wakes you. One hears your promise.",
                color = MorroColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "100% offline • no accounts • stored on this phone",
                color = MorroColors.TextTertiary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
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
            .morroGradientBorder(
                shape = MorroShapes.Card,
                width = 1.dp,
                brush = MorroGradients.horizontal(if (granted) 0.85f else 0.35f)
            )
            .clickable(enabled = !granted, onClick = onAllow)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MorroColors.AccentTactical.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MorroColors.AccentTactical,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MorroColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = why,
                color = MorroColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (granted) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MorroColors.AccentBubbly.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MorroColors.AccentBubbly,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Granted",
                    color = MorroColors.AccentBubbly,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = "Allow",
                color = MorroColors.AccentTactical,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, MorroColors.AccentTactical.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Reliability actions, styled as a terminal readout rather than a blocking step. Both actions are
 * optional and Continue never depends on them.
 */
@Composable
private fun SystemAccessCard(
    hasExactAlarmPermission: Boolean,
    isBatteryIgnored: Boolean,
    onRequestExactAlarm: () -> Unit,
    onRequestBattery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorroShapes.Card)
            .background(MorroColors.SurfaceCard)
            .border(1.dp, MorroColors.BorderSubtle, MorroShapes.Card)
            .padding(16.dp)
    ) {
        Text(
            text = "SYSTEM CHECK",
            color = MorroColors.TextTertiary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        TerminalLine(
            label = "exact timing",
            value = if (hasExactAlarmPermission) "ACTIVE" else "NEEDED",
            ok = hasExactAlarmPermission
        )
        Spacer(modifier = Modifier.height(4.dp))
        TerminalLine(
            label = "battery",
            value = if (isBatteryIgnored) "UNRESTRICTED" else "OPTIMISED",
            ok = isBatteryIgnored
        )

        if (!hasExactAlarmPermission || !isBatteryIgnored) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!hasExactAlarmPermission) {
                    TextButton(onClick = onRequestExactAlarm) {
                        Text(
                            text = "Fix timing",
                            color = MorroColors.AccentTactical,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
                if (!isBatteryIgnored) {
                    TextButton(onClick = onRequestBattery) {
                        Text(
                            text = "Optimise battery",
                            color = MorroColors.AccentTactical,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Optional, but phones that sleep hard can delay an alarm without them.",
            color = MorroColors.TextTertiary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun TerminalLine(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "> $label",
            color = MorroColors.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Text(
            text = value,
            color = if (ok) MorroColors.AccentBubbly else MorroColors.AccentTactical,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
