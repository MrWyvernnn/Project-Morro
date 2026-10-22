package com.morro.alarm

import kotlin.random.Random

/**
 * MathEngine: Generates simple mental math questions for the alarm puzzle gate.
 * Provides single-digit multiplication, 2-digit addition, and 2-digit subtraction.
 */
object MathEngine {

    private val random = Random(System.currentTimeMillis())

    /** Simple data class holding a math question text and its answer */
    data class Question(val text: String, val answer: Int)

    /**
     * Generates a random math question for the puzzle.
     * Returns either:
     * - Single-digit multiplication (e.g., "7 × 8 = ?")
     * - 2-digit addition (e.g., "24 + 37 = ?")
     * - 2-digit subtraction (e.g., "85 - 19 = ?")
     */
    fun generateQuestion(): Question {
        val type = random.nextInt(3) // 0: multiplication, 1: addition, 2: subtraction

        return when (type) {
            0 -> {
                // Single-digit multiplication
                val a = random.nextInt(2, 10) // 2-9 to avoid trivial cases
                val b = random.nextInt(2, 10)
                val answer = a * b
                Question("$a × $b = ?", answer)
            }
            1 -> {
                // 2-digit addition
                val a = random.nextInt(10, 100) // 10-99
                val b = random.nextInt(10, 100)
                val answer = a + b
                Question("$a + $b = ?", answer)
            }
            else -> {
                // 2-digit subtraction (ensure positive result)
                val a = random.nextInt(50, 200) // 50-199 to ensure enough room for subtraction
                val b = random.nextInt(1, 100) // 1-99
                val answer = a - b
                Question("$a - $b = ?", answer)
            }
        }
    }

    /**
     * Verifies if the user's answer matches the question.
     * The userAnswer is a String from EditText to handle empty input safely.
     */
    fun verify(userAnswer: String, question: Question): Boolean {
        if (userAnswer.isBlank()) return false
        return try {
            val answer = userAnswer.toInt()
            answer == question.answer
        } catch (e: NumberFormatException) {
            false
        }
    }
}