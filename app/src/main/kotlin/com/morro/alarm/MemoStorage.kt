package com.morro.alarm

import android.content.Context
import java.io.File

/**
 * MemoStorage: Owns everything about where a recorded memo lives.
 *
 * Two files in app-private storage (context.filesDir):
 *   - temp_memo.m4a  : the clip currently being recorded / previewed
 *   - latest_memo.m4a: the clip that the alarm will play
 *
 * The path of the saved clip is mirrored into SharedPreferences ("morro_prefs")
 * so we can find it again without scanning the filesystem.
 */
object MemoStorage {

    const val PREFS_NAME = "morro_prefs"
    const val KEY_LATEST_MEMO_PATH = "latest_memo_path"
    const val FILENAME_LATEST = "latest_memo.m4a"
    const val FILENAME_TEMP = "temp_memo.m4a"

    /** Absolute path of the temp (work-in-progress) recording. */
    fun getTempPath(context: Context): String =
        File(context.filesDir, FILENAME_TEMP).absolutePath

    /** Absolute path of the saved memo, or null if nothing is saved. */
    fun getLatestMemoPath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_LATEST_MEMO_PATH, null) ?: return null
        // Validate the file is really there: prefs can survive a manual file wipe.
        return if (File(path).exists() && File(path).length() > 0L) path else null
    }

    /**
     * Promotes the temp recording to be the saved memo.
     * Returns true only if a usable clip was actually stored.
     */
    fun saveMemo(context: Context): Boolean {
        val temp = File(context.filesDir, FILENAME_TEMP)
        if (!temp.exists() || temp.length() == 0L) return false

        val latest = File(context.filesDir, FILENAME_LATEST)
        if (latest.exists()) latest.delete()

        val moved = temp.renameTo(latest)
        if (!moved) {
            // renameTo can fail across some storage layers; fall back to copy.
            val copied = try {
                temp.copyTo(latest, overwrite = true)
                true
            } catch (e: Exception) {
                false
            }
            temp.delete()
            if (!copied) return false
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATEST_MEMO_PATH, latest.absolutePath)
            .apply()
        return true
    }

    /** Deletes the work-in-progress recording. The saved memo is untouched. */
    fun clearTemp(context: Context) {
        File(context.filesDir, FILENAME_TEMP).delete()
    }

    /** True when an uncommitted temp recording exists on disk. */
    fun hasTempMemo(context: Context): Boolean {
        val temp = File(context.filesDir, FILENAME_TEMP)
        return temp.exists() && temp.length() > 0L
    }

    /** True when a saved memo exists on disk. */
    fun hasSavedMemo(context: Context): Boolean = getLatestMemoPath(context) != null
}
