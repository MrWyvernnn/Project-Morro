package com.morro.alarm

import kotlin.random.Random

/**
 * MathEngine: Generates mental arithmetic questions for the alarm puzzle gate.
 * Meets the requirement:
 * - Addition/subtraction for numbers 12-89
 * - Single-digit multiplication (2-9)
 */
object MathEngine {

    private val random = Random(System.currentTimeMillis())

    /** Simple data class holding a math question text and its answer */
    data class Question(val text: String, val answer: Int)

    /**
     * Generates a random math question for the puzzle:
     * - Single-digit multiplication (e.g., "7 × 8 = ?")
     * - Addition for numbers 12-89 (e.g., "24 + 37 = ?")
     * - Subtraction for numbers 12-89 with a positive result (e.g., "65 - 28 = ?")
     */
    fun generateQuestion(): Question {
        return when (random.nextInt(3)) {
            0 -> {
                // Single-digit multiplication (2-9 to avoid trivial 0 and 1)
                val a = random.nextInt(2, 10)
                val b = random.nextInt(2, 10)
                Question("$a × $b = ?", a * b)
            }
            1 -> {
                // Addition for numbers 12-89
                val a = random.nextInt(12, 90)
                val b = random.nextInt(12, 90)
                Question("$a + $b = ?", a + b)
            }
            else -> {
                // Subtraction for numbers 12-89 (ensure non-negative result for numeric keypad)
                val num1 = random.nextInt(12, 90)
                val num2 = random.nextInt(12, 90)
                val a = maxOf(num1, num2)
                val b = minOf(num1, num2)
                Question("$a - $b = ?", a - b)
            }
        }
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
}