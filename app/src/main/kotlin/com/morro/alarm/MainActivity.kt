package com.morro.alarm

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) Toast.makeText(this, "Notifications disabled — alarm may not show", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        requestExactAlarmPermission()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF8C00),
                    secondary = Color(0xFFFFA726),
                    background = Color(0xFF000000),
                    surface = Color(0xFF1A1200),
                    onPrimary = Color.Black,
                    onSecondary = Color.Black,
                    onBackground = Color(0xFFFFE0B2),
                    onSurface = Color(0xFFFFCC80)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF000000)) {
                    MainScreen(
                        context = this,
                        onScheduleAlarm = { timeMillis -> scheduleAlarm(timeMillis) },
                        onTestAlarm = { scheduleTestAlarm() },
                        onCancel = { cancelAlarm() }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun scheduleAlarm(timeMillis: Long) {
        AlarmScheduler.scheduleAlarm(this, timeMillis)
        Toast.makeText(this, "Alarm set for ${AlarmScheduler.getFormattedTime(this)}", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleTestAlarm() {
        val now = System.currentTimeMillis()
        AlarmScheduler.scheduleAlarm(this, now + 60_000L)
        Toast.makeText(this, "Test alarm in 1 min", Toast.LENGTH_SHORT).show()
    }

    private fun cancelAlarm() {
        AlarmScheduler.cancelAlarm(this)
        Toast.makeText(this, "Alarm canceled", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(
    context: Context,
    onScheduleAlarm: (Long) -> Unit,
    onTestAlarm: () -> Unit,
    onCancel: () -> Unit
) {
    val currentTimeStr = remember {
        val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        mutableStateOf(fmt.format(java.util.Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentTimeStr.value = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date())
        }
    }

    val alarmTime = AlarmScheduler.getNextAlarmTime(context)
    val alarmFormatted = AlarmScheduler.getFormattedTime(context)
    val hasActiveAlarm = alarmTime != null && alarmTime > System.currentTimeMillis()

    // Countdown to next alarm
    val countdownStr = remember(alarmTime) {
        if (hasActiveAlarm && alarmTime != null) {
            val diffMillis = maxOf(0, alarmTime - System.currentTimeMillis())
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis / (1000 * 60)) % 60
            if (hours > 0) "Rings in $hours hours $minutes mins" else "Rings in $minutes mins"
        } else {
            "No alarm set"
        }
    }

    var vibrateEnabled by remember { mutableStateOf(true) }
    var snoozeMinutes by remember { mutableStateOf(10) }
    val snoozeOptions = listOf(5 to "5m", 10 to "10m", 15 to "15m")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Hero Clock + Next Alarm Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTimeStr.value,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFFFFE0B2),
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasActiveAlarm) "Next alarm at $alarmFormatted" else "No alarm scheduled",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFFFB74D)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = countdownStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFA726)
                )
            }
        }

        // Vibrate Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = vibrateEnabled,
                onCheckedChange = { vibrateEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF8C00),
                    checkedTrackColor = Color(0xFFFFA726),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (vibrateEnabled) "Vibrate on alarm: ON" else "Vibrate on alarm: OFF",
                color = Color(0xFFFFCC80),
                fontSize = 16.sp
            )
        }

        // Snooze settings selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Snooze:", color = Color(0xFFFFCC80), fontSize = 16.sp)
            snoozeOptions.forEach { (mins, label) ->
                FilterChip(
                    selected = snoozeMinutes == mins,
                    onClick = { snoozeMinutes = mins },
                    label = { Text(label, color = if (snoozeMinutes == mins) Color.Black else Color(0xFFFFCC80)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF8C00),
                        containerColor = Color(0xFF1A1200)
                    )
                )
            }
        }

        // Time selector row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            var selectedHour by remember { mutableStateOf(7) }
            var selectedMinute by remember { mutableStateOf(0) }

            Button(
                onClick = {
                    val cal = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            selectedHour = h
                            selectedMinute = m
                            val targetCal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, h)
                                set(Calendar.MINUTE, m)
                                set(Calendar.SECOND, 0)
                                val targetMillis = timeInMillis
                                if (targetMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                            }
                            onScheduleAlarm(targetCal.timeInMillis)
                        },
                        selectedHour,
                        selectedMinute,
                        false
                    ).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8C00), contentColor = Color.Black)
            ) { Text("Set Alarm") }

            Button(
                onClick = { onTestAlarm() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
            ) { Text("Test +1 Min") }
        }

        if (hasActiveAlarm) {
            Button(
                onClick = { onCancel() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
            ) { Text("Cancel Alarm") }
        }
    }
}
