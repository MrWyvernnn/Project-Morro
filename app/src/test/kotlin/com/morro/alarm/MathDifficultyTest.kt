package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alarm gate now offers four taps instead of a keypad, so the guarantees moved: the answer must
 * always be one of the choices, the choices must never repeat, and no difficulty may hand back a
 * choice below zero. A missing answer would leave the alarm ringing with nothing to tap.
 */
class MathDifficultyTest {

    private val allDifficulties = MathEngine.Difficulty.values().toList()

    @Test
    fun `every question offers four distinct choices containing the answer`() {
        allDifficulties.forEach { difficulty ->
            repeat(300) {
                val question = MathEngine.generateQuestion(difficulty)
                assertEquals(
                    "wrong choice count at $difficulty for ${question.text}",
                    4,
                    question.options.size
                )
                assertEquals(
                    "duplicate choices at $difficulty for ${question.text}: ${question.options}",
                    4,
                    question.options.toSet().size
                )
                assertTrue(
                    "answer missing from ${question.options} for ${question.text}",
                    question.options.contains(question.answer)
                )
            }
        }
    }

    @Test
    fun `no difficulty offers a negative choice`() {
        allDifficulties.forEach { difficulty ->
            repeat(300) {
                val question = MathEngine.generateQuestion(difficulty)
                assertTrue(
                    "negative choice in ${question.options} for ${question.text}",
                    question.options.all { it >= 0 }
                )
            }
        }
    }

    @Test
    fun `question text still evaluates to the stored answer at every difficulty`() {
        allDifficulties.forEach { difficulty ->
            repeat(200) {
                val question = MathEngine.generateQuestion(difficulty)
                val parts = question.text.removeSuffix(" = ?").split(" ")
                assertEquals("unexpected format: ${question.text}", 3, parts.size)
                val left = parts[0].toInt()
                val right = parts[2].toInt()
                val expected = when (parts[1]) {
                    "×" -> left * right
                    "+" -> left + right
                    "-" -> left - right
                    else -> error("unknown operator in ${question.text}")
                }
                assertEquals("wrong answer for ${question.text}", expected, question.answer)
            }
        }
    }

    @Test
    fun `easy stays inside single digits`() {
        repeat(300) {
            val parts = MathEngine.generateQuestion(MathEngine.Difficulty.EASY)
                .text.removeSuffix(" = ?").split(" ")
            val left = parts[0].toInt()
            val right = parts[2].toInt()
            assertTrue("${parts[0]} out of range", left in 1..18)
            assertTrue("${parts[2]} out of range", right in 1..18)
            if (parts[1] == "×") {
                assertTrue("easy product too large: ${parts[0]} × ${parts[2]}", left <= 5 && right <= 5)
            }
        }
    }

    @Test
    fun `hard asks for two-digit products and three-digit sums`() {
        val kept: MutableList<String> = mutableListOf()
        repeat(400) {
            val question = MathEngine.generateQuestion(MathEngine.Difficulty.HARD)
            val parts = question.text.removeSuffix(" = ?").split(" ")
            when (parts[1]) {
                "×" -> {
                    assertTrue("hard product too small: ${question.text}", parts[0].toInt() >= 12)
                    assertTrue("hard product too small: ${question.text}", parts[0].toInt() * parts[2].toInt() >= 36)
                }
                else -> {
                    assertTrue("hard sum too small: ${question.text}", parts[0].toInt() >= 100)
                    assertTrue("hard sum too small: ${question.text}", parts[2].toInt() >= 100)
                }
            }
            kept += parts[1]
        }
        assertTrue("hard never multiplies", kept.contains("×"))
    }

    @Test
    fun `a tapped choice is checked against the answer`() {
        repeat(200) {
            val question = MathEngine.generateQuestion(MathEngine.Difficulty.MEDIUM)
            assertTrue(MathEngine.isCorrect(question.answer, question))
            question.options.filter { it != question.answer }.forEach { wrong ->
                assertTrue(!MathEngine.isCorrect(wrong, question))
            }
        }
    }

    @Test
    fun `choices can be rebuilt for a restored question`() {
        val restored = MathEngine.Question("24 + 37 = ?", 61, MathEngine.optionsFor(61))
        assertEquals(4, restored.options.size)
        assertTrue(restored.options.contains(61))
    }
}
