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
        if (!isGranted) Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        requestExactAlarmPermission()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
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
        Toast.makeText(this, "Alarm set", Toast.LENGTH_SHORT).show()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Hero status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hasActiveAlarm) "Alarm Active" else "No Active Alarm",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (hasActiveAlarm) Color(0xFFBB86FC) else Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasActiveAlarm) "Set for $alarmFormatted" else "Select a time below",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray
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
                        false // 24-hour format false
                    ).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
            ) { Text("Set Alarm") }

            Button(
                onClick = { onTestAlarm() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
            ) { Text("Test +1 Min") }
        }

        if (hasActiveAlarm) {
            Button(
                onClick = { onCancel() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679))
            ) { Text("Cancel Alarm") }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = currentTimeStr.value,
            style = MaterialTheme.typography.displayLarge,
            color = Color(0x55BB86FC),
            fontSize = 64.sp
        )
    }
}
