package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.OemFamily
import com.morro.alarm.OemGuidance
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.morroHairline

/**
 * OemGuidanceCard — the door to the OEM settings screen, shown before anything has gone wrong.
 *
 * It sits under the permission rows on purpose: the switches it points at live outside the app, so
 * no permission dialog can fix them, and a person only finds them if the app says where they are.
 * The copy states what the phone does, not what the person failed to do.
 */
@Composable
fun OemGuidanceCard(
    guidance: OemGuidance,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MorroShapes.Card)
            .background(MorroColors.SurfaceCard)
            .morroHairline(radius = 16.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Alarm reliability",
            color = MorroColors.TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (guidance.family == OemFamily.GENERIC) {
                "Android can pause background apps, which can delay an alarm."
            } else {
                "${guidance.brandLabel} blocks alarms from starting unless you allow it."
            },
            color = MorroColors.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "One setting to check",
                color = MorroColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Open settings",
                color = MorroColors.Honey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, MorroColors.BorderStrong, RoundedCornerShape(50))
                    .clickable(onClick = onOpen)
                    // 48dp tall target, matching the permission rows above.
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 18.dp)
            )
        }
    }
}
