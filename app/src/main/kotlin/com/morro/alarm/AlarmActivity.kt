package com.morro.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } else {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        AlarmSoundPlayer.start(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF8C00),
                    background = Color(0xFF000000),
                    surface = Color(0xFF1A1200),
                    onPrimary = Color.Black,
                    onBackground = Color(0xFFFFE0B2),
                    onSurface = Color(0xFFFFCC80)
                )
            ) {
                AlarmScreen(
                    onDismiss = { dismissAlarm() },
                    onSnooze = { snoozeAlarm() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmSoundPlayer.start(this)
    }

    override fun onPause() {
        super.onPause()
        // Keep sound running if just briefly obscured; stop only on explicit dismiss/snooze
    }

    private fun dismissAlarm() {
        AlarmSoundPlayer.stop()
        AlarmScheduler.cancelAlarm(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(1001)
        finish()
    }

    private fun snoozeAlarm() {
        AlarmSoundPlayer.stop()
        AlarmScheduler.snoozeAlarm(this, 10)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(1001)
        finish()
    }

    override fun onBackPressed() {
        // Must use Dismiss or Snooze buttons; back does nothing
    }
}

@Composable
fun AlarmScreen(onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val currentTime = remember {
        mutableStateOf(SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            currentTime.value = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing amber ring
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .background(Color(0x15FF8C00), shape = androidx.compose.foundation.shape.CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(Color(0x30FF8C00), shape = androidx.compose.foundation.shape.CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color(0x50FF8C00), shape = androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = currentTime.value,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFFFFE0B2),
                    fontSize = 56.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "ALARM",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFFFCC80)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Promise what you're doing today.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFB74D)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f).height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF8C00),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Snooze (10m)", fontSize = 20.sp)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    )
                ) {
                    Text("Dismiss", fontSize = 20.sp)
                }
            }
        }
    }
}
