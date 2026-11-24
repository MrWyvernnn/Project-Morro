package com.morro.alarm.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.components.MorroSkyScrim
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.staggeredReveal
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.morroHairline

/**
 * Sign-in — NOT ROUTED. Kept on disk, deliberately outside [IntroStep].
 *
 * Morro has no accounts and does not want any. A Play purchase is tied to the Google account
 * already on the device, so a sign-in screen would add a credential for us to store, plus an
 * in-app and web account-deletion obligation under Play policy, in exchange for nothing. Voice
 * memos, alarms and mission choices stay local.
 *
 * Do not wire this back into the first-run flow. If a future feature seems to need it, the answer
 * is almost certainly to key that feature to the Play entitlement instead.
 *
 * What is left here is a faithful placeholder: neither button authenticates anything, so the screen
 * would have to be rebuilt around a real identity provider before it could ship. The layout follows
 * the questions screen — one reading column, clouds behind it, a soft scrim so the copy never sits
 * on a cloud, and a single filled action. Gmail is the outlined secondary, because one screen gets
 * one bright thing.
 */
@Composable
fun SignInScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Full-bleed so the sky reaches the edges; the reading column takes the insets, not the Box.
    Box(modifier = modifier.fillMaxSize()) {
        MorroSky(intensity = 0.55f)

        // Same scrim as the questions: scenery stays in the margins, copy stays legible.
        MorroSkyScrim()

        Column(
            modifier = Modifier
                // Height first, then a reading width: fillMaxSize() would pin the column to the
                // full tablet width and the widthIn cap below would have no room to apply.
                .fillMaxHeight()
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter)
                // safeDrawing is system bars plus the IME; applying imePadding as well on the
                // parent added the navigation-bar inset twice once the keyboard opened.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onContinue) {
                    Text(
                        text = "Skip",
                        color = MorroColors.TextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MorroGhostMascot(
                modifier = Modifier
                    .size(width = 96.dp, height = 108.dp)
                    .staggeredReveal(0),
                happy = true
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Welcome to Morro",
                modifier = Modifier.staggeredReveal(1),
                color = MorroColors.TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 30.sp,
                lineHeight = 37.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sign in to continue. Your promise stays on this phone.",
                modifier = Modifier.staggeredReveal(2),
                color = MorroColors.TextSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(30.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredReveal(3),
                placeholder = {
                    Text(
                        text = "you@email.com",
                        color = MorroColors.TextTertiary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp
                    )
                },
                singleLine = true,
                shape = MorroShapes.Button,
                textStyle = TextStyle(
                    color = MorroColors.TextPrimary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                // The keyboard's own Done key does what the button does, instead of only closing.
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (email.isNotBlank()) onContinue()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MorroColors.Honey,
                    unfocusedBorderColor = MorroColors.BorderSubtle,
                    focusedContainerColor = MorroColors.SurfaceCard,
                    unfocusedContainerColor = MorroColors.SurfaceCard,
                    cursorColor = MorroColors.Honey,
                    focusedTextColor = MorroColors.TextPrimary,
                    unfocusedTextColor = MorroColors.TextPrimary,
                    focusedPlaceholderColor = MorroColors.TextTertiary,
                    unfocusedPlaceholderColor = MorroColors.TextTertiary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            MorroPrimaryButton(
                text = "Continue with email",
                onClick = onContinue,
                enabled = email.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MorroColors.BorderSubtle)
                )
                Text(
                    text = "or",
                    color = MorroColors.TextTertiary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MorroColors.BorderSubtle)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            GmailButton(onClick = onContinue)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Preview only. No account is created and nothing leaves this phone.",
                color = MorroColors.TextTertiary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                lineHeight = 16.sp

            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * The Gmail secondary: an outlined pill with a monogram rather than a bundled brand asset, since no
 * logo font or icon pack ships with the app.
 */
@Composable
private fun GmailButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interactionSource)
            .clip(MorroShapes.Button)
            .background(MorroColors.SurfaceCard)
            .morroHairline(radius = 12.dp, color = MorroColors.BorderStrong)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(MorroColors.TextPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "G",
                color = MorroColors.Canvas,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "Continue with Gmail",
            color = MorroColors.TextPrimary,
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
