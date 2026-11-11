package com.morro.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography

/**
 * AffirmationGate: simplest possible typing mission.
 * User types the target affirmation exactly to silence the alarm.
 * No clipboard blocking — rely on cognitive friction of typing.
 */
@Composable
fun AffirmationGate(
    onSuccess: () -> Unit,
    onSnooze: () -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val targetSentence = stringResource(R.string.target_affirmation)
    var typedText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun validateAndSubmit() {
        val normalizedInput = typedText
            .trim()
            .lowercase()
            .replace(Regex("[.,!]"), "")
        val normalizedTarget = targetSentence
            .trim()
            .lowercase()
            .replace(Regex("[.,!]"), "")

        if (normalizedInput == normalizedTarget) {
            keyboardController?.hide()
            onSuccess()
        } else {
            isError = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Type the commitment to silence the alarm.",
            style = typo.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Display the target sentence clearly for the user to read and type
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
            shape = morroShapes().Card
        ) {
            Text(
                text = targetSentence,
                style = typo.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = typedText,
            onValueChange = {
                typedText = it
                if (isError) isError = false
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type here...", color = colors.textDisabled) },
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(4.dp),
            colors = TextFieldColors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textDisabled,
                errorTextColor = colors.error,
                focusedContainerColor = Color.Unspecified,
                unfocusedContainerColor = Color.Unspecified,
                disabledContainerColor = Color.Unspecified,
                errorContainerColor = Color.Unspecified,
                cursorColor = colors.accentTactical,
                errorCursorColor = colors.error,
                textSelectionColors = TextSelectionColors(
                    handleColor = colors.accentTactical,
                    backgroundColor = colors.accentTactical.copy(alpha = 0.3f)
                ),
                focusedIndicatorColor = colors.accentTactical,
                unfocusedIndicatorColor = colors.borderSubtle,
                disabledIndicatorColor = Color.Unspecified,
                errorIndicatorColor = colors.error,
                focusedLabelColor = Color.Unspecified,
                unfocusedLabelColor = Color.Unspecified,
                disabledLabelColor = Color.Unspecified,
                errorLabelColor = Color.Unspecified,
                focusedLeadingIconColor = Color.Unspecified,
                unfocusedLeadingIconColor = Color.Unspecified,
                disabledLeadingIconColor = Color.Unspecified,
                errorLeadingIconColor = Color.Unspecified,
                focusedTrailingIconColor = Color.Unspecified,
                unfocusedTrailingIconColor = Color.Unspecified,
                disabledTrailingIconColor = Color.Unspecified,
                errorTrailingIconColor = Color.Unspecified,
                focusedPlaceholderColor = colors.textDisabled,
                unfocusedPlaceholderColor = colors.textDisabled,
                disabledPlaceholderColor = colors.textDisabled,
                errorPlaceholderColor = colors.error,
                focusedSupportingTextColor = Color.Unspecified,
                unfocusedSupportingTextColor = Color.Unspecified,
                disabledSupportingTextColor = Color.Unspecified,
                errorSupportingTextColor = Color.Unspecified,
                focusedPrefixColor = Color.Unspecified,
                unfocusedPrefixColor = Color.Unspecified,
                disabledPrefixColor = Color.Unspecified,
                errorPrefixColor = Color.Unspecified,
                focusedSuffixColor = Color.Unspecified,
                unfocusedSuffixColor = Color.Unspecified,
                disabledSuffixColor = Color.Unspecified,
                errorSuffixColor = Color.Unspecified
            ),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { validateAndSubmit() }
            )
        )

        if (isError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Almost — check spelling and punctuation.",
                style = typo.bodyMedium,
                color = colors.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { validateAndSubmit() },
            enabled = typedText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentTactical,
                contentColor = Color.White
            )
        ) {
            Text("Submit", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSnooze,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentTactical,
                contentColor = Color.White
            )
        ) {
            Text("Snooze (10m)", fontSize = 18.sp)
        }
    }
}