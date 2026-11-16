package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The puzzle gate is the only thing standing between a sleeping person and a dismissed alarm, so
 * its arithmetic has to be right every single time. These tests check the two guarantees the alarm
 * relies on:
 *
 *  1. the answer the engine stores is the answer to the question it printed;
 *  2. the operands stay inside the documented ranges, and subtraction never goes negative (the
 *     keypad has no minus key, so a negative answer would be unsolvable and the alarm would ring
 *     forever).
 */
class MathEngineTest {

    @Test
    fun `generated question text always matches its stored answer`() {
        repeat(500) {
            val question = MathEngine.generateQuestion()
            val parts = question.text.removeSuffix(" = ?").split(" ")

            assertEquals("unexpected question format: ${question.text}", 3, parts.size)

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

    @Test
    fun `multiplication uses single digits two to nine`() {
        repeat(400) {
            val question = MathEngine.generateQuestion()
            if (!question.text.contains("×")) return@repeat
            val parts = question.text.removeSuffix(" = ?").split(" ")
            assertTrue(parts[0].toInt() in 2..9)
            assertTrue(parts[2].toInt() in 2..9)
            assertTrue(question.answer in 4..81)
        }
    }

    @Test
    fun `addition and subtraction operands stay in range`() {
        repeat(400) {
            val question = MathEngine.generateQuestion()
            val parts = question.text.removeSuffix(" = ?").split(" ")
            if (parts[1] == "×") return@repeat
            assertTrue("${parts[0]} out of range", parts[0].toInt() in 12..89)
            assertTrue("${parts[2]} out of range", parts[2].toInt() in 12..89)
        }
    }

    @Test
    fun `subtraction never produces a negative answer`() {
        repeat(600) {
            val question = MathEngine.generateQuestion()
            if (question.text.contains("-")) {
                assertTrue("negative answer in ${question.text}", question.answer >= 0)
            }
        }
    }

    @Test
    fun `correct answer is accepted`() {
        repeat(200) {
            val question = MathEngine.generateQuestion()
            assertTrue(MathEngine.verify(question.answer.toString(), question))
        }
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        val question = MathEngine.Question("24 + 37 = ?", 61)
        assertTrue(MathEngine.verify(" 61 ", question))
        assertTrue(MathEngine.verify("61", question))
    }

    @Test
    fun `wrong answer is rejected`() {
        val question = MathEngine.Question("7 × 8 = ?", 56)
        assertFalse(MathEngine.verify("54", question))
        assertFalse(MathEngine.verify("57", question))
        assertFalse(MathEngine.verify("0", question))
    }

    @Test
    fun `blank and non numeric input is rejected without throwing`() {
        val question = MathEngine.Question("65 - 28 = ?", 37)
        assertFalse(MathEngine.verify("", question))
        assertFalse(MathEngine.verify("   ", question))
        assertFalse(MathEngine.verify("abc", question))
        assertFalse(MathEngine.verify("3 7", question))
        assertFalse(MathEngine.verify("-37", question))
        assertFalse(MathEngine.verify("37.0", question))
    }

    @Test
    fun `all three operations appear`() {
        val operators = mutableSetOf<String>()
        repeat(400) {
            val parts = MathEngine.generateQuestion().text.removeSuffix(" = ?").split(" ")
            operators += parts[1]
        }
        assertEquals(setOf("×", "+", "-"), operators)
    }
}
