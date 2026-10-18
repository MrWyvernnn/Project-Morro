package com.morro.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AlarmActivity: Full-screen activity that displays when alarm fires.
 * Shows over lock screen via manifest attributes (showWhenLocked, turnScreenOn).
 * Displays "ALARM FIRING" text and a dismiss button.
 * Stops alarm sound when dismissed.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For API < 27, set flags to show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } else {
            // API 27+ uses manifest attributes and setShowWhenLocked/setTurnScreenOn
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent {
            AlarmScreen(
                onDismiss = { dismissAlarm() }
            )
        }
    }

    /**
     * Stop alarm sound and finish activity
     */
    private fun dismissAlarm() {
        AlarmSoundPlayer.stop()
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing alarm
        // User must tap dismiss button
    }
}

@Composable
fun AlarmScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Text(
                text = "ALARM FIRING",
                fontSize = 48.sp,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .width(200.dp)
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Red
                )
            ) {
                Text(
                    text = "Dismiss",
                    fontSize = 24.sp
                )
            }
        }
    }
}
