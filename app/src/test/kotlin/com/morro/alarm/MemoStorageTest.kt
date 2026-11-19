package com.morro.alarm

import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Losing tomorrow's memo is the worst thing this app can do: the alarm then rings with no promise
 * to play. The 15 s cap makes that loss easy, because MediaRecorder finalises the clip itself and
 * our later `stop()` throws — so the only honest success signal is the file on disk. These tests
 * pin that single rule for both paths that end a recording (the silence gate in AlarmActivity and
 * the "voice promise ready" state in the alarm builder).
 */
class MemoStorageTest {

    private val scratch = mutableListOf<File>()

    private fun clip(bytes: Int): File {
        val file = File.createTempFile("temp_memo", ".m4a")
        if (bytes > 0) file.writeBytes(ByteArray(bytes) { 1 })
        scratch += file
        return file
    }

    @After
    fun cleanUp() {
        scratch.forEach { it.delete() }
    }

    @Test
    fun `clip written before the native cap fires counts as a recording`() {
        assertTrue(MemoStorage.isUsableClip(clip(bytes = 512)))
    }

    @Test
    fun `missing clip does not count as a recording`() {
        val gone = File.createTempFile("temp_memo", ".m4a")
        gone.delete()

        assertFalse(MemoStorage.isUsableClip(gone))
    }

    @Test
    fun `empty clip does not count as a recording`() {
        assertFalse(MemoStorage.isUsableClip(clip(bytes = 0)))
    }

    @Test
    fun `clip that was already promoted is no longer a temp recording`() {
        // saveMemo() moves temp -> latest, so the second caller finds no temp file. It must report
        // "nothing left here" rather than claiming the clip was lost.
        val temp = clip(bytes = 512)
        val saved = File.createTempFile("latest_memo", ".m4a")
        scratch += saved

        assertTrue(temp.renameTo(saved))
        assertTrue(MemoStorage.isUsableClip(saved))
        assertFalse(MemoStorage.isUsableClip(temp))
    }
}
