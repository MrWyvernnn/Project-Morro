package com.morro.alarm.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the two palettes down.
 *
 * The theme switch is a visual change, so the only honest test is contrast plus "these exact values
 * belong to this exact theme". Everything here is WCAG arithmetic on the declared tokens — no
 * emulator, no rendering, no Robolectric.
 */
class MorroPaletteTest {

    /** WCAG 2.1 relative luminance of a colour in sRGB. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private val palettes = listOf(
        "Midnight" to MorroPalettes.Midnight,
        "Autumn" to MorroPalettes.Autumn,
    )

    @Test
    fun everyTextRoleClearsAaOnTheCanvas() {
        palettes.forEach { (name, palette) ->
            listOf(
                "textPrimary" to palette.textPrimary,
                "textSecondary" to palette.textSecondary,
                "textTertiary" to palette.textTertiary,
            ).forEach { (role, color) ->
                val ratio = contrast(color, palette.canvas)
                assertTrue(
                    "$name $role is ${"%.2f".format(ratio)}:1 on the canvas, needs 4.5",
                    ratio >= 4.5
                )
            }
        }
    }

    @Test
    fun everyTextRoleClearsAaOnACard() {
        palettes.forEach { (name, palette) ->
            listOf(
                "textPrimary" to palette.textPrimary,
                "textSecondary" to palette.textSecondary,
            ).forEach { (role, color) ->
                val ratio = contrast(color, palette.surfaceCard)
                assertTrue(
                    "$name $role is ${"%.2f".format(ratio)}:1 on a card, needs 4.5",
                    ratio >= 4.5
                )
            }
        }
    }

    @Test
    fun accentClearsNonTextContrast() {
        palettes.forEach { (name, palette) ->
            listOf("canvas" to palette.canvas, "card" to palette.surfaceCard).forEach { (where, bg) ->
                val ratio = contrast(palette.honey, bg)
                assertTrue(
                    "$name accent is ${"%.2f".format(ratio)}:1 on the $where, needs 3.0",
                    ratio >= 3.0
                )
            }
        }
    }

    @Test
    fun bothThemesShareTheAmoledCanvas() {
        // The whole point of a second theme is that it does not light the ground.
        assertEquals(MorroPalettes.Midnight.canvas, MorroPalettes.Autumn.canvas)
        assertEquals(Color(0xFF000000), MorroPalettes.Autumn.canvas)
    }

    @Test
    fun autumnIsNotAColourShiftOfMidnight() {
        assertTrue(
            "the accent must actually change",
            MorroPalettes.Midnight.honey != MorroPalettes.Autumn.honey
        )
        assertTrue(
            "the surface ramp must actually warm up",
            MorroPalettes.Midnight.surfaceCard != MorroPalettes.Autumn.surfaceCard
        )
    }

    @Test
    fun midnightKeepsItsOriginalValues() {
        val midnight = MorroPalettes.Midnight
        assertEquals(Color(0xFF0B0A08), midnight.surface)
        assertEquals(Color(0xFF121110), midnight.surfaceCard)
        assertEquals(Color(0xFFEFE7D6), midnight.textPrimary)
        assertEquals(Color(0xFFE8C58F), midnight.honey)
        assertEquals(Color(0xFFD8C1A4), midnight.error)
    }

    @Test
    fun ofReturnsTheSameInstanceForEveryCall() {
        assertSame(MorroPalettes.Midnight, MorroPalettes.of(MorroThemeChoice.MIDNIGHT))
        assertSame(MorroPalettes.Autumn, MorroPalettes.of(MorroThemeChoice.AUTUMN))
    }
}
