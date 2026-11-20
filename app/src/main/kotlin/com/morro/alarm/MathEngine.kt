package com.morro.alarm

import android.content.Context
import kotlin.random.Random

/**
 * MathEngine: Generates mental arithmetic questions for the alarm puzzle gate.
 *
 * The gate used to be a text field and a number keypad. At 6 a.m. a keypad is the wrong instrument —
 * four big answers can be hit without aiming — so a question now carries its own four choices, one
 * of which is the answer. The arithmetic never changed: every question still states itself in text
 * and stores the value that text evaluates to.
 *
 * Difficulty is the person's own choice, made once when they set the alarm:
 *  - EASY   single digits, small sums and differences, small times tables
 *  - MEDIUM times tables to nine, two-digit sums and differences (the original range)
 *  - HARD   two-digit products and three-digit sums and differences
 */
object MathEngine {

    // A seeded kotlin Random is a plain XORWOW instance, which is not safe to share across
    // threads. The puzzle needs variety, not unpredictability, so the thread-safe default is the
    // right generator and removes a whole class of "same question twice" hazard.
    private val random = Random.Default

    /** How hard the three puzzles are, chosen by the person, never inferred from anything. */
    enum class Difficulty(val label: String, val detail: String) {
        EASY("Easy", "Single digits, small sums"),
        MEDIUM("Medium", "Times tables, two-digit sums"),
        HARD("Hard", "Two-digit products, three-digit sums"),
    }

    /**
     * A question: what it asks, what the answer is, and the four choices shown on the alarm screen.
     *
     * `options` defaults to empty because a question can exist for arithmetic alone (the unit tests
     * and the restore path build one from a stored answer). Anything that draws the gate calls
     * [generateQuestion], which always fills in four distinct choices.
     */
    data class Question(
        val text: String,
        val answer: Int,
        val options: List<Int> = emptyList(),
    )

    /**
     * Generates a question at [difficulty], with four tappable answer choices.
     *
     * The default is MEDIUM so existing call sites and the documented operand ranges are unchanged.
     */
    fun generateQuestion(difficulty: Difficulty = Difficulty.MEDIUM): Question {
        val (text, answer) = when (difficulty) {
            Difficulty.EASY -> easyQuestion()
            Difficulty.MEDIUM -> mediumQuestion()
            Difficulty.HARD -> hardQuestion()
        }
        return Question(text, answer, optionsFor(answer))
    }

    /** Single digits: sums to 18, differences from a nine, times tables to five. */
    private fun easyQuestion(): Pair<String, Int> = when (random.nextInt(3)) {
        0 -> {
            val a = random.nextInt(2, 10)
            val b = random.nextInt(2, 10)
            "$a + $b = ?" to a + b
        }
        1 -> {
            val a = random.nextInt(4, 10)
            val b = random.nextInt(1, a + 1)
            "$a - $b = ?" to a - b
        }
        else -> {
            val a = random.nextInt(2, 6)
            val b = random.nextInt(2, 6)
            "$a × $b = ?" to a * b
        }
    }

    /** The original ranges: times tables 2–9, addition and subtraction over 12–89. */
    private fun mediumQuestion(): Pair<String, Int> = when (random.nextInt(3)) {
        0 -> {
            val a = random.nextInt(2, 10)
            val b = random.nextInt(2, 10)
            "$a × $b = ?" to a * b
        }
        1 -> {
            val a = random.nextInt(12, 90)
            val b = random.nextInt(12, 90)
            "$a + $b = ?" to a + b
        }
        else -> {
            val a = random.nextInt(12, 90)
            val b = random.nextInt(12, 90)
            "${maxOf(a, b)} - ${minOf(a, b)} = ?" to (maxOf(a, b) - minOf(a, b))
        }
    }

    /** Two-digit products, and three-digit sums and differences. Still doable in your head. */
    private fun hardQuestion(): Pair<String, Int> = when (random.nextInt(3)) {
        0 -> {
            val a = random.nextInt(12, 40)
            val b = random.nextInt(3, 10)
            "$a × $b = ?" to a * b
        }
        1 -> {
            val a = random.nextInt(100, 500)
            val b = random.nextInt(100, 500)
            "$a + $b = ?" to a + b
        }
        else -> {
            val a = random.nextInt(100, 900)
            val b = random.nextInt(100, 900)
            "${maxOf(a, b)} - ${minOf(a, b)} = ?" to (maxOf(a, b) - minOf(a, b))
        }
    }

    /**
     * Four tappable choices: the answer and three near misses.
     *
     * The misses sit within ±10 of the answer so they have to be read rather than estimated, and
     * they are never negative — a choice below zero is not something a person can quickly reject.
     * The answer is always present exactly once, whatever the loop below does.
     */
    fun optionsFor(answer: Int): List<Int> {
        val options = linkedSetOf(answer)
        var attempt = 0
        while (options.size < 4 && attempt < 80) {
            val step = random.nextInt(1, 11) * if (random.nextBoolean()) 1 else -1
            val candidate = answer + step
            if (candidate >= 0 && candidate != answer) options += candidate
            attempt++
        }
        // A tiny answer (0–2) can run out of near misses; walk outward until the row is full.
        var fallback = 10
        while (options.size < 4) {
            options += answer + fallback
            fallback += 10
        }
        return options.toList().shuffled(random)
    }

    /**
     * Verifies if the user's answer matches the question.
     * The userAnswer is a String from input to handle empty or invalid input safely.
     */
    fun verify(userAnswer: String, question: Question): Boolean {
        if (userAnswer.isBlank()) return false
        return try {
            val answer = userAnswer.trim().toInt()
            answer == question.answer
        } catch (e: NumberFormatException) {
            false
        }
    }

    /** The gate's own check: one of the four choices was tapped. */
    fun isCorrect(choice: Int, question: Question): Boolean = choice == question.answer
}

/**
 * The chosen puzzle difficulty, stored beside the alarm it belongs to.
 *
 * It lives in the same `morro_prefs` file as the mission and the theme, so backing up or clearing
 * the app takes all of it together. A missing or unreadable value is MEDIUM: the gate must never
 * fail to produce a question because a preference was garbage.
 */
object MathPrefs {
    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_DIFFICULTY = "math_difficulty"

    fun getDifficulty(context: Context): MathEngine.Difficulty {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_DIFFICULTY, null) ?: return MathEngine.Difficulty.MEDIUM
        return try {
            MathEngine.Difficulty.valueOf(name)
        } catch (e: IllegalArgumentException) {
            MathEngine.Difficulty.MEDIUM
        }
    }

    fun setDifficulty(context: Context, difficulty: MathEngine.Difficulty) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DIFFICULTY, difficulty.name).apply()
    }
}
