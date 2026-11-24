package com.morro.alarm

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.MorroCard
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroTheme

/**
 * The one screen that explains why this particular phone might not let Morro ring.
 *
 * It exists because a missing ring is invisible: by the time someone suspects the app, the morning
 * is already ruined. Reaching this screen before that happens is the whole point, which is why it
 * is linked from onboarding as well as Settings.
 *
 * Every row is a switch that lives in the phone's settings, not in Morro, so the screen never
 * pretends to be able to change them itself. It opens the right screen and says what to look for.
 */
class OemGuidanceActivity : ComponentActivity() {

    private var batteryUnrestricted by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        batteryUnrestricted = isBatteryUnrestricted()

        setContent {
            MorroTheme {
                OemGuidanceScreen(
                    guidance = OemReliability.current(),
                    batteryUnrestricted = batteryUnrestricted,
                    onOpenAutostart = { OemReliability.openAutostartSettings(this) },
                    onOpenBattery = { OemReliability.openBatterySettings(this) },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The person may have just flipped the switch in the system screen behind this one.
        batteryUnrestricted = isBatteryUnrestricted()
    }

    private fun isBatteryUnrestricted(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
}

@Composable
fun OemGuidanceScreen(
    guidance: OemGuidance,
    batteryUnrestricted: Boolean,
    onOpenAutostart: () -> Unit,
    onOpenBattery: () -> Unit,
    onBack: () -> Unit,
) {
    val opensAutostart = guidance.family != OemFamily.SAMSUNG && guidance.family != OemFamily.GENERIC

    Box(modifier = Modifier.fillMaxSize()) {
        MorroSky(intensity = 0.75f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Alarm reliability",
                color = MorroColors.TextPrimary,
                fontSize = 30.sp,
                lineHeight = 37.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = guidance.headline,
                color = MorroColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            MorroCard {
                Column {
                    Text(
                        text = "What to change on ${guidance.brandLabel}",
                        color = MorroColors.TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    guidance.steps.forEachIndexed { index, step ->
                        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                        StepRow(number = index + 1, text = step)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MorroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (batteryUnrestricted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MorroColors.Honey,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (batteryUnrestricted) {
                            "Battery: unrestricted"
                        } else {
                            "Battery: restricted"
                        },
                        color = if (batteryUnrestricted) {
                            MorroColors.TextPrimary
                        } else {
                            MorroColors.TextSecondary
                        },
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            MorroPrimaryButton(
                text = if (opensAutostart) {
                    "Open ${guidance.brandLabel} settings"
                } else {
                    "Open battery settings"
                },
                onClick = if (opensAutostart) onOpenAutostart else onOpenBattery,
                modifier = Modifier.fillMaxWidth()
            )

            if (opensAutostart) {
                // Samsung exposes no autostart screen, so on those phones this is the primary door
                // and the button above already points at it.
                TextButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Battery settings",
                        color = MorroColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Done",
                    color = MorroColors.TextTertiary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** One numbered instruction. The number is the only decoration; the text carries the meaning. */
@Composable
private fun StepRow(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$number",
            color = MorroColors.Honey,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = MorroColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
