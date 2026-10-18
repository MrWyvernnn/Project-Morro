package com.morro.alarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * MainActivity: Entry point for Morro alarm app.
 * Displays a button to schedule a test alarm 1 minute from now.
 * Requests exact alarm and notification permissions at runtime.
 */
class MainActivity : ComponentActivity() {

    // Permission launcher for POST_NOTIFICATIONS (API 33+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // User denied notification permission - alarm won't be visible in notification shade
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions on first launch
        requestNotificationPermission()
        requestExactAlarmPermission()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onScheduleAlarm = { scheduleAlarm() }
                    )
                }
            }
        }
    }

    /**
     * Request POST_NOTIFICATIONS permission (required on API 33+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Request exact alarm permission.
     * On API 31+, this requires user to grant permission via system settings.
     */
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Direct user to settings to grant exact alarm permission
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    /**
     * Schedule alarm to fire in 1 minute using AlarmScheduler
     */
    private fun scheduleAlarm() {
        val triggerTimeMillis = System.currentTimeMillis() + 60_000L // 1 minute from now
        AlarmScheduler.scheduleAlarm(this, triggerTimeMillis)
    }
}

@Composable
fun MainScreen(onScheduleAlarm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onScheduleAlarm,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = "Fire Alarm in 1 Minute",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
