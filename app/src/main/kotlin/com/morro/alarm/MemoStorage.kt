package com.morro.alarm

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    private const val TAG = "MemoStorage"
    const val PREFS_NAME = "morro_prefs"
    const val KEY_LATEST_MEMO_PATH = "latest_memo_path"
    const val FILENAME_LATEST = "latest_memo.m4a"
    const val FILENAME_TEMP = "temp_memo.m4a"

    /** Absolute path of the temp (work-in-progress) recording. */
    fun getTempPath(context: Context): String {
        val dir = context.applicationContext?.filesDir ?: context.filesDir
        return File(dir, FILENAME_TEMP).absolutePath
    }

    /** Absolute path of the saved memo, or null if nothing is saved. */
    fun getLatestMemoPath(context: Context): String? {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_LATEST_MEMO_PATH, null)
        if (path != null && File(path).exists() && File(path).length() > 0L) {
            return path
        }
        val commitmentFile = File(appCtx.filesDir, "commitment_memo.m4a")
        if (commitmentFile.exists() && commitmentFile.length() > 0L) {
            return commitmentFile.absolutePath
        }
        val latestFile = File(appCtx.filesDir, FILENAME_LATEST)
        if (latestFile.exists() && latestFile.length() > 0L) {
            return latestFile.absolutePath
        }
        return null
    }

    /**
     * Promotes the temp recording to be the saved memo using java.nio.file.Files.move
     * with StandardCopyOption.REPLACE_EXISTING without deleting latest_memo.m4a first.
     * Uses synchronous commit() for SharedPreferences update.
     * Returns true only if a usable clip was actually stored.
     */
    fun saveMemo(context: Context): Boolean {
        val appCtx = context.applicationContext
        val temp = File(appCtx.filesDir, FILENAME_TEMP)
        if (!temp.exists() || temp.length() == 0L) return false

        val latest = File(appCtx.filesDir, FILENAME_LATEST)

        // Use Files.move with REPLACE_EXISTING to atomically replace latest_memo.m4a
        // without deleting it first. This is more efficient and atomic than copy+delete.
        try {
            Files.move(
                temp.toPath(),
                latest.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: IOException) {
            Log.e(TAG, "IOException during Files.move of temp memo", e)
            return false
        }

        return appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATEST_MEMO_PATH, latest.absolutePath)
            .commit()
    }

    /** Deletes the work-in-progress recording. The saved memo is untouched. */
    fun clearTemp(context: Context) {
        val dir = context.applicationContext?.filesDir ?: context.filesDir
        File(dir, FILENAME_TEMP).delete()
    }

    /** True when an uncommitted temp recording exists on disk. */
    fun hasTempMemo(context: Context): Boolean {
        val dir = context.applicationContext?.filesDir ?: context.filesDir ?: return false
        val temp = File(dir, FILENAME_TEMP)
        return temp.exists() && temp.length() > 0L
    }

    /** True when a saved memo exists on disk. */
    fun hasSavedMemo(context: Context): Boolean = getLatestMemoPath(context) != null
}
