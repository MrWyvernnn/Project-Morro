package com.morro.alarm.ui.puzzle

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CognitiveChallengeTest {

    private lateinit var mockPrefs: MutableMap<String, Any?>
    private lateinit var fakePreferences: FakeSharedPreferences
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockPrefs = mutableMapOf()
        fakePreferences = FakeSharedPreferences(mockPrefs)
        CognitivePrefs.prefsOpener = { _, _ -> fakePreferences }

        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafeObj = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        mockContext = allocateMethod.invoke(unsafeObj, android.content.ContextWrapper::class.java) as Context
    }

    @After
    fun tearDown() {
        CognitivePrefs.prefsOpener = null
    }

    @Test
    fun `default challenge is MATH and default difficulty is STANDARD`() {
        assertEquals(CognitiveChallenge.MATH, CognitivePrefs.getChallenge(mockContext))
        assertEquals(CognitiveDifficulty.STANDARD, CognitivePrefs.getDifficulty(mockContext))
    }

    @Test
    fun `persists and restores cognitive challenge`() {
        CognitivePrefs.setChallenge(mockContext, CognitiveChallenge.CHESS)
        assertEquals(CognitiveChallenge.CHESS, CognitivePrefs.getChallenge(mockContext))

        CognitivePrefs.setChallenge(mockContext, CognitiveChallenge.SUDOKU)
        assertEquals(CognitiveChallenge.SUDOKU, CognitivePrefs.getChallenge(mockContext))

        CognitivePrefs.setChallenge(mockContext, CognitiveChallenge.MATH)
        assertEquals(CognitiveChallenge.MATH, CognitivePrefs.getChallenge(mockContext))
    }

    @Test
    fun `persists and restores cognitive difficulty`() {
        CognitivePrefs.setDifficulty(mockContext, CognitiveDifficulty.HARD)
        assertEquals(CognitiveDifficulty.HARD, CognitivePrefs.getDifficulty(mockContext))

        CognitivePrefs.setDifficulty(mockContext, CognitiveDifficulty.STANDARD)
        assertEquals(CognitiveDifficulty.STANDARD, CognitivePrefs.getDifficulty(mockContext))
    }

    @Test
    fun `falls back safely to default on corrupt or unknown string`() {
        mockPrefs["cognitive_challenge"] = "INVALID_CHALLENGE"
        mockPrefs["cognitive_difficulty"] = "SUPER_EXTREME"

        assertEquals(CognitiveChallenge.MATH, CognitivePrefs.getChallenge(mockContext))
        assertEquals(CognitiveDifficulty.STANDARD, CognitivePrefs.getDifficulty(mockContext))
    }

    @Test
    fun `falls back safely on null values`() {
        mockPrefs.remove("cognitive_challenge")
        mockPrefs.remove("cognitive_difficulty")

        assertEquals(CognitiveChallenge.MATH, CognitivePrefs.getChallenge(mockContext))
        assertEquals(CognitiveDifficulty.STANDARD, CognitivePrefs.getDifficulty(mockContext))
    }

    @Test
    fun `cognitive challenge enum labels and details are accurate`() {
        assertEquals("Math", CognitiveChallenge.MATH.label)
        assertEquals("Mental arithmetic sums", CognitiveChallenge.MATH.detail)

        assertEquals("Chess", CognitiveChallenge.CHESS.label)
        assertEquals("Find the checkmate", CognitiveChallenge.CHESS.detail)

        assertEquals("Sudoku", CognitiveChallenge.SUDOKU.label)
        assertEquals("Complete the grid", CognitiveChallenge.SUDOKU.detail)
    }

    @Test
    fun `cognitive difficulty enum labels and details are accurate`() {
        assertEquals("Standard", CognitiveDifficulty.STANDARD.label)
        assertEquals("Quick morning solve", CognitiveDifficulty.STANDARD.detail)

        assertEquals("Hard", CognitiveDifficulty.HARD.label)
        assertEquals("Deep focus challenge", CognitiveDifficulty.HARD.detail)
    }

    @Test
    fun `CognitivePrefs holds no Context or Activity reference`() {
        val fields = CognitivePrefs::class.java.declaredFields
        for (field in fields) {
            assertFalse(
                "CognitivePrefs must not hold Context: ${field.name}",
                Context::class.java.isAssignableFrom(field.type)
            )
            assertFalse(
                "CognitivePrefs must not hold Activity: ${field.name}",
                android.app.Activity::class.java.isAssignableFrom(field.type)
            )
        }
    }

    private class FakeSharedPreferences(
        private val storage: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = storage
        override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (storage[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = storage[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = storage[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = storage[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removed.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                storage.clear()
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                removed.forEach { storage.remove(it) }
                storage.putAll(temp)
            }
        }
    }
}
