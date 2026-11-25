package com.morro.alarm.puzzle.chess

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Proxy
import java.util.zip.GZIPInputStream

class PuzzleRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: MockSharedPreferences

    @Before
    fun setUp() {
        PuzzleRepository.resetForTesting()
        mockPrefs = MockSharedPreferences()

        // Allocate Context via sun.misc.Unsafe to satisfy the type without Android runtime
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafeObj = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        mockContext = allocateMethod.invoke(unsafeObj, android.content.ContextWrapper::class.java) as Context

        // Connect test hooks
        PuzzleRepository.assetOpener = { _, path -> openTestAsset(path) }
        PuzzleRepository.prefsOpener = { _, _ -> mockPrefs }
    }

    @After
    fun tearDown() {
        PuzzleRepository.resetForTesting()
    }

    private fun findAssetFile(path: String): File {
        val candidates = listOf(
            File("app/src/main/assets", path),
            File("src/main/assets", path),
            File("../app/src/main/assets", path)
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Asset not found in candidates: $path")
    }

    private fun openTestAsset(path: String): InputStream {
        return FileInputStream(findAssetFile(path))
    }

    private fun parseAssetLines(path: String): List<String> {
        val file = findAssetFile(path)
        val lines = mutableListOf<String>()
        GZIPInputStream(FileInputStream(file)).use { gzip ->
            BufferedReader(InputStreamReader(gzip, Charsets.UTF_8)).use { reader ->
                reader.forEachLine { lines.add(it) }
            }
        }
        return lines
    }

    private fun validateFen(fen: String) {
        val parts = fen.split(" ")
        assertTrue("FEN must have at least 4 fields (placement, active color, castling, ep): $fen", parts.size >= 4)
        val ranks = parts[0].split("/")
        assertEquals("FEN must have 8 ranks: $fen", 8, ranks.size)
        val validChars = setOf('p', 'r', 'n', 'b', 'q', 'k', 'P', 'R', 'N', 'B', 'Q', 'K', '1', '2', '3', '4', '5', '6', '7', '8')
        for (rank in ranks) {
            var squares = 0
            for (ch in rank) {
                assertTrue("Invalid piece char '$ch' in FEN: $fen", ch in validChars)
                squares += if (ch.isDigit()) ch.digitToInt() else 1
            }
            assertEquals("Rank must sum to 8 squares: $rank in $fen", 8, squares)
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun `every row in the sample bundled CSV parses correctly`() {
        val m1Lines = parseAssetLines("puzzles/mate_in_1.csv.gz")
        val m2Lines = parseAssetLines("puzzles/mate_in_2.csv.gz")

        assertTrue("mate_in_1 should have header + rows", m1Lines.size > 1)
        assertTrue("mate_in_2 should have header + rows", m2Lines.size > 1)

        for (line in m1Lines.drop(1)) {
            val record = PuzzleRepository.parseLine(line)
            assertNotNull("Row should parse: $line", record)
            assertTrue("mateIn1 row must contain mateIn1 in themes: $line", record!!.themes.contains("mateIn1"))
        }

        for (line in m2Lines.drop(1)) {
            val record = PuzzleRepository.parseLine(line)
            assertNotNull("Row should parse: $line", record)
            assertTrue("mateIn2 row must contain mateIn2 in themes: $line", record!!.themes.contains("mateIn2"))
        }
    }

    @Test
    fun `every row in bundled CSV assets and FALLBACK passes full FenParser and ChessRules validation`() {
        // 1. Validate hardcoded FALLBACK
        val fbBoard = FenParser.parse(PuzzleRepository.FALLBACK.fen).getOrThrow()
        val fbMoves = PuzzleRepository.FALLBACK.moves.split(" ").map { ChessMove.fromUci(it)!! }
        assertEquals(2, fbMoves.size)
        assertTrue("FALLBACK moves[0] must be legal", fbMoves[0] in ChessRules.allLegalMoves(fbBoard))
        val fbAfter0 = fbBoard.apply(fbMoves[0])
        assertTrue("FALLBACK moves[1] must be legal", fbMoves[1] in ChessRules.allLegalMoves(fbAfter0))
        val fbAfter1 = fbAfter0.apply(fbMoves[1])
        assertTrue("FALLBACK moves[1] must deliver checkmate", ChessRules.isCheckmate(fbAfter1, fbAfter1.sideToMove))

        // 2. Validate every Mate-in-1 row
        val m1Lines = parseAssetLines("puzzles/mate_in_1.csv.gz")
        assertTrue("mate_in_1 should have at least 50 puzzles", m1Lines.size > 50)
        for (line in m1Lines.drop(1)) {
            val record = PuzzleRepository.parseLine(line)
            assertNotNull("Row should parse & validate: $line", record)
            val board = FenParser.parse(record!!.fen).getOrThrow()
            val moves = record.moves.split(" ").map { ChessMove.fromUci(it)!! }
            assertEquals("Mate-in-1 must have 2 moves: $line", 2, moves.size)
            assertTrue("Moves[0] illegal in $line", moves[0] in ChessRules.allLegalMoves(board))
            val b1 = board.apply(moves[0])
            assertTrue("Moves[1] illegal in $line", moves[1] in ChessRules.allLegalMoves(b1))
            val b2 = b1.apply(moves[1])
            assertTrue("Moves[1] does not deliver checkmate in $line", ChessRules.isCheckmate(b2, b2.sideToMove))
        }

        // 3. Validate every Mate-in-2 row
        val m2Lines = parseAssetLines("puzzles/mate_in_2.csv.gz")
        assertTrue("mate_in_2 should have at least 50 puzzles", m2Lines.size > 50)
        for (line in m2Lines.drop(1)) {
            val record = PuzzleRepository.parseLine(line)
            assertNotNull("Row should parse & validate: $line", record)
            val board = FenParser.parse(record!!.fen).getOrThrow()
            val moves = record.moves.split(" ").map { ChessMove.fromUci(it)!! }
            assertEquals("Mate-in-2 must have 4 moves: $line", 4, moves.size)
            assertTrue("Moves[0] illegal in $line", moves[0] in ChessRules.allLegalMoves(board))
            val b1 = board.apply(moves[0])
            assertFalse("Mate-in-2 puzzle already has mate-in-1 before Moves[1]: $line", ChessRules.hasMateInOne(b1))
            assertTrue("Moves[1] illegal in $line", moves[1] in ChessRules.allLegalMoves(b1))
            val b2 = b1.apply(moves[1])
            assertTrue("Moves[1] is not a forced mate-in-2 in $line", ChessRules.isForcedMateInTwo(b2))
            assertTrue("Moves[2] illegal in $line", moves[2] in ChessRules.allLegalMoves(b2))
            val b3 = b2.apply(moves[2])
            assertTrue("Moves[3] illegal in $line", moves[3] in ChessRules.allLegalMoves(b3))
            val b4 = b3.apply(moves[3])
            assertTrue("Moves[3] does not deliver checkmate in $line", ChessRules.isCheckmate(b4, b4.sideToMove))
        }
    }

    @Test
    fun `pickDeterministic is pure and reproducible without clearing prefs`() = runBlocking {
        PuzzleRepository.load(mockContext)
        val p1 = PuzzleRepository.pickDeterministic(CognitiveDifficulty.STANDARD, seed = 42L)
        val p2 = PuzzleRepository.pickDeterministic(CognitiveDifficulty.STANDARD, seed = 42L)
        assertEquals(p1.puzzleId, p2.puzzleId)
    }

    @Test
    fun `moves string has the expected length`() {
        val m1Lines = parseAssetLines("puzzles/mate_in_1.csv.gz")
        val m2Lines = parseAssetLines("puzzles/mate_in_2.csv.gz")

        for (line in m1Lines.drop(1)) {
            val record = PuzzleRepository.parseLine(line)!!
            val tokens = record.moves.split(" ").filter { it.isNotBlank() }
            assertTrue("Mate in 1 must have >= 2 move tokens: ${record.moves}", tokens.size >= 2)
        }

        for (line in m2Lines.drop(1)) {
            val record = PuzzleRepository.parseLine(line)!!
            val tokens = record.moves.split(" ").filter { it.isNotBlank() }
            assertTrue("Mate in 2 must have >= 4 move tokens: ${record.moves}", tokens.size >= 4)
        }
    }

    @Test
    fun `repository loads successfully with test resources`() = runBlocking {
        PuzzleRepository.load(mockContext)
        val (m1Count, m2Count) = PuzzleRepository.getLoadedCounts()
        assertTrue("mateIn1 loaded: $m1Count", m1Count >= 20)
        assertTrue("mateIn2 loaded: $m2Count", m2Count >= 15)

        // Second call should be a no-op
        PuzzleRepository.load(mockContext)
        assertEquals("Loaded counts unchanged after second load", Pair(m1Count, m2Count), PuzzleRepository.getLoadedCounts())
    }

    @Test
    fun `no Context retained after load`() {
        // Verify via reflection that PuzzleRepository holds no Context fields
        val fields = PuzzleRepository::class.java.declaredFields
        for (field in fields) {
            assertFalse(
                "Field '${field.name}' (${field.type.name}) holds or is a Context",
                Context::class.java.isAssignableFrom(field.type)
            )
        }
    }

    @Test
    fun `pick returns different puzzles for different seeds`() = runBlocking {
        PuzzleRepository.load(mockContext)
        val puzzleA = PuzzleRepository.pick(CognitiveDifficulty.STANDARD, seed = 12345L, context = mockContext)
        val puzzleB = PuzzleRepository.pick(CognitiveDifficulty.STANDARD, seed = 67890L, context = mockContext)

        assertNotNull(puzzleA)
        assertNotNull(puzzleB)
        // With distinct seeds across 25 puzzles, they should be different
        assertNotEquals("Different seeds should yield different puzzles", puzzleA.puzzleId, puzzleB.puzzleId)
    }

    @Test
    fun `pick returns the same puzzle for the same seed`() = runBlocking {
        // Reset recent history between picks so recent filter does not change the pool
        mockPrefs.clear()
        val puzzle1 = PuzzleRepository.pick(CognitiveDifficulty.STANDARD, seed = 42L, context = mockContext)

        mockPrefs.clear()
        val puzzle2 = PuzzleRepository.pick(CognitiveDifficulty.STANDARD, seed = 42L, context = mockContext)

        assertEquals("Same seed must yield same puzzle", puzzle1.puzzleId, puzzle2.puzzleId)
        assertEquals("Same seed must yield same FEN", puzzle1.fen, puzzle2.fen)
        assertEquals("Same seed must yield same moves", puzzle1.moves, puzzle2.moves)
    }

    @Test
    fun `pick for hard returns mate in 2 puzzle`() = runBlocking {
        mockPrefs.clear()
        val puzzle = PuzzleRepository.pick(CognitiveDifficulty.HARD, seed = 999L, context = mockContext)
        assertTrue("HARD difficulty must return mateIn2 puzzle: ${puzzle.themes}", puzzle.themes.contains("mateIn2"))
        val tokens = puzzle.moves.split(" ").filter { it.isNotBlank() }
        assertTrue("HARD puzzle must have >= 4 move tokens", tokens.size >= 4)
    }

    @Test
    fun `malformed rows are skipped without throwing`() {
        val malformedLine1 = "invalid,line"
        val malformedLine2 = "id,fen,moves,not_a_rating,theme"
        val malformedLine3 = ",,,,,"

        assertEquals(null, PuzzleRepository.parseLine(malformedLine1))
        assertEquals(null, PuzzleRepository.parseLine(malformedLine2))
        assertEquals(null, PuzzleRepository.parseLine(malformedLine3))
    }

    @Test
    fun `corrupted assets return fallback puzzle`() = runBlocking {
        PuzzleRepository.assetOpener = { _, _ -> ByteArrayInputStream("corrupted data not gzip".toByteArray()) }
        val fallback = PuzzleRepository.pick(CognitiveDifficulty.STANDARD, seed = 1L, context = mockContext)
        assertEquals(PuzzleRepository.FALLBACK.puzzleId, fallback.puzzleId)
    }

    @Test
    fun `recent history avoids repeating last 20 served puzzles`() = runBlocking {
        PuzzleRepository.load(mockContext)
        val pickedIds = mutableSetOf<String>()

        // Pick 10 times with different seeds
        for (i in 1..10) {
            val p = PuzzleRepository.pick(CognitiveDifficulty.STANDARD, seed = i.toLong() * 1000L, context = mockContext)
            pickedIds.add(p.puzzleId)
            val stored = mockPrefs.getString("last_puzzle_ids_std", "") ?: ""
            assertTrue("Recently picked ID should be stored in prefs", stored.contains(p.puzzleId))
        }
        assertTrue("Multiple distinct puzzles should be picked", pickedIds.size >= 8)
    }
}

/**
 * Minimal in-memory [SharedPreferences] implementation for JVM unit tests.
 */
class MockSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    fun clear() {
        data.clear()
    }

    override fun getAll(): MutableMap<String, *> = data

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null && value != null) temp[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null && values != null) temp[key] = values
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
            if (key != null) temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            temp.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            for ((k, v) in temp) {
                if (v == null) data.remove(k) else data[k] = v
            }
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
