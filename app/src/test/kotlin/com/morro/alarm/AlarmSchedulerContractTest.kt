package com.morro.alarm

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The locked-boot path must never write, and must never refresh the boot mirror.
 *
 * This is a contract test rather than a behaviour test because the dangerous regression is
 * structural: someone later "tidying" `armWithoutPersisting` to also persist, or to call
 * `AlarmBootMirror.mirrorFrom`, would compile, pass every other test, and quietly destroy the
 * alarm on the one path that runs before credential storage is readable.
 *
 * Why it reads the source: this module has exactly one test dependency (JUnit 4) and no Robolectric
 * or Mockito, so there is no Android runtime to observe a `SharedPreferences` write from. Adding a
 * dependency to assert one absence is not a trade worth making, and a source assertion fails loudly
 * and immediately if the function is changed or renamed.
 *
 * The assertion is deliberately narrow: it looks only at the body of `armWithoutPersisting`, so the
 * KDoc above it is free to name `mirrorFrom` while explaining why it must not be called.
 */
class AlarmSchedulerContractTest {

    private val source: File by lazy {
        val candidates = listOf(
            File("src/main/kotlin/com/morro/alarm/AlarmScheduler.kt"),
            File("app/src/main/kotlin/com/morro/alarm/AlarmScheduler.kt"),
        )
        candidates.firstOrNull { it.exists() }
            ?: error(
                "Could not find AlarmScheduler.kt from ${File("").absolutePath}. " +
                    "Looked for: ${candidates.joinToString { it.path }}"
            )
    }

    /** The function's own lines, from its signature to the next blank line. */
    private fun armWithoutPersistingBody(): String {
        val lines = source.readLines()
        val start = lines.indexOfFirst { it.contains("fun armWithoutPersisting(") }
        check(start >= 0) {
            "armWithoutPersisting is gone from AlarmScheduler.kt. If it was renamed, update this " +
                "test deliberately — the locked-boot path depends on this exact contract."
        }
        return lines.drop(start)
            .takeWhile { it.isNotBlank() }
            .joinToString("\n")
    }

    @Test
    fun `the function under test still exists and still arms something`() {
        // A guard on the guard: if the body were emptied, the "contains no write" assertions below
        // would pass for the wrong reason.
        assertTrue(armWithoutPersistingBody().contains("armAlarmClock"))
    }

    @Test
    fun `the locked-boot arm never persists the alarm`() {
        val body = armWithoutPersistingBody()
        assertFalse("armWithoutPersisting must not write prefs: $body", body.contains("savePrefs"))
        assertFalse("armWithoutPersisting must not clear prefs: $body", body.contains("clearPrefs"))
    }

    @Test
    fun `the locked-boot arm never refreshes the boot mirror`() {
        // mirrorFrom reads isArmed from credential storage. Before the first unlock that returns the
        // default false, so it takes its "not armed" branch and clears the device-protected mirror
        // the alarm was just read from — the alarm is then unrecoverable on the next boot.
        val body = armWithoutPersistingBody()
        assertFalse("armWithoutPersisting must not mirror: $body", body.contains("mirrorFrom"))
        assertFalse("armWithoutPersisting must not touch the mirror: $body", body.contains("AlarmBootMirror"))
    }
}
