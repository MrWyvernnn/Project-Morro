package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.BuildConfig
import com.morro.alarm.ui.theme.MorroThemeChoice
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography

/**
 * MorroSettingsSheet — the few things a person can actually change, in one place.
 *
 * Almost every row is a door to something that already exists: the wake-up builder, the mission
 * picker inside it, and the privacy policy. Inventing settings here would mean storing values
 * nothing reads, so the sheet stays close to a directory rather than a form.
 *
 * The one real control is the theme switch. It is a switch rather than a picker because there are
 * exactly two themes; a list of two would be a menu to read where a toggle is enough.
 *
 * @param wakeUpLabel the current wake-up time, or "Not set"
 * @param missionLabel the mission that will get the person out of bed
 * @param oemLabel the phone's manufacturer, shown as the reliability row's value
 * @param themeChoice the theme in use right now, shown as the row's value
 * @param onAutumnThemeChange true for Autumn, false for Midnight
 * @param onOpenWakeUp opens the alarm builder
 * @param onOpenOemGuidance opens the manufacturer-specific reliability screen
 * @param onOpenPrivacy opens the privacy policy
 * @param onDismiss called on backdrop tap, drag-down, or Close
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorroSettingsSheet(
    wakeUpLabel: String,
    missionLabel: String,
    oemLabel: String,
    themeChoice: MorroThemeChoice,
    onAutumnThemeChange: (Boolean) -> Unit,
    onOpenWakeUp: () -> Unit,
    onOpenOemGuidance: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = morroShapes().Sheet,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Settings",
                style = typo.headlineLarge,
                color = colors.textPrimary
            )

            Text(
                text = "Morro keeps everything on this phone. These are the parts you can change.",
                style = typo.bodyMedium,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsRow(
                title = "Wake-up",
                value = wakeUpLabel,
                onClick = onOpenWakeUp
            )

            SettingsRow(
                title = "Mission",
                value = missionLabel,
                onClick = onOpenWakeUp
            )

            SettingsRow(
                title = "Alarm reliability",
                value = oemLabel,
                onClick = onOpenOemGuidance
            )

            SettingsToggleRow(
                title = "Autumn theme",
                value = themeChoice.label,
                checked = themeChoice == MorroThemeChoice.AUTUMN,
                onCheckedChange = onAutumnThemeChange
            )

            SettingsRow(
                title = "Privacy",
                value = "On-device only",
                onClick = onOpenPrivacy
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Morro ${BuildConfig.VERSION_NAME} · no account, no cloud, no trackers",
                style = typo.labelSmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = shapes.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.textPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Close",
                    style = typo.titleMedium
                )
            }
        }
    }
}

/**
 * A setting that is a value, not a door: title and current state on the left, the switch on the
 * right. The whole 56dp row is the target, same as [SettingsRow], so the switch is easy to hit and
 * the row and the switch never disagree about what is on.
 */
@Composable
private fun SettingsToggleRow(
    title: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shapes.Card)
            .background(colors.surfaceCard)
            .morroHairline(radius = 16.dp, color = colors.borderSubtle)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = title,
                style = typo.bodyLarge,
                color = colors.textPrimary
            )
            Text(
                text = value,
                style = typo.bodyMedium,
                fontSize = 12.sp,
                color = colors.textTertiary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        MorroSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * A door: title on the left, current value on the right.
 *
 * The value is on the row so the sheet answers "what is it now?" without opening anything, and the
 * whole row is the target at 56dp, which is comfortably past the 48dp minimum.
 */
@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shapes.Card)
            .background(colors.surfaceCard)
            .morroHairline(radius = 16.dp, color = colors.borderSubtle)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = typo.bodyLarge,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = typo.bodyMedium,
            fontSize = 13.sp,
            color = colors.textTertiary,
            textAlign = TextAlign.End
        )
    }
}
