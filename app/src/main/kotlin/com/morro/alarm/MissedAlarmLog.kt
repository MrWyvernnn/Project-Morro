package com.morro.alarm

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * A local record of the rings that did not go to plan.
 *
 * Morro has no analytics and never will, which means the only way to learn that an OEM is killing
 * alarms is to write it down on the phone and let the person show it to us. This file is that
 * notebook: a ten-entry ring buffer in `SharedPreferences`, no network, no identifier.
 *
 * Two failure shapes are recorded, because they have different causes and different fixes:
 *
 * - [AlarmEventKind.LATE_FIRE] — the ring arrived, but later than scheduled. Almost always Doze or
 *   an OEM background limit holding the broadcast.
 * - [AlarmEventKind.NEVER_FIRED] — the scheduled time passed with no ring at all. Almost always the
 *   app being force-stopped, auto-start being denied, or the alarm being wiped by the system.
 *
 * A missing ring can only be inferred, and inference needs care: "the stored trigger is in the past"
 * is also true for a ring that happened and was dismissed while the process died before it could
 * re-arm. That is why [AlarmEventKind.NEVER_FIRED] requires `lastFiredAt < expectedAt` — the ring
 * really never happened for that occurrence.
 */
enum class AlarmEventKind {
    LATE_FIRE,
    NEVER_FIRED,
}

/**
 * One logged occurrence. Kept as plain data so the classification rules below can be unit-tested
 * without a device, a Robolectric runner, or an Android `Context`.
 *
 * `manufacturer` and `model` are captured when the event happens, not when it is read: the point is
 * to know which phone failed, and a person may later swap devices.
 */
data class AlarmEvent(
    val kind: AlarmEventKind,
    val expectedAtMillis: Long,
    val observedAtMillis: Long,
    val manufacturer: String,
    val model: String,
)

/** A ring later than this counts as late. Under a minute is ordinary scheduling jitter. */
internal const val LATE_THRESHOLD_MILLIS = 60_000L

/** Enough history to spot a pattern, small enough to read on one screen. */
internal const val EVENT_CAPACITY = 10

/**
 * Classifies a ring against the time it was expected, or null when it was on time.
 *
 * A negative delta is not late — it means the clock moved (a manual time change or a timezone
 * change after the alarm was armed), and that is already handled by `BootReceiver`, not by this log.
 */
internal fun lateFireFor(expectedAtMillis: Long?, observedAtMillis: Long): AlarmEventKind? {
    if (expectedAtMillis == null) return null
    return if (observedAtMillis - expectedAtMillis > LATE_THRESHOLD_MILLIS) {
        AlarmEventKind.LATE_FIRE
    } else {
        null
    }
}

/**
 * True when an armed alarm's time has passed and no ring was ever recorded for it.
 *
 * `expectedAtMillis >= now` means the alarm has not been due yet. `lastFiredAtMillis >=
 * expectedAtMillis` means something did ring for that occurrence, so the alarm fired and the only
 * unanswered question is whether the person dismissed it — not this log's business.
 */
internal fun isNeverFired(
    armed: Boolean,
    expectedAtMillis: Long?,
    nowMillis: Long,
    lastFiredAtMillis: Long,
): Boolean {
    if (!armed || expectedAtMillis == null) return false
    if (expectedAtMillis >= nowMillis) return false
    return lastFiredAtMillis < expectedAtMillis
}

/**
 * Newest first, capped at [capacity]. A ring buffer, so the buffer never grows into a history
 * screen nobody asked for.
 */
internal fun appendEvent(
    existing: List<AlarmEvent>,
    event: AlarmEvent,
    capacity: Int = EVENT_CAPACITY,
): List<AlarmEvent> = (listOf(event) + existing).take(capacity)

/**
 * Storage for [AlarmEvent]s and the two timestamps the inference in [isNeverFired] needs.
 *
 * Each event lives under its own keyed slot (`alarm_log_0.kind`, `alarm_log_0.expected`, …) rather
 * than in one delimited string. `Build.MODEL` can contain commas, quotes and newlines, and a single
 * blob would need an escaping scheme that fails quietly. Keyed primitives cannot be mis-parsed.
 */
object MissedAlarmLog {

    private const val TAG = "MissedAlarmLog"
    private const val PREFS_NAME = "morro_prefs"

    private const val KEY_EVENT_COUNT = "alarm_log_count"
    private const val KEY_LAST_FIRED = "alarm_last_fired_at"
    private const val KEY_LAST_COMPLETED = "alarm_last_completed_at"
    private const val KEY_DISMISSED_EXPECTED = "alarm_log_dismissed_expected"

    /**
     * The occurrence that most recently rang, as its expected instant.
     *
     * Kept separately from [KEY_LAST_FIRED] because the two answer different questions.
     * `alarm_last_fired_at` is *when* something rang, which is what the late/missed inference needs.
     * This is *which* occurrence rang, which is what stops a second delivery for the same
     * occurrence from ringing twice — and it survives a clock change, where comparing two
     * wall-clock instants does not.
     */
    private const val KEY_HANDLED_OCCURRENCE = "alarm_handled_occurrence"

    private const val SLOT = "alarm_log_"
    private const val FIELD_KIND = ".kind"
    private const val FIELD_EXPECTED = ".expected"
    private const val FIELD_OBSERVED = ".observed"
    private const val FIELD_MANUFACTURER = ".manufacturer"
    private const val FIELD_MODEL = ".model"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Records that the ring for [expectedAtMillis] started.
     *
     * Called from the broadcast, before anything re-arms. It stores both facts in one write: when
     * it rang, and which occurrence it was. The occurrence is what makes the catch-up path
     * idempotent — a second delivery for the same instant can see that this instant already rang.
     */
    fun recordFired(
        context: Context,
        expectedAtMillis: Long?,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val editor = prefs(context).edit().putLong(KEY_LAST_FIRED, atMillis)
        if (expectedAtMillis != null) {
            editor.putLong(KEY_HANDLED_OCCURRENCE, expectedAtMillis)
        }
        editor.apply()
    }

    /** When something last rang, or 0 when nothing ever has. */
    fun lastFiredAt(context: Context): Long = prefs(context).getLong(KEY_LAST_FIRED, 0L)

    /**
     * The expected instant of the occurrence that last rang, or [Long.MIN_VALUE] when none has.
     *
     * `Long.MIN_VALUE` rather than `0` because `0` is a legal (if ancient) instant, and a sentinel
     * that can collide with real data is how a duplicate check starts suppressing real rings.
     */
    fun handledOccurrence(context: Context): Long =
        prefs(context).getLong(KEY_HANDLED_OCCURRENCE, Long.MIN_VALUE)

    /**
     * Records an occurrence that came due and never rang.
     *
     * Used by the boot catch-up path, which is the one place that can know an occurrence was
     * missed *before* ringing it: if the ring then fails to start, the record is the only trace
     * that the morning went wrong.
     */
    fun recordNeverFired(
        context: Context,
        expectedAtMillis: Long,
        observedAtMillis: Long = System.currentTimeMillis(),
    ) {
        record(
            context,
            AlarmEvent(
                kind = AlarmEventKind.NEVER_FIRED,
                expectedAtMillis = expectedAtMillis,
                observedAtMillis = observedAtMillis,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
            )
        )
        Log.w(TAG, "Recorded a missed ring for $expectedAtMillis on ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    /** Records that the person finished the gates and dismissed the ring. */
    fun recordCompleted(context: Context, atMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_COMPLETED, atMillis).apply()
    }

    fun recordLateFire(
        context: Context,
        expectedAtMillis: Long,
        observedAtMillis: Long = System.currentTimeMillis(),
    ) {
        record(
            context,
            AlarmEvent(
                kind = AlarmEventKind.LATE_FIRE,
                expectedAtMillis = expectedAtMillis,
                observedAtMillis = observedAtMillis,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
            )
        )
    }

    /**
     * Runs the inference on app open and returns the event worth showing, if any.
     *
     * It never records the same occurrence twice: a person can open Morro a dozen times before they
     * act on the card, and a log that fills up with one missed morning would hide the next one.
     */
    fun evaluateMissedRing(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): AlarmEvent? {
        val armed = AlarmScheduler.isArmed(context)
        val expected = AlarmScheduler.getNextAlarmTime(context)
        val firedAt = prefs(context).getLong(KEY_LAST_FIRED, 0L)

        if (!isNeverFired(armed, expected, nowMillis, firedAt)) return latestNeverFired(context, expected)

        val alreadyLogged = events(context).any {
            it.kind == AlarmEventKind.NEVER_FIRED && it.expectedAtMillis == expected
        }
        if (alreadyLogged) return latestNeverFired(context, expected)

        val event = AlarmEvent(
            kind = AlarmEventKind.NEVER_FIRED,
            expectedAtMillis = expected ?: 0L,
            observedAtMillis = nowMillis,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
        )
        record(context, event)
        Log.w(TAG, "Recorded a missed ring for $expected on ${event.manufacturer} ${event.model}")
        return event
    }

    /** Newest first. */
    fun events(context: Context): List<AlarmEvent> {
        val prefs = prefs(context)
        val count = prefs.getInt(KEY_EVENT_COUNT, 0).coerceIn(0, EVENT_CAPACITY)
        return (0 until count).mapNotNull { index -> readSlot(prefs, index) }
    }

    /** The most recent event, or null when nothing has gone wrong yet. */
    fun latest(context: Context): AlarmEvent? = events(context).firstOrNull()

    fun isDismissed(context: Context, event: AlarmEvent): Boolean =
        prefs(context).getLong(KEY_DISMISSED_EXPECTED, Long.MIN_VALUE) == event.expectedAtMillis

    /**
     * Dismisses one event for good. Stored by expected time, so the same card never returns but a
     * different missed morning still speaks up.
     */
    fun dismiss(context: Context, event: AlarmEvent) {
        prefs(context).edit()
            .putLong(KEY_DISMISSED_EXPECTED, event.expectedAtMillis)
            .apply()
    }

    private fun latestNeverFired(context: Context, expectedAtMillis: Long?): AlarmEvent? =
        events(context).firstOrNull {
            it.kind == AlarmEventKind.NEVER_FIRED && it.expectedAtMillis == expectedAtMillis
        }

    private fun record(context: Context, event: AlarmEvent) {
        val updated = appendEvent(events(context), event)
        val prefs = prefs(context)
        val editor = prefs.edit()

        // Slots beyond the new size are removed rather than left behind: a stale tenth slot would
        // resurface as a phantom event the moment the count went back up.
        for (index in 0 until EVENT_CAPACITY) {
            val slot = "$SLOT$index"
            val entry = updated.getOrNull(index)
            if (entry == null) {
                editor.remove(slot + FIELD_KIND)
                editor.remove(slot + FIELD_EXPECTED)
                editor.remove(slot + FIELD_OBSERVED)
                editor.remove(slot + FIELD_MANUFACTURER)
                editor.remove(slot + FIELD_MODEL)
            } else {
                editor.putString(slot + FIELD_KIND, entry.kind.name)
                editor.putLong(slot + FIELD_EXPECTED, entry.expectedAtMillis)
                editor.putLong(slot + FIELD_OBSERVED, entry.observedAtMillis)
                editor.putString(slot + FIELD_MANUFACTURER, entry.manufacturer)
                editor.putString(slot + FIELD_MODEL, entry.model)
            }
        }
        editor.putInt(KEY_EVENT_COUNT, updated.size)
        editor.apply()
    }

    private fun readSlot(prefs: SharedPreferences, index: Int): AlarmEvent? {
        val slot = "$SLOT$index"
        val kindName = prefs.getString(slot + FIELD_KIND, null) ?: return null
        val kind = try {
            AlarmEventKind.valueOf(kindName)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown alarm event kind '$kindName'; skipping slot $index")
            return null
        }
        return AlarmEvent(
            kind = kind,
            expectedAtMillis = prefs.getLong(slot + FIELD_EXPECTED, 0L),
            observedAtMillis = prefs.getLong(slot + FIELD_OBSERVED, 0L),
            manufacturer = prefs.getString(slot + FIELD_MANUFACTURER, "").orEmpty(),
            model = prefs.getString(slot + FIELD_MODEL, "").orEmpty(),
        )
    }

    /** Debug-only: wipes the log so a test miss does not linger. */
    internal fun clear(context: Context) {
        val editor = prefs(context).edit()
        for (index in 0 until EVENT_CAPACITY) {
            val slot = "$SLOT$index"
            editor.remove(slot + FIELD_KIND)
            editor.remove(slot + FIELD_EXPECTED)
            editor.remove(slot + FIELD_OBSERVED)
            editor.remove(slot + FIELD_MANUFACTURER)
            editor.remove(slot + FIELD_MODEL)
        }
        editor.remove(KEY_EVENT_COUNT)
        editor.remove(KEY_DISMISSED_EXPECTED)
        editor.remove(KEY_HANDLED_OCCURRENCE)
        editor.apply()
    }
}
