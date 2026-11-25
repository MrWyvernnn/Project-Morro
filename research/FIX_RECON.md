# FIX_RECON — reliability bug-fix pass (Phase 0)

Read-only recon. No production code was touched while writing this.

## 0 · Baseline

| | |
|---|---|
| Branch | `main`, working tree clean |
| HEAD | `e0d1d07 chess/sudoku` |
| Previous | `4879d99 ask once after a dismissal whether the person is still up` |

**The brief's file list is out of date in two ways, and both matter for this pass:**

1. `e0d1d07` landed after the last reliability commit and adds a whole cognitive-gate
   subsystem: `puzzle/chess/*`, `puzzle/sudoku/*`, `ui/puzzle/*`, `GateSilenceController.kt`,
   two CSV assets, `research/GATE_INTEGRATION.md`, `research/PUZZLE_STATUS.md`, `tools/*.py`.
   It also rewrites large parts of `AlarmActivity.kt` (473 lines) and `AlarmSoundPlayer.kt`.
2. `app/src/main/res/drawable/` **does not exist**. There is no drawable directory at all,
   only `mipmap-anydpi-v26/ic_launcher.xml` and `values/`.

The ring-path logic this pass cares about survived that commit unchanged: `finishAlarm()`,
`promptForTomorrowMemo()`, `rearmNextWakeUp()`, `isWakeUpCheckRing` and the
`MissedAlarmLog.recordCompleted` hook are all still present and semantically identical
(`AlarmActivity.kt:428-504`). `GateSilenceController` only drives Morro's own
`MediaPlayer` volume (`0.0f`/`1.0f`) and never touches system streams, so it does not
interact with anything below.

**The five competitor documents named in §1 of the brief are not in this repo**
(`alarmy-architecture.md`, `sleep-as-android-architecture.md`, `sleep-cycle-analysis.md`,
`alarm-architecture-study.md`, `competitive-retention-teardown.md` — all "not found").
`research/` contains only `GATE_INTEGRATION.md` and `PUZZLE_STATUS.md`. Nothing was
implemented from either.

---

## 1 · Verdict on the six findings

| # | Finding | Verdict |
|---|---|---|
| 1 | Locked-boot overwrite race | **Confirmed as an invariant violation in code.** The "no ring at all" outcome is *not* confirmed — it needs the device test, and the system's handling of a deferred alarm broadcast decides it. See §2 for the exact branch. |
| 2 | `armWithoutPersisting` could destroy the mirror | **Confirmed as a live hazard, not a live bug.** Nothing currently calls `mirrorFrom` from that path, but nothing enforces it either. |
| 3 | Stale "didn't ring" card after a late ring | **Confirmed, and worse than described.** `LATE_FIRE` events are recorded but *never displayed at all* — both selection paths only return `NEVER_FIRED`. |
| 4 | `restoreAfterBoot` asks early and shortens the window | **Confirmed.** No key records that the question was shown; `showQuestion()` is called unconditionally on restore. |
| 5 | Stock notification icon | **Confirmed, and broader.** Three `setSmallIcon` call sites, all framework drawables. There is no app icon resource to reference yet. |
| 6A | `record()` writes 50 keys per event | **Confirmed.** Loop is `0 until 10`, five fields per slot, in the shared `morro_prefs` file, and `recordLateFire` is called on the ring path. |
| 6B | `BatteryOptimizationDebugActivity` | **Confirmed dead** — declared, compiled into release, never launched. Not debug-gated. |
| 6C | Repo composition | **Confirmed**, numbers below. |

Already fixed / no action: `USE_EXACT_ALARM` + capped `SCHEDULE_EXACT_ALARM`, foreground
service type, `MY_PACKAGE_REPLACED`/`TIME_SET`/`TIMEZONE_CHANGED`, sign-in unrouted.

---

## 2 · `AlarmScheduler.kt` — full quotes requested by §2

`ALARM_REQUEST_CODE = 1001` (`:30`). Every arm and every cancel uses it.

```kotlin
fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int): Boolean {
    val scheduled = scheduleAlarm(context, nextOccurrenceMillis(hour, minute))
    if (scheduled) {
        context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE).edit()
            .putInt(KEY_ALARM_HOUR, hour)
            .putInt(KEY_ALARM_MINUTE, minute)
            .apply()
        AlarmBootMirror.mirrorFrom(context)
    }
    return scheduled
}
```

```kotlin
fun scheduleAlarm(context: Context, triggerTimeMillis: Long): Boolean {
    val finalTriggerTime = rollForwardIfPast(triggerTimeMillis)
    if (!armAlarmClock(context, finalTriggerTime)) return false
    savePrefs(context, finalTriggerTime)
    return true
}

fun armWithoutPersisting(context: Context, triggerTimeMillis: Long): Boolean =
    armAlarmClock(context, rollForwardIfPast(triggerTimeMillis))
```

```kotlin
fun getSavedAlarmTime(context: Context): Pair<Int, Int>? {
    val prefs = context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE)
    if (!prefs.contains(KEY_ALARM_HOUR) || !prefs.contains(KEY_ALARM_MINUTE)) return null
    val hour = prefs.getInt(KEY_ALARM_HOUR, -1)
    val minute = prefs.getInt(KEY_ALARM_MINUTE, -1)
    return if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
}

fun isArmed(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ALARM_ARMED, false)

fun getNextAlarmTime(context: Context): Long? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val v = prefs.getLong(KEY_NEXT_ALARM_TIME, -1L)
    return if (v == -1L) null else v
}
```

```kotlin
fun cancelAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmReceiver.ACTION_TRIGGER_ALARM
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST_CODE, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    try { alarmManager.cancel(pendingIntent) } catch (e: Exception) { }
    clearPrefs(context)
    AlarmBootMirror.clear(context)
}
```

`nextOccurrenceMillis(hour, minute, nowMillis)` sets the wall clock to `hour:minute`,
zeroes seconds/ms, and adds a day when `<= now` (`:60-73`). `rollForwardIfPast` does the
same thing for an absolute instant (`:161-170`).

`armAlarmClock` (`:117-155`) is the single scheduling primitive: `canScheduleExactAlarms()`
guard on S+, `setAlarmClock(AlarmClockInfo(finalTriggerTime, showPendingIntent), pendingIntent)`,
and `false` (not a throw) on failure.

### Request codes and PendingIntent flags — every site

| Owner | Request code | Kind | Flags |
|---|---|---|---|
| `AlarmScheduler.armAlarmClock` | `1001` | broadcast → `AlarmReceiver` | `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` |
| `AlarmScheduler.armAlarmClock` (show) | `0` | activity → `MainActivity` | `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` |
| `AlarmScheduler.cancelAlarm` | `1001` | broadcast → `AlarmReceiver` | same |
| `WakeUpCheck.schedule` | `1003` / `1004` | broadcast → `WakeUpCheckReceiver` | same |
| `WakeUpCheck.schedule` (show) | `0` | activity → `MainActivity` | same |
| `WakeUpCheck.showQuestion` (`I'm up`) | `1003` | broadcast → `WakeUpCheckReceiver` | same |
| `AlarmReceiver.postFallbackNotification` | `2002` | activity → `AlarmActivity` | same |
| `WakeUpCheck.NOTIFICATION_ID` | `1005` | notification id | — |
| `AlarmForegroundService.NOTIFICATION_ID` | `1002` | notification id | — |

**There is exactly one alarm slot.** `armAlarmClock` is shared by the daily alarm, a snooze,
a re-arm and the locked-boot arm, and all four use request code `1001` with
`FLAG_UPDATE_CURRENT`. That is the mechanism behind Finding 1: the last writer wins.

### Which paths touch the mirror

| Path | Persists `morro_prefs` | Touches `AlarmBootMirror` |
|---|---|---|
| `scheduleAlarm` | yes (`savePrefs`) | **no** |
| `scheduleDailyAlarm` | yes (hour/minute) | yes — `mirrorFrom` on success |
| `snoozeAlarm` | yes (via `scheduleAlarm`) | yes — `mirrorFrom` on success |
| `cancelAlarm` | yes (`clearPrefs`) | yes — `clear` |
| `armWithoutPersisting` | **no** | **no** |

`scheduleAlarm` is called directly by `BootReceiver.rearmAfterUnlock` when the stored
trigger is still in the future. It does not refresh the mirror, which is harmless there
because the mirror already holds the same instant — noted as drift, not fixed.

---

## 3 · `BootReceiver.kt` — four branches

| Action | Handler | Scheduler calls |
|---|---|---|
| `LOCKED_BOOT_COMPLETED` | `rearmBeforeFirstUnlock` | reads `AlarmBootMirror` only; `armWithoutPersisting(trigger)` |
| `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` | `rearmAfterUnlock` | `WakeUpCheck.restoreAfterBoot`; then `scheduleAlarm(stored)` if future, else `scheduleDailyAlarm(hour, minute)` |
| `TIME_SET`, `TIMEZONE_CHANGED` | `rescheduleForNewClock` | `scheduleDailyAlarm(hour, minute)` |

```kotlin
private fun rearmAfterUnlock(context: Context) {
    WakeUpCheck.restoreAfterBoot(context)
    if (!AlarmScheduler.isArmed(context)) return

    val nextAlarmTime = AlarmScheduler.getNextAlarmTime(context)
    if (nextAlarmTime != null && nextAlarmTime > System.currentTimeMillis()) {
        rearm { AlarmScheduler.scheduleAlarm(context, nextAlarmTime) }
    } else {
        // The stored trigger is gone or already past: the ring was lost to a reboot, not
        // cancelled, so the next occurrence is recomputed from the saved time.
        AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
            rearm { AlarmScheduler.scheduleDailyAlarm(context, hour, minute) }
        }
    }
}
```

**Finding 1 is here.** The `else` branch is reached when the stored trigger is in the past,
which is exactly the locked-boot-then-late-unlock case. It does not ask whether that
occurrence ever rang; it rolls straight to tomorrow with request code `1001`, which
overwrites the pending alarm. `armWithoutPersisting` never wrote `morro_prefs`, so
`getNextAlarmTime` still returns the 07:00 instant — that is the evidence the branch ignores.

`MissedAlarmLog.recordFired` is the only thing that records "it rang", and it is written by
`AlarmReceiver`, which is not direct-boot aware. So at the moment `rearmAfterUnlock` runs,
the honest answer to "did it ring?" is `MissedAlarmLog`'s `alarm_last_fired_at`.

**What is *not* confirmed** is the user-visible outcome. Whether the deferred ring still
arrives after the overwrite depends on how the platform treats an alarm broadcast aimed at a
non-direct-boot-aware receiver while the user is locked. If it is queued and delivered at
unlock, the person gets one late ring; if it is dropped, they get nothing. I cannot settle
that without a device, and I will not claim either. The invariant fix is safe under both
outcomes because it is idempotent by construction (§7).

---

## 4 · `AlarmReceiver.kt` — logging vs re-arming

There is **no re-arm in this file at all**. The brief's mental model ("skip the daily
re-arm branch") does not match this codebase: re-arming happens in
`AlarmActivity.finishAlarm()` on dismissal, never in the broadcast.

Order inside `onReceive`:

1. `isWakeUpRering = intent.getBooleanExtra(WakeUpCheck.EXTRA_RERING, false)`
2. health log, before anything can move the bookkeeping:
   ```kotlin
   if (intent.action != ACTION_TEST_ALARM && !isWakeUpRering) {
       val expected = AlarmScheduler.getNextAlarmTime(context)
       val now = System.currentTimeMillis()
       MissedAlarmLog.recordFired(context, now)
       if (expected != null && lateFireFor(expected, now) != null) {
           MissedAlarmLog.recordLateFire(context, expected, now)
       }
   }
   ```
3. wake lock 60 s, start `AlarmForegroundService`
4. `startActivity(AlarmActivity)` as a BAL safety net, carrying `EXTRA_RERING` when present
5. fallback notification, then release the wake lock in `finally`

So the logging reads the expected trigger *before* `recordFired` overwrites it — correct —
and nothing here re-arms. That matters for the Finding 1 design: the guard has to be about
"don't ring twice", not about "don't re-arm twice".

---

## 5 · `MissedAlarmLog.kt`

`evaluateMissedRing` control flow (`:156-181`), verbatim:

```kotlin
val armed = AlarmScheduler.isArmed(context)
val expected = AlarmScheduler.getNextAlarmTime(context)
val firedAt = prefs(context).getLong(KEY_LAST_FIRED, 0L)

if (!isNeverFired(armed, expected, nowMillis, firedAt)) return latestNeverFired(context, expected)

val alreadyLogged = events(context).any {
    it.kind == AlarmEventKind.NEVER_FIRED && it.expectedAtMillis == expected
}
if (alreadyLogged) return latestNeverFired(context, expected)

val event = AlarmEvent(kind = NEVER_FIRED, expectedAtMillis = expected ?: 0L, ...)
record(context, event)
return event
```

Both selection paths are `latestNeverFired`, which filters `kind == NEVER_FIRED`
(`:206-209`). **A `LATE_FIRE` event can never be returned to the dashboard.** Finding 3's
stale-card scenario is real, and the deeper defect is that the late-ring story is
unreachable by construction.

`record()` (`:211-237`) loops `0 until EVENT_CAPACITY` and, per slot, writes or removes five
keys — 50 preference operations per event, plus `alarm_log_count`, into `morro_prefs`, the
same file that holds mission, alarm time and wake-check state. `recordLateFire` is called
from `AlarmReceiver` on the ring path. Confirmed.

`clear()` has the same 50-operation shape (`:258-271`).

---

## 6 · `WakeUpCheck.kt`

`restoreAfterBoot` (`:208-230`), verbatim:

```kotlin
val prefs = prefs(context)
if (!prefs.getBoolean(KEY_PENDING, false)) {
    if (prefs.getLong(KEY_RINGING, 0L) != 0L) {
        prefs.edit().putLong(KEY_RINGING, 0L).apply()
    }
    return
}

val alertAt = prefs.getLong(KEY_ALERT_AT, 0L)
if (alertAt <= System.currentTimeMillis()) {
    Log.w(TAG, "Wake-up check window expired during the reboot")
    resolve(context)
    return
}

if (schedule(context, REQUEST_ALERT, ACTION_ALERT, alertAt)) {
    showQuestion(context)
} else {
    resolve(context)
}
```

Keys present: `wake_check_enabled`, `wake_check_pending`, `wake_check_alert_at`,
`wake_check_armed_at`, `wake_check_ringing`. **No key records that the question was
shown.** `showQuestion()` is called unconditionally on the restore path, so a reboot in the
first three minutes posts the question at boot and leaves the person the remainder of the
original 90 seconds — possibly less than a second. The KDoc at `:203-207` claims otherwise
("the 90 seconds are still counted from when it was first asked"), which is only true when
the question *was* asked. Confirmed.

Note: `showQuestion()` is also called by `WakeUpCheckReceiver` on `ACTION_CHECK`, so the
shown-timestamp must be written in `showQuestion()` itself, not in the restore branch.

---

## 7 · Tests — what exists

20 files, 165 `@Test` methods. Relevant to this pass:

| File | Tests | Covers |
|---|---|---|
| `MissedAlarmLogTest.kt` | 10 | `lateFireFor`, `isNeverFired`, `appendEvent` |
| `WakeUpCheckTest.kt` | 5 | `morningIsFree` |
| `AlarmSchedulerTest.kt` | 4 | `nextOccurrenceMillis` |
| `GateSilenceControllerTest.kt` | 6 | new gate silence |
| `MathEngineTest`, `MathDifficultyTest`, `MemoStorageTest`, `OemGuidanceTest`, `PhotoTargetPoolTest`, `IntroSequenceTest`, `OnboardingQuestionsTest`, `PaywallPlansTest`, `MorroPaletteTest`, `MotionMathTest`, `ChessEngineTest`, `PuzzleRepositoryTest`, `SudokuTest`, `ChessBoardUiTest`, `CognitiveChallengeTest`, `SudokuUiTest` | 140 | unrelated to this pass |

Missing entirely: `catchUpNeeded`, `preferredEventFor`, `restorePlan`, any mirror-write
guard, any ring-buffer head-pointer test.

`junit:junit:4.13.2` is the only test dependency (`app/build.gradle.kts:73`). **No
Robolectric, no Mockito, no androidx.test.** Any new test must be pure JVM.

---

## 8 · Manifest — receivers

```xml
<receiver
    android:name=".AlarmReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.morro.alarm.ACTION_TRIGGER_ALARM" />
        <action android:name="com.morro.alarm.ACTION_TEST_ALARM" />
    </intent-filter>
</receiver>

<receiver
    android:name=".BootReceiver"
    android:directBootAware="true"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>

<receiver
    android:name=".WakeUpCheckReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.morro.alarm.ACTION_WAKE_CHECK" />
        <action android:name="com.morro.alarm.ACTION_WAKE_CHECK_ALERT" />
        <action android:name="com.morro.alarm.ACTION_I_AM_UP" />
    </intent-filter>
</receiver>
```

All three are `exported="false"`. `BootReceiver` is the only direct-boot-aware component;
`AlarmReceiver`, `AlarmForegroundService` and `AlarmActivity` are not, which is the
documented limit and must stay that way.

`<activity-alias android:name=".AlarmRingingActivity" android:targetActivity=".AlarmActivity" android:exported="false" />`
also exists and points at the ring activity.

No `android:permission` and no `android:enabled` anywhere in the manifest.

---

## 9 · `BatteryOptimizationDebugActivity` — dead

Three references in the whole repo: the class declaration
(`BatteryOptimizationDebugActivity.kt:30`), its own internal composable call (`:42`), and
the manifest entry (`AndroidManifest.xml:64-67`). **No entry point exists** — no
`startActivity`, no `PendingIntent`, no click handler, no reflection. It lives in
`src/main`, so it compiles into release, and no `BuildConfig.DEBUG` guard touches it.

It is dead code, not debug-only code. The real door is `OemGuidanceActivity`, launched from
`MainActivity.kt:454`, `MainActivity.kt:577` and `OnboardingActivity.kt:138`.

---

## 10 · `setSmallIcon` — three sites, all framework drawables

| File | Line | Icon |
|---|---|---|
| `AlarmForegroundService.kt` | 106 | `android.R.drawable.ic_dialog_alert` |
| `AlarmReceiver.kt` | 157 | `android.R.drawable.ic_dialog_alert` |
| `WakeUpCheck.kt` | 254 | `android.R.drawable.ic_popup_reminder` |

`app/src/main/res/` contains only `mipmap-anydpi-v26/ic_launcher.xml` and
`values/{colors,strings,themes}.xml`. The `mipmap-*dpi` folders exist and are empty. There
is no `@drawable/` or `R.drawable.` reference anywhere in `app/`, so Finding 5 requires
creating `res/drawable/` from scratch.

---

## 11 · Repo composition (6C)

| Ext | Count | Where |
|---|---|---|
| `.kt` | 93 | 67 `app/src/main`, 20 `app/src/test`, 6 inside skill packs |
| `.md` | 664 | 329 `.claude/skills`, 329 `.freebuff/skills` (byte-identical duplicate tree), 2 `research/`, 2 `design/`, 2 root |
| `.py` | 66 | 32 + 32 skill packs, 2 `tools/` |
| `.js` | 7 | 3 + 3 skill packs, 1 `workflows/scripts` |
| `.csv` | 106 | 53 + 53 skill packs |

The Python/JavaScript share the brief mentions is almost entirely two **duplicated skill
packs** (`.claude/skills` and `.freebuff/skills`, same 24 skills, 329 md + 53 csv + 32 py
each). Real app source is 93 `.kt` + 5 `.xml` + 3 `.kts` + 2 `.gz`.

No root `README.md`. No repo description or topics can be read from inside the working tree.
`mcp.json` at root is 0 bytes. `.claude/worktrees/` is empty. `workflows/scripts/` holds one
file, `ultracode-morro-hotfix-wf.js`, unreferenced by any Gradle or Kotlin file.

---

## 12 · Corrections to the brief's assumptions

1. **`AlarmReceiver` has no re-arm branch.** Finding 1's fix must protect against a second
   *ring*, not against a second *re-arm*.
2. **`armWithoutPersisting` already does not mirror.** Finding 2 is a missing guard, not a
   live data-loss bug. It cannot be unit-tested with Robolectric (not a dependency), so the
   guard will be a source-level contract test plus KDoc.
3. **`LATE_FIRE` is never displayed.** Finding 3's fix must also change the selection, not
   just the preference order, or the "rang late" copy stays unreachable.
4. **No `res/drawable/` exists**, so Finding 5 adds a directory as well as a file.
5. **The repo contains a large puzzle subsystem** that the brief does not mention. It is
   untouched by this pass, and nothing here may break it.
6. **The competitor research documents in §1 are not in this repo.** Nothing was taken from
   them; the forbidden-API list was still respected.

---

## 13 · Planned fix per finding (Phase 2 onward)

**Finding 1 — invariant: a due, unfired alarm is never silently rolled forward.**

Pure decision function:

```kotlin
internal fun catchUpNeeded(
    armed: Boolean,
    expectedAtMillis: Long?,
    nowMillis: Long,
    lastFiredAtMillis: Long,
): Boolean
```

In `rearmAfterUnlock`, when the stored trigger is past *and* nothing rang for it:
record `NEVER_FIRED` for that occurrence, then arm a catch-up ring on a **distinct request
code (1006)** carrying `EXTRA_OCCURRENCE`, and **return without rolling to tomorrow** —
so the missed occurrence stays the stored trigger until the person actually dismisses it.
If the catch-up cannot be armed, fall through to the existing roll-forward so the person is
not left with nothing.

Idempotency, both orders:
- `armCatchUp` re-reads `alarm_last_fired_at` immediately before arming; if the deferred
  broadcast already rang, it is a no-op.
- `AlarmReceiver` gains a pure `isDuplicateRing(expected, now, lastFired)` guard that drops
  a second delivery for an occurrence that already rang, except for the wake-up re-ring and
  the debug test ring. The guard reads `alarm_last_fired_at` *before* `recordFired` writes.

The ring path stays non-direct-boot-aware. `BootReceiver`'s KDoc limit is preserved.

**Finding 2** — KDoc on `mirrorFrom` (never before first unlock, and why) and on
`armWithoutPersisting` (must not persist or mirror), plus a JVM contract test that reads
`AlarmScheduler.kt` and asserts the `armWithoutPersisting` body contains no
`mirrorFrom` / `savePrefs` / `clearPrefs` call.

**Finding 3** — `preferredEventFor(events, expectedAtMillis)` preferring `LATE_FIRE`;
both `latestNeverFired` call sites replaced; `MissedRingCard` copy split by kind
("rang late" vs "didn't ring"), same tone, same action label.

**Finding 4** — `KEY_QUESTION_SHOWN_AT` written with `commit()` inside `showQuestion()`;
`restorePlan(questionShownAt, alertAt, now)` returning
`RESCHEDULE_ORIGINAL` / `REARM_FULL_WINDOW` / `EXPIRED`; restore re-arms the full window
instead of posting the question early.

**Finding 5** — `res/drawable/ic_morro_notification.xml`, 24dp, single white path,
silhouette only; referenced from all three `setSmallIcon` sites.

**Finding 6A** — head-pointer ring buffer: `alarm_log_v2_head`, `alarm_log_v2_count`,
slot `alarm_log_v2_<i>.<field>`; 7 writes per event; old `alarm_log_*` slots removed on
first read; `alarm_log_dismissed_expected` kept (renaming it would resurrect a dismissed
card).

**Finding 6B** — dead; delete the class and the manifest entry, one revert away.

**Finding 6C** — report only. Nothing deleted.

---

## 14 · Open questions (non-blocking)

1. **Finding 1's empirical test needs the phone.** No device is attached here, so the
   "does the deferred ring still arrive" half stays unverified. The fix is idempotent and
   safe under both outcomes, so it proceeds; the device checklist still has to be run.
2. **Deleting `BatteryOptimizationDebugActivity`.** §6B says delete if dead, §6C says ask
   before deleting. It is dead, so it is on the Phase 7 list with an explicit call-out
   rather than a silent removal.
