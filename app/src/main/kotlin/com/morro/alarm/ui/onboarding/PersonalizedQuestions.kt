package com.morro.alarm.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
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
import com.morro.alarm.ui.theme.MorroGradients
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroGradientBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Five quiet questions between the splash and the how-it-works slides.
 *
 * Editorial, not a wizard: the top bar says where you are in mono, the progress strip is the only
 * gradient, and each screen gives one question, one helper line and one column of answers. The
 * selected answer takes an orange -> pink border and a pink check; nothing else on screen is warm.
 *
 * The final answer is free text and gets real input behaviour: keyboard-aware compaction, a shake
 * plus inline error on empty submit, and a counter that warns before the cap.
 */

internal object QuestionsTokens {
    val ground: Color = MorroColors.Canvas
    val surfaceCard: Color = MorroColors.SurfaceElevated
    val surfaceQuiet: Color = MorroColors.SurfaceCard
    val borderIdle: Color = MorroColors.BorderSubtle
    val accent: Color = MorroColors.AccentTactical
    val accentPink: Color = MorroColors.AccentBubbly
    val error: Color = MorroColors.AccentBubblyDeep
    val inkPrimary: Color = MorroColors.TextPrimary
    val inkSecondary: Color = MorroColors.TextSecondary
    val inkMuted: Color = MorroColors.TextTertiary
}

internal enum class CardVariant { Unselected, Selected, Disabled }

private data class OnboardingQuestion(
    val key: String,
    val prompt: String,
    val helper: String,
    val options: List<String>,
    val fallback: String,
)

private const val TotalQuestions = 5
private const val SelectionSettleMillis = 190L

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
        helper = "One line. Morro reads it back to you in the morning.",
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

    /** Writes the whole profile in one shot; unanswered questions keep their neutral fallback. */
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizedQuestions(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val imeVisible = WindowInsets.isImeVisible

    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var promise by remember { mutableStateOf("") }

    val topSpace by animateDpAsState(
        targetValue = if (imeVisible) 6.dp else 20.dp,
        animationSpec = MorroSpring.DpSpringDefault,
        label = "headerCompact"
    )
    val gapBelowProgress by animateDpAsState(
        targetValue = if (imeVisible) 12.dp else 26.dp,
        animationSpec = MorroSpring.DpSpringDefault,
        label = "headerGap"
    )

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
            delay(SelectionSettleMillis)
            if (index == at && index < questions.lastIndex) index = at + 1
        }
    }

    BackHandler(enabled = index > 0) { index -= 1 }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        AmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(topSpace))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MORRO • 0${index + 1} / 0$TotalQuestions",
                    color = QuestionsTokens.accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 2.2.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { finish() }) {
                    Text(
                        text = "SKIP",
                        color = QuestionsTokens.inkSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            ProgressStrip(index = index)

            Spacer(modifier = Modifier.height(gapBelowProgress))

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
                    compact = imeVisible,
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
    compact: Boolean,
    onPromiseChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var errorVisible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    fun submitPromise() {
        if (promise.isBlank()) {
            errorVisible = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 420
                        -10f at 0
                        10f at 60
                        -7f at 130
                        7f at 200
                        -4f at 280
                        0f at 420
                    }
                )
            }
        } else {
            onContinue()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = if (compact) Arrangement.Top else Arrangement.Center
    ) {
        Text(
            text = question.prompt,
            style = TextStyle(
                color = QuestionsTokens.inkPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.2.sp,
                fontWeight = FontWeight.Medium
            )
        )

        if (!compact) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.helper,
                style = TextStyle(
                    color = QuestionsTokens.inkSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(if (compact) 16.dp else 26.dp))

        if (question.options.isEmpty()) {
            Column(modifier = Modifier.graphicsLayer { translationX = shake.value.dp.toPx() }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MorroShapes.Card)
                        .background(QuestionsTokens.surfaceCard)
                        .then(
                            when {
                                errorVisible -> Modifier.morroGradientBorder(
                                    shape = MorroShapes.Card,
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        listOf(QuestionsTokens.error, QuestionsTokens.error)
                                    )
                                )
                                promise.isNotBlank() -> Modifier.morroGradientBorder(MorroShapes.Card, 1.dp)
                                else -> Modifier.morroGradientBorder(
                                    shape = MorroShapes.Card,
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        listOf(QuestionsTokens.borderIdle, QuestionsTokens.borderIdle)
                                    )
                                )
                            }
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    PromiseInput(
                        value = promise,
                        onValueChange = {
                            if (errorVisible) errorVisible = false
                            onPromiseChange(it)
                        }
                    )
                }

                AnimatedVisibility(visible = errorVisible) {
                    Text(
                        text = "Write something to continue",
                        color = QuestionsTokens.error,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ContinueButton(
                active = promise.isNotBlank(),
                onClick = { submitPromise() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                question.options.forEach { option ->
                    AnswerCard(
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
 * An answer card: 0D0D12, 20dp squarcle, 1dp hairline. Selected takes the gradient border, a 6%
 * warm wash and a pink check — three signals, so colour is never the only carrier of meaning.
 */
@Composable
private fun AnswerCard(
    label: String,
    variant: CardVariant,
    onClick: () -> Unit,
) {
    val selected = variant == CardVariant.Selected
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = MorroSpring.FloatSpringPress,
        label = "answerPress"
    )
    val shape = MorroShapes.Card

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                if (selected) QuestionsTokens.accent.copy(alpha = 0.06f)
                else QuestionsTokens.surfaceCard
            )
            .then(if (selected) Modifier.morroGradientBorder(shape, 1.dp) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(color = MorroColors.AccentBubbly),
                enabled = variant != CardVariant.Disabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = if (selected) QuestionsTokens.inkPrimary else QuestionsTokens.inkSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.2.sp
                )
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(QuestionsTokens.accentPink.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = QuestionsTokens.accentPink,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Continue: charcoal pill at rest, and once the person has written something the border becomes a
 * breathing orange -> pink line. Tapping it while empty is still allowed, so the screen can explain
 * itself with a shake instead of a dead button.
 */
@Composable
private fun ContinueButton(active: Boolean, onClick: () -> Unit) {
    val glow = rememberInfiniteTransition(label = "continueGlow").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "continueBorder"
    ).value
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = MorroSpring.FloatSpringPress,
        label = "continuePress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MorroShapes.Pill)
            .background(QuestionsTokens.surfaceCard)
            .then(
                if (active) {
                    Modifier.morroGradientBorder(
                        shape = MorroShapes.Pill,
                        width = 1.5.dp,
                        brush = MorroGradients.horizontal(glow)
                    )
                } else {
                    Modifier.morroGradientBorder(
                        shape = MorroShapes.Pill,
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(QuestionsTokens.borderIdle, QuestionsTokens.borderIdle)
                        )
                    )
                }
            )
            .selectable(
                selected = false,
                interactionSource = interactionSource,
                indication = ripple(color = MorroColors.AccentBubbly),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Continue",
            color = if (active) QuestionsTokens.inkPrimary else QuestionsTokens.inkMuted,
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

/** Five segments; completed and current carry the gradient, the rest stay hairline. */
@Composable
private fun ProgressStrip(index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (segment in 0 until TotalQuestions) {
            val filled = segment <= index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (filled) MorroGradients.horizontal()
                        else Brush.horizontalGradient(
                            listOf(QuestionsTokens.borderIdle, QuestionsTokens.borderIdle)
                        )
                    )
            )
        }
    }
}
