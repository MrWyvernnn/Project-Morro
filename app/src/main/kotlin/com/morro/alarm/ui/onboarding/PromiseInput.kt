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

/** The promise is one line a person can actually keep, so it stays deliberately short. */
const val MaxPromiseLength = 100

/**
 * Free-text answer for the final question.
 *
 * The field is borderless on purpose: the surrounding card already draws the bezel, so the text,
 * the orange caret and the count are the only marks inside it. The caret is the accent so the one
 * warm signal on screen sits exactly where the person is typing.
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
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.4.sp,
        fontWeight = FontWeight.Normal
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { next ->
                // Hard cap in the model, not just in the counter, so a paste cannot overshoot.
                if (next.length <= maxLength) onValueChange(next)
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle,
            cursorBrush = SolidColor(MorroColors.AccentTactical),
            singleLine = false,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MorroColors.TextDisabled
                        )
                    }
                    innerTextField()
                }
            }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${value.length}/$maxLength",
                color = MorroColors.TextDisabled,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp
            )
        }
    }
}
