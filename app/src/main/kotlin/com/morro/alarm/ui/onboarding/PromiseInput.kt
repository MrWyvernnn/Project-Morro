package com.morro.alarm.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.MorroColors

/** One line a person can actually keep, so it stays deliberately short. */
const val MaxPromiseLength = 100

/** The counter starts warning this many characters before the cap. */
private const val CounterWarningMargin = 12

/**
 * Free-text answer for the final question.
 *
 * Borderless on purpose: the surrounding card is the bezel, so the text, the honey caret and the
 * count are the only marks inside it. The counter turns honey when the person gets within 12
 * characters of the cap — a warning, not a scold.
 */
@Composable
fun PromiseInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = MaxPromiseLength,
    placeholder: String = "Say it the way you'd say it out loud",
) {
    val textStyle = TextStyle(
        color = MorroColors.TextPrimary,
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal
    )

    val nearLimit = value.length >= maxLength - CounterWarningMargin

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { next ->
                // Hard cap in the model, not just the counter, so a paste cannot overshoot.
                if (next.length <= maxLength) onValueChange(next)
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle,
            cursorBrush = SolidColor(MorroColors.Honey),
            singleLine = false,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MorroColors.TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${value.length}/$maxLength",
                color = if (nearLimit) MorroColors.Honey else MorroColors.TextTertiary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}
