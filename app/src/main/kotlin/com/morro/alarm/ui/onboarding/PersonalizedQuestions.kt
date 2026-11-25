package com.morro.alarm.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.components.MorroSkyScrim
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.staggeredReveal
import com.morro.alarm.ui.theme.morroHairline
import kotlinx.coroutines.launch

/**
 * Five questions between the sign-in screen and the personalizing beat: three quick choices, then
 * the two written ones that carry the promise.
 *
 * The layout is a single column with one question at a time. There is no wizard chrome: a quiet
 * "Question 1 of 5", a two-pixel progress rail, the question at 26sp, then answers. Answer rows are
 * flat cards with a hairline; selection is a honey border plus a check, so it is never signalled by
 * colour alone and never by a gradient.
 *
 * The last two answers are free text with real input behaviour: keyboard-aware compaction, a shake
 * plus inline error on empty submit, and a counter that warns before the cap.
 */

internal enum class CardVariant { Unselected, Selected, Disabled }

internal data class OnboardingQuestion(
    val key: String,
    val prompt: String,
    val helper: String,
    val options: List<String>,
    val fallback: String,
)

/** Draft answers as a flat key/value list, so rotation does not wipe the flow. */
private val AnswersSaver = Saver<SnapshotStateMap<String, String>, List<String>>(
    save = { map -> map.entries.flatMap { listOf(it.key, it.value) } },
    restore = { flat ->
        mutableStateMapOf<String, String>().apply {
            for (i in flat.indices step 2) {
                if (i + 1 < flat.size) put(flat[i], flat[i + 1])
            }
        }
    }
)

internal val onboardingQuestions: List<OnboardingQuestion> = listOf(
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
        helper = "Morro will aim its harder wake-ups there.",
        options = listOf("Early morning", "Late night", "Midday", "When I am moving"),
        fallback = "Early morning"
    ),
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_STRUGGLE,
        prompt = "What is your biggest morning struggle?",
        helper = "Naming it is most of the work.",
        options = listOf("Hitting snooze", "No motivation", "Racing thoughts", "Phone scrolling"),
        fallback = "Hitting snooze"
    ),
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_EXTRA,
        prompt = "Imagine you had back every hour you slept through. What changes?",
        helper = "\u201CEvery action you take is a vote for the type of person you wish to become.\u201D \u2014 James Clear, Atomic Habits",
        options = emptyList(),
        fallback = ""
    ),
    OnboardingQuestion(
        key = PersonalizationStorage.KEY_TARGET,
        prompt = "What are you committing to tomorrow?",
        helper = "One line. Morro reads it back to you in the morning.",
        options = emptyList(),
        fallback = ""
    )
)

/**
 * One rail segment per question, derived from the list.
 *
 * This used to be a hand-written `7`. The list lost two questions and the rail would have kept
 * drawing seven segments — the counter and the rail would disagree with the quiz forever. A count
 * read off the list cannot drift.
 */
internal val TotalQuestions: Int = onboardingQuestions.size

/** Restores the question index, clamped to the question list this build actually ships. */
private val QuestionIndexSaver = androidx.compose.runtime.saveable.Saver<MutableIntState, Int>(
    save = { it.intValue },
    restore = { mutableIntStateOf(it.coerceIn(0, onboardingQuestions.lastIndex)) }
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
    const val KEY_EXTRA = "onboarding_extra_time"
    const val KEY_TARGET = "onboarding_target"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isComplete(context: Context): Boolean = prefs(context).getBoolean(KEY_DONE, false)






    /** Writes the whole profile in one shot; unanswered questions keep their neutral fallback. */
    fun save(context: Context, answers: Map<String, String>) {
        val editor = prefs(context).edit()
        for (question in onboardingQuestions) {
            val value = answers[question.key]?.takeIf { it.isNotBlank() } ?: question.fallback
            editor.putString(question.key, value)
        }
        val promise = answers[KEY_TARGET].orEmpty().trim().take(MaxPromiseLength)
        editor.putString(KEY_TARGET, promise)
        editor.putString(KEY_EXTRA, answers[KEY_EXTRA].orEmpty().trim().take(MaxPromiseLength))
        editor.putBoolean(KEY_DONE, true)
        editor.apply()
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
    val imeVisible = WindowInsets.isImeVisible

    // A remembered index from an older build can point past the end of this build's question
    // list, and onboardingQuestions[questionIndex] would throw before the flow ever renders.
    var index by rememberSaveable(saver = QuestionIndexSaver) { mutableIntStateOf(0) }
    // Draft answers survive rotation and process death; the map is flattened to a String list.
    val answers: SnapshotStateMap<String, String> = rememberSaveable(saver = AnswersSaver) {
        mutableStateMapOf()
    }

    val topSpace by animateDpAsState(
        targetValue = if (imeVisible) 8.dp else 24.dp,
        animationSpec = tween(180, easing = MorroMotion.EnterEasing),
        label = "headerCompact"
    )
    val gapBelowProgress by animateDpAsState(
        targetValue = if (imeVisible) 16.dp else 32.dp,
        animationSpec = tween(180, easing = MorroMotion.EnterEasing),
        label = "headerGap"
    )

    fun finish() {
        PersonalizationStorage.save(context = context, answers = answers.toMap())
        onComplete()
    }

    /** Continue moves to the next question; only the last one closes the flow. */
    fun advance() {
        if (index >= onboardingQuestions.lastIndex) finish() else index += 1
    }

    fun choose(question: OnboardingQuestion, option: String) {
        // Choosing no longer moves the flow on by itself: the Continue button below the answers
        // does that, so a mis-tap can be corrected before leaving the question.
        answers[question.key] = option
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BackHandler(enabled = index > 0) { index -= 1 }

    // Full-bleed so the clouds reach the edges; the column below owns the insets.
    Box(modifier = modifier.fillMaxSize()) {
        MorroSky(intensity = 0.7f)

        // The sky is scenery; the question is the page. The shared scrim keeps the clouds in the
        // margins so a longer question (the hour quote, the two free-text prompts) never sits on one.
        MorroSkyScrim(alpha = 0.95f)

        Column(
            modifier = Modifier
                // One reading column, centred on tablets and foldables.
                .fillMaxHeight()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                // safeDrawing = system bars + IME. Applying imePadding on the Box *and* the system
                // bars here double-counted the navigation-bar inset once the keyboard opened.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(topSpace))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Question ${index + 1} of $TotalQuestions",
                    color = MorroColors.TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    letterSpacing = 0.2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { finish() }) {
                    Text(
                        text = "Skip",
                        color = MorroColors.TextSecondary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ProgressRail(index = index)

            Spacer(modifier = Modifier.height(gapBelowProgress))

            AnimatedContent(
                targetState = index,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    // The new question enters from the side you are heading toward, so the flow has
                    // a direction instead of being a stack of unrelated fades.
                    val forward = targetState > initialState
                    val incoming = slideInHorizontally(
                        animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) { width -> if (forward) width / 5 else -width / 5 } + fadeIn(tween(240))
                    val outgoing = slideOutHorizontally(
                        animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ) { width -> if (forward) -width / 8 else width / 8 } + fadeOut(tween(180))
                    incoming togetherWith outgoing
                },
                label = "questionChange"
            ) { questionIndex ->
                val question = onboardingQuestions[questionIndex]
                QuestionBody(
                    question = question,
                    selected = answers[question.key],
                    text = answers[question.key].orEmpty(),
                    compact = imeVisible,
                    onTextChange = { answers[question.key] = it },
                    onSelect = { option -> choose(question, option) },
                    onContinue = { advance() }
                )
            }
        }
    }
}

@Composable
private fun QuestionBody(
    question: OnboardingQuestion,
    selected: String?,
    text: String,
    compact: Boolean,
    onTextChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var errorVisible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    fun submitText() {
        if (text.isBlank()) {
            errorVisible = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        -8f at 0
                        8f at 55
                        -5f at 120
                        5f at 185
                        -3f at 255
                        0f at 400
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
                color = MorroColors.TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 26.sp,
                lineHeight = 33.sp,
                letterSpacing = (-0.3).sp,
                fontWeight = FontWeight.Medium
            )
        )

        if (!compact) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = question.helper,
                style = TextStyle(
                    color = MorroColors.TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(if (compact) 18.dp else 30.dp))

        if (question.options.isEmpty()) {
            Column(modifier = Modifier.graphicsLayer { translationX = shake.value.dp.toPx() }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MorroShapes.Card)
                        .background(MorroColors.SurfaceCard)
                        .morroHairline(
                            radius = 16.dp,
                            color = when {
                                errorVisible -> MorroColors.Error.copy(alpha = 0.6f)
                                text.isNotBlank() -> MorroColors.BorderStrong
                                else -> MorroColors.BorderSubtle
                            }
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    PromiseInput(
                        value = text,
                        onValueChange = {
                            if (errorVisible) errorVisible = false
                            onTextChange(it)
                        }
                    )
                }

                AnimatedVisibility(visible = errorVisible) {
                    Text(
                        text = "Write something to continue",
                        color = MorroColors.Error,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ContinueButton(
                active = text.isNotBlank(),
                onClick = { submitText() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                question.options.forEachIndexed { optionIndex, option ->
                    AnswerCard(
                        label = option,
                        variant = if (option == selected) CardVariant.Selected else CardVariant.Unselected,
                        onClick = { onSelect(option) },
                        index = optionIndex
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ContinueButton(
                active = selected != null,
                onClick = onContinue
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * An answer row: a flat card, a hairline, and — when chosen — a honey border plus a check.
 * Three signals, so colour is never the only carrier of meaning.
 */
@Composable
private fun AnswerCard(
    label: String,
    variant: CardVariant,
    onClick: () -> Unit,
    index: Int = 0,
) {
    val selected = variant == CardVariant.Selected
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MorroShapes.Card
    val container by animateColorAsState(
        targetValue = if (selected) MorroColors.SurfaceElevated else MorroColors.SurfaceCard,
        animationSpec = tween(durationMillis = 200),
        label = "answerFill"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MorroColors.BorderActive else MorroColors.BorderSubtle,
        animationSpec = tween(durationMillis = 200),
        label = "answerBorder"
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -1.5f else 0f,
        animationSpec = MorroSpring.FloatSpringPlayful,
        label = "answerLift"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .staggeredReveal(index)
            .pressScale(interactionSource)
            .graphicsLayer {
                translationY = lift
            }
            .clip(shape)
            .background(container)
            .morroHairline(radius = 16.dp, color = border)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(color = MorroColors.TextPrimary),
                enabled = variant != CardVariant.Disabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
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
                    color = if (selected) MorroColors.TextPrimary else MorroColors.TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.1.sp
                )
            )
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(140)) + scaleIn(
                    initialScale = 0.5f,
                    animationSpec = tween(140, easing = MorroMotion.EnterEasing)
                ),
                exit = fadeOut(tween(100))
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(MorroColors.Honey.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MorroColors.Honey,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Continue is the primary action: a warm off-white fill with near-black text, which is the
 * clearest hierarchy on a black screen. It stays tappable while empty so the screen can explain
 * itself with a shake instead of a dead button.
 */
@Composable
private fun ContinueButton(active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (active) MorroColors.TextPrimary else MorroColors.SurfaceElevated,
        animationSpec = tween(durationMillis = 200),
        label = "continueFill"
    )
    val labelColor by animateColorAsState(
        targetValue = if (active) MorroColors.Canvas else MorroColors.TextTertiary,
        animationSpec = tween(durationMillis = 200),
        label = "continueLabel"
    )
    val border by animateColorAsState(
        targetValue = if (active) Color.Transparent else MorroColors.BorderSubtle,
        animationSpec = tween(durationMillis = 200),
        label = "continueBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interactionSource)
            .clip(MorroShapes.Button)
            .background(fill)
            .morroHairline(radius = 12.dp, color = border)
            .selectable(
                selected = false,
                interactionSource = interactionSource,
                indication = ripple(color = MorroColors.Canvas),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Continue",
            color = labelColor,
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * One segment per question: completed and current are filled with the same warm white, the rest
 * stay quiet.
 *
 * Each fill is revealed by width rather than by colour, so progress reads as a mask sliding over
 * the track instead of a light switching on.
 */
@Composable
private fun ProgressRail(index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (segment in 0 until TotalQuestions) {
            val filled by animateFloatAsState(
                targetValue = if (segment <= index) 1f else 0f,
                animationSpec = tween(320, easing = MorroMotion.EnterEasing),
                label = "railSegment$segment"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MorroColors.BorderSubtle)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(filled)
                        .background(MorroColors.TextPrimary.copy(alpha = 0.75f))
                )
            }
        }
    }
}
