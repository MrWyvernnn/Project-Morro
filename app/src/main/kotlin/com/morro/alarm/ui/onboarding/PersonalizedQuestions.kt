package com.morro.alarm.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.MorroColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Prompt 3 - five quiet questions between the ghost splash and the dashboard.
 *
 * The idea: the five things Morro must know about a person become five questions on black, lit from
 * behind. One question per screen, one warm signal shared by the progress bar and the caret, and
 * never a second question competing for attention.
 *
 * The answers are written once, when the flow finishes or is skipped, so a half-finished flow never
 * leaves a partial profile behind.
 */

// ---------------------------------------------------------------------------------------------
// Component tokens: one place where this screen's colours are decided. No raw hex below this
// line - every colour is either a Morro token or a token-derived alpha.
// ---------------------------------------------------------------------------------------------

internal object QuestionsTokens {
    val ground: Color = MorroColors.Canvas
    val surfaceCard: Color = MorroColors.SurfaceVariant
    val borderIdle: Color = MorroColors.BorderSubtle
    val borderChosen: Color = MorroColors.BorderActive
    val accent: Color = MorroColors.AccentTactical
    val inkPrimary: Color = MorroColors.TextPrimary
    val inkSecondary: Color = MorroColors.TextSecondary
    val inkMuted: Color = MorroColors.TextDisabled
    val fillChosen: Color = MorroColors.AccentTactical.copy(alpha = 0.12f)
}

/** Explicit variants instead of ad-hoc styling, so a card can never be half-selected. */
internal enum class CardVariant { Unselected, Selected, Disabled }

internal data class CardStyle(val fill: Color, val border: Color)

/** Single merger for card state: variants resolve to concrete colours in one place. */
internal fun cardStyle(variant: CardVariant): CardStyle = when (variant) {
    CardVariant.Unselected -> CardStyle(QuestionsTokens.surfaceCard, QuestionsTokens.borderIdle)
    CardVariant.Selected -> CardStyle(QuestionsTokens.fillChosen, QuestionsTokens.borderChosen)
    CardVariant.Disabled -> CardStyle(
        QuestionsTokens.surfaceCard.copy(alpha = 0.55f),
        QuestionsTokens.borderIdle.copy(alpha = 0.55f)
    )
}

// ---------------------------------------------------------------------------------------------
// Copy: warm and accountable, never guilt.
// ---------------------------------------------------------------------------------------------

internal data class OnboardingQuestion(
    val key: String,
    val prompt: String,
    val helper: String,
    val options: List<String>,
    val fallback: String,
)

private const val TotalQuestions = 5

private val questions: List<OnboardingQuestion> = listOf(
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_IDENTITY,
        prompt = "What do you want to be known for?",
        helper = "The word you'd want on the door.",
        options = listOf("Discipline", "Creativity", "Kindness", "Growth"),
        fallback = "Growth"
    ),
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_ENERGY,
        prompt = "When do you feel most alive?",
        helper = "Morro will aim its harder moments here.",
        options = listOf("Early morning", "Late night", "Midday", "Whenever I'm moving"),
        fallback = "Early morning"
    ),
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_STRUGGLE,
        prompt = "What's your biggest morning struggle?",
        helper = "Naming it is most of the work.",
        options = listOf("Hitting snooze", "No motivation", "Racing thoughts", "Phone scrolling"),
        fallback = "Hitting snooze"
    ),
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_TONE,
        prompt = "How do you want Morro to talk to you?",
        helper = "You can change this later.",
        options = listOf("Gentle nudge", "Firm accountability", "Quiet reminder", "Encouraging coach"),
        fallback = "Gentle nudge"
    ),
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_TARGET,
        prompt = "What are you committing to tomorrow?",
        helper = "One line. Morro will read it back to you tomorrow morning.",
        options = emptyList(),
        fallback = ""
    )
)

// ---------------------------------------------------------------------------------------------
// Storage
// ---------------------------------------------------------------------------------------------

object PersonalizationStorage {
    const val PREFS_NAME = "morro_prefs"

    const val KEY_DONE = "onboarding_questions_done"
    const val KEY_IDENTITY = "onboarding_identity"
    const val KEY_ENERGY = "onboarding_energy"
    const val KEY_STRUGGLE = "onboarding_struggle"
    const val KEY_TONE = "onboarding_tone"
    const val KEY_TARGET = "onboarding_target"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the person finished or deliberately skipped the questions. */
    fun isComplete(context: Context): Boolean = prefs(context).getBoolean(KEY_DONE, false)

    fun identity(context: Context): String =
        prefs(context).getString(KEY_IDENTITY, "Growth").orEmpty()

    fun energy(context: Context): String =
        prefs(context).getString(KEY_ENERGY, "Early morning").orEmpty()

    fun struggle(context: Context): String =
        prefs(context).getString(KEY_STRUGGLE, "Hitting snooze").orEmpty()

    fun tone(context: Context): String =
        prefs(context).getString(KEY_TONE, "Gentle nudge").orEmpty()

    fun target(context: Context): String =
        prefs(context).getString(KEY_TARGET, "").orEmpty()

    /**
     * Writes the whole profile in one shot. Unanswered questions get their neutral fallback, and
     * the promise is stored trimmed and capped - an empty promise stays empty rather than turning
     * into a commitment the person never made.
     */
    fun save(context: Context, answers: Map<String, String>) {
        val editor = prefs(context).edit()
        for (question in questions) {
            val value = answers[question.key]?.takeIf { it.isNotBlank() } ?: question.fallback
            editor.putString(question.key, value)
        }
        val promise = answers[KEY_TARGET].orEmpty().trim().take(MaxPromiseLength)
        editor.putString(KEY_TARGET, promise)
        editor.putBoolean(KEY_DONE, true)
        editor.commit()
    }
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

private const val SelectionSettleMillis = 180L

@Composable
fun PersonalizedQuestions(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var promise by remember { mutableStateOf("") }

    fun finish() {
        PersonalizationStorage.save(
            context = context,
            answers = answers.toMap() + (PersonalizationStorage.KEY_TARGET to promise)
        )
        onComplete()
    }

    fun choose(question: OnboardingQuestion, option: String) {
        val at = index
        answers[question.key] = option
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            // A short beat so the selection reads as chosen before the screen moves on.
            delay(SelectionSettleMillis)
            if (index == at && index < questions.lastIndex) index = at + 1
        }
    }

    BackHandler(enabled = index > 0) { index -= 1 }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MORRO · 0${index + 1} / 0$TotalQuestions",
                    color = QuestionsTokens.accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 2.2.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { finish() },
                    colors = ButtonDefaults.textButtonColors(contentColor = QuestionsTokens.inkMuted)
                ) {
                    Text(
                        text = "SKIP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ProgressStrip(index = index, reducedMotion = reducedMotion)

            Spacer(modifier = Modifier.height(28.dp))

            Crossfade(
                targetState = index,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                animationSpec = tween(
                    durationMillis = 240,
                    easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
                ),
                label = "questionFade"
            ) { questionIndex ->
                val question = questions[questionIndex]
                QuestionBody(
                    question = question,
                    selected = answers[question.key],
                    promise = promise,
                    onPromiseChange = { promise = it },
                    onSelect = { option -> choose(question, option) },
                    onContinue = { finish() }
                )
            }
        }
    }
}

@Composable
private fun QuestionBody(
    question: OnboardingQuestion,
    selected: String?,
    promise: String,
    onPromiseChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = question.prompt,
            style = TextStyle(
                color = QuestionsTokens.inkPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.4.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = question.helper,
            style = TextStyle(
                color = QuestionsTokens.inkSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.3.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (question.options.isEmpty()) {
            PromiseCard(value = promise, onValueChange = onPromiseChange)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onContinue,
                enabled = promise.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuestionsTokens.accent,
                    contentColor = QuestionsTokens.ground,
                    disabledContainerColor = QuestionsTokens.surfaceCard,
                    disabledContentColor = QuestionsTokens.inkMuted
                )
            ) {
                Text(
                    text = "Continue",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    letterSpacing = 0.3.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                question.options.forEach { option ->
                    QuestionCard(
                        label = option,
                        variant = if (option == selected) CardVariant.Selected else CardVariant.Unselected,
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}

/**
 * An answer card: a 1dp shell around a quiet fill, the interior lightening only once chosen.
 * Press feedback happens on touch-down (0.98) so the tap feels acknowledged before the hand lifts.
 */
@Composable
private fun QuestionCard(
    label: String,
    variant: CardVariant,
    onClick: () -> Unit,
) {
    val style = cardStyle(variant)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "cardPress"
    )
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(style.fill, shape)
            .border(1.dp, style.border, shape)
            .selectable(
                selected = variant == CardVariant.Selected,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = variant != CardVariant.Disabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (variant == CardVariant.Selected) {
                    QuestionsTokens.inkPrimary
                } else {
                    QuestionsTokens.inkSecondary
                },
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.4.sp
            )
        )
    }
}

/** The final question's card: same shell as an answer card, with the promise typed inside it. */
@Composable
private fun PromiseCard(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(QuestionsTokens.surfaceCard, shape)
            .border(1.dp, QuestionsTokens.borderIdle, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        PromiseInput(value = value, onValueChange = onValueChange)
    }
}

/**
 * Five measured bars. Completed and current are the same warm signal; the current one breathes so
 * the eye lands there. Position is also spelled out in the eyebrow above, so the pulse is never the
 * only clue about where the person is.
 */
@Composable
private fun ProgressStrip(index: Int, reducedMotion: Boolean) {
    val pulse = rememberInfiniteTransition(label = "progressPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "currentSegmentPulse"
    ).value
    val currentAlpha = if (reducedMotion) 1f else pulse

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (segment in 0 until TotalQuestions) {
            val color = when {
                segment < index -> QuestionsTokens.accent
                segment == index -> QuestionsTokens.accent.copy(alpha = currentAlpha)
                else -> QuestionsTokens.borderIdle
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}
