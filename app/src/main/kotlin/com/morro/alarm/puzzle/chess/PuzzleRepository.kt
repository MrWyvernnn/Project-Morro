package com.morro.alarm.puzzle.chess

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.random.Random

/**
 * Cognitive difficulty level for puzzle selection.
 * STANDARD → mate-in-1, HARD → mate-in-2.
 */
enum class CognitiveDifficulty { STANDARD, HARD }

/**
 * Singleton repository that loads and serves verified chess puzzles from bundled
 * gzipped CSV assets.
 *
 * - [load] reads and validates the assets once; subsequent calls are no-ops.
 * - [pick] selects a puzzle for a ring, avoiding recently served IDs per difficulty.
 * - [pickDeterministic] performs pure side-effect-free selection from an explicit exclusion set.
 * - No [Context] reference is retained after [load] or [pick] returns.
 */
object PuzzleRepository {

    private const val ASSET_MATE_IN_1 = "puzzles/mate_in_1.csv.gz"
    private const val ASSET_MATE_IN_2 = "puzzles/mate_in_2.csv.gz"
    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_LAST_IDS_STANDARD = "last_puzzle_ids_std"
    private const val KEY_LAST_IDS_HARD = "last_puzzle_ids_hard"
    private const val RECENT_HISTORY_SIZE = 20

    /**
     * Verified hardcoded fallback if assets cannot be loaded.
     * Black plays g8h8, White delivers back-rank mate with e1e8#.
     */
    val FALLBACK = PuzzleRecord(
        puzzleId = "fallback_001",
        fen = "6k1/5ppp/8/8/8/8/5PPP/4R1K1 b - - 0 1",
        moves = "g8h8 e1e8",
        rating = 600,
        themes = "mateIn1 backRankMate short"
    )

    @Volatile
    private var mateIn1: List<PuzzleRecord>? = null

    @Volatile
    private var mateIn2: List<PuzzleRecord>? = null

    private val mutex = Mutex()

    // Test hooks to allow injecting custom asset and prefs loaders in local unit tests.
    internal var assetOpener: ((Context, String) -> InputStream)? = null
    internal var prefsOpener: ((Context, String) -> SharedPreferences)? = null

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Reads and parses the gzipped CSV assets into memory. Safe to call from
     * any coroutine; runs on [Dispatchers.IO]. The second call is a no-op.
     *
     * @param context used only for [Context.assets]; not retained.
     */
    private fun safeAppContext(context: Context): Context =
        try { context.applicationContext ?: context } catch (_: Throwable) { context }

    suspend fun load(context: Context) {
        val appCtx = safeAppContext(context)
        mutex.withLock {
            if (mateIn1 != null && mateIn2 != null) return
            val (loadedM1, loadedM2) = withContext(Dispatchers.IO) {
                parseAsset(appCtx, ASSET_MATE_IN_1) to parseAsset(appCtx, ASSET_MATE_IN_2)
            }
            mateIn1 = loadedM1
            mateIn2 = loadedM2
        }
    }

    /**
     * Pure deterministic puzzle selection from the loaded pool, excluding [excludeIds].
     * Never mutates SharedPreferences or any global state.
     */
    fun pickDeterministic(
        difficulty: CognitiveDifficulty,
        seed: Long,
        excludeIds: Set<String> = emptySet()
    ): PuzzleRecord {
        val pool = when (difficulty) {
            CognitiveDifficulty.STANDARD -> mateIn1
            CognitiveDifficulty.HARD -> mateIn2
        }
        if (pool.isNullOrEmpty()) {
            return ChessEngine.generateTacticalPuzzle(difficulty, seed) ?: FALLBACK
        }

        val candidates = if (excludeIds.isEmpty()) pool else pool.filter { it.puzzleId !in excludeIds }
        val source = candidates.ifEmpty { pool }
        val rng = Random(seed)
        return source[rng.nextInt(source.size)]
    }

    /**
     * Picks a puzzle for [difficulty] using [seed], avoiding recently served IDs
     * stored in SharedPreferences. Thread-safe and race-free via [mutex].
     */
    suspend fun pick(
        difficulty: CognitiveDifficulty,
        seed: Long,
        context: Context
    ): PuzzleRecord {
        val appCtx = safeAppContext(context)
        load(appCtx)

        return mutex.withLock {
            val pool = when (difficulty) {
                CognitiveDifficulty.STANDARD -> mateIn1
                CognitiveDifficulty.HARD -> mateIn2
            }
            if (pool.isNullOrEmpty()) return@withLock FALLBACK

            val key = historyKey(difficulty)
            val recent = getRecentIds(appCtx, key)
            val chosen = pickDeterministic(difficulty, seed, recent)
            val cap = minOf(RECENT_HISTORY_SIZE, (pool.size - 1).coerceAtLeast(0))
            saveRecentId(appCtx, key, chosen.puzzleId, cap)
            chosen
        }
    }

    // ── internals ───────────────────────────────────────────────────────

    private fun historyKey(difficulty: CognitiveDifficulty): String = when (difficulty) {
        CognitiveDifficulty.STANDARD -> KEY_LAST_IDS_STANDARD
        CognitiveDifficulty.HARD -> KEY_LAST_IDS_HARD
    }

    /**
     * Parses a gzipped CSV asset into a list of verified [PuzzleRecord]s.
     * Malformed or illegal rows are skipped. Returns fallback list if corrupt/empty.
     */
    private fun parseAsset(context: Context, path: String): List<PuzzleRecord> {
        return try {
            val records = mutableListOf<PuzzleRecord>()
            val stream = assetOpener?.invoke(context, path) ?: context.assets.open(path)
            stream.use { raw ->
                GZIPInputStream(raw).use { gzip ->
                    BufferedReader(InputStreamReader(gzip, Charsets.UTF_8)).use { reader ->
                        var first = true
                        reader.forEachLine { line ->
                            if (first) {
                                first = false
                                return@forEachLine // skip header
                            }
                            parseLine(line)?.let { records.add(it) }
                        }
                    }
                }
            }
            if (records.isEmpty()) listOf(FALLBACK) else records
        } catch (_: Throwable) {
            listOf(FALLBACK)
        }
    }

    /**
     * Parses and validates one CSV line into a [PuzzleRecord], or null if malformed/illegal.
     * Expected columns: PuzzleId,FEN,Moves,Rating,Themes
     */
    internal fun parseLine(line: String): PuzzleRecord? {
        val parts = line.split(",", limit = 5)
        if (parts.size < 5) return null
        val rating = parts[3].trim().toIntOrNull() ?: return null
        val puzzleId = parts[0].trim()
        val fen = parts[1].trim()
        val moves = parts[2].trim()
        val themes = parts[4].trim()

        if (puzzleId.isEmpty() || fen.isEmpty() || moves.isEmpty()) return null

        val board = FenParser.parse(fen).getOrNull() ?: return null
        val moveTokens = moves.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (moveTokens.size < 2) return null

        val setupMove = ChessMove.fromUci(moveTokens[0]) ?: return null
        if (setupMove !in ChessRules.allLegalMoves(board)) return null
        val afterSetup = board.apply(setupMove)
        val firstUserMove = ChessMove.fromUci(moveTokens[1]) ?: return null
        if (firstUserMove !in ChessRules.allLegalMoves(afterSetup)) return null

        return PuzzleRecord(
            puzzleId = puzzleId,
            fen = fen,
            moves = moves,
            rating = rating,
            themes = themes
        )
    }

    // ── SharedPreferences helpers (per-difficulty deduplicated LRU) ──────

    private fun getPrefs(context: Context): SharedPreferences? {
        return try {
            prefsOpener?.invoke(context, PREFS_NAME)
                ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getRecentIds(context: Context, key: String): Set<String> {
        val prefs = getPrefs(context) ?: return emptySet()
        val raw = prefs.getString(key, "") ?: ""
        return raw.split("|").filter { it.isNotBlank() }.toSet()
    }

    private fun saveRecentId(context: Context, key: String, id: String, maxSize: Int) {
        if (maxSize <= 0) return
        val prefs = getPrefs(context) ?: return
        val raw = prefs.getString(key, "") ?: ""
        val list = raw.split("|").filter { it.isNotBlank() }.toMutableList()
        list.remove(id)
        list.add(id)
        while (list.size > maxSize) list.removeAt(0)
        prefs.edit().putString(key, list.joinToString("|")).apply()
    }

    // ── testing helpers ─────────────────────────────────────────────────

    /**
     * Resets cached state and test hooks. **Only for unit tests.**
     */
    internal fun resetForTesting() {
        mateIn1 = null
        mateIn2 = null
        assetOpener = null
        prefsOpener = null
    }

    internal fun getLoadedCounts(): Pair<Int, Int> {
        return Pair(mateIn1?.size ?: 0, mateIn2?.size ?: 0)
    }
}
