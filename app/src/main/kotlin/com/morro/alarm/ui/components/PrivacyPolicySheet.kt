package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography

/**
 * MorroPrivacyPolicySheet — Full production local-first privacy policy sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorroPrivacyPolicySheet(onDismiss: () -> Unit) {
    val colors = morroColors()
    val typo = morroTypography()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(colors.borderSubtle, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "PRIVACY POLICY",
                style = typo.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = colors.accentTactical
            )

            PolicySection(
                title = "100% Local-First Architecture",
                body = "Morro operates exclusively on your physical device. We do not operate cloud servers, user accounts, or remote databases."
            )

            PolicySection(
                title = "Camera & Visual Mission Processing (QR & Photo Missions)",
                body = "When you use the QR/Barcode Scanner or the 30-Item Photo Recognition Mission, camera frames are analyzed strictly in volatile device RAM using bundled, offline on-device machine learning (Google ML Kit). Photos taken to dismiss your alarm are never uploaded, transmitted, or saved to your public photo gallery, and are immediately discarded from memory once verification completes."
            )

            PolicySection(
                title = "Microphone & Voice Commitment Storage",
                body = "Audio recorded for your Voice Commitment Memo is saved strictly inside Android's sandboxed app-private directory (context.filesDir/commitment_memo.m4a). No external app, cloud backup, or server can access your voice recordings."
            )

            PolicySection(
                title = "Psychological Onboarding Responses",
                body = "Your answers to the onboarding interrogation are stored locally in encrypted/sandboxed SharedPreferences solely to configure your local alarm dashboard and mission defaults."
            )

            PolicySection(
                title = "Zero Telemetry & Zero Third-Party Trackers",
                body = "Morro contains zero advertising SDKs, zero analytics trackers (no Firebase Analytics, Mixpanel, or Meta SDKs), and does not request android.permission.INTERNET for data collection."
            )

            PolicySection(
                title = "Hardware & System Permissions",
                body = "SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM: Required to trigger your alarm at the exact millisecond scheduled during Android Doze mode.\n\n" +
                        "CAMERA: Required only when registering or completing QR/Barcode and Object Photo missions.\n\n" +
                        "RECORD_AUDIO: Required only when recording a personal Voice Commitment memo.\n\n" +
                        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Requested so OEM battery killers do not silently terminate your morning alarm."
            )

            PolicySection(
                title = "Complete Data Destruction",
                body = "Clearing app storage or uninstalling Morro permanently and irrecoverably erases all alarms, preferences, and voice recordings from your hardware."
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentTactical,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "CLOSE",
                    style = typo.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    val colors = morroColors()
    val typo = morroTypography()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = typo.bodyLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = colors.textPrimary
        )
        Text(
            text = body,
            style = typo.bodyMedium.copy(
                lineHeight = 20.sp
            ),
            color = colors.textSecondary
        )
    }
}