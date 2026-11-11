package com.morro.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography

/**
 * BatteryOptimizationDebugActivity:
 * Simple debug screen (Slice 3 OEM Battery Hardening) to request that the user
 * whitelist Morro from Android battery optimizations / Doze mode.
 */
class BatteryOptimizationDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MorroTheme {
                val colors = morroColors()
                val typo = morroTypography()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.canvas
                ) {
                    BatteryOptimizationDebugScreen(
                        isIgnoringOptimizations = isIgnoringBatteryOptimizations(),
                        onRequestWhitelist = { requestIgnoreBatteryOptimizations() },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        }
        return true
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general battery saver settings if direct intent fails
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }
}

@Composable
fun BatteryOptimizationDebugScreen(
    isIgnoringOptimizations: Boolean,
    onRequestWhitelist: () -> Unit,
    onBack: () -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "OEM Battery Hardening (Debug)",
            style = typo.headlineMedium,
            color = colors.accentTactical,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
            shape = MorroShapes.Card
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val statusText = if (isIgnoringOptimizations) {
                    "STATUS: Whitelisted (Battery optimizations ignored)"
                } else {
                    "STATUS: Optimized (Alarms may be delayed or killed by Doze)"
                }
                val statusColor = if (isIgnoringOptimizations) colors.success else colors.error

                Text(
                    text = statusText,
                    style = typo.bodyMedium,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "To guarantee alarm reliability with the screen off and device idle, " +
                    "Morro must be excluded from system battery optimizations.",
            style = typo.bodyMedium,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestWhitelist,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentTactical,
                contentColor = Color.White
            ),
            shape = MorroShapes.Button
        ) {
            Text("Request Battery Whitelist", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.textPrimary
            ),
            shape = MorroShapes.Button
        ) {
            Text("Back to Main", color = colors.textPrimary)
        }
    }
}