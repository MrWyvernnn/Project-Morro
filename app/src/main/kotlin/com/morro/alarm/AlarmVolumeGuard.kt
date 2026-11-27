package com.morro.alarm

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Pure threshold check for alarm stream audibility.
 *
 * Android devices often floor `STREAM_ALARM` at 1 instead of 0, so checking `== 0` is dead code
 * on hardware where `1/16` or `2/16` is quiet enough to sleep through. Any ratio below 40% is
 * treated as too quiet.
 */
fun isTooQuiet(volume: Int, max: Int): Boolean {
    if (max <= 0) return false
    return (volume.coerceAtLeast(0).toFloat() / max.toFloat()) < 0.4f
}

/**
 * Pure decision helper for the ring-time volume floor watchdog.
 *
 * Returns the clamped volume level to write via `AudioManager.setStreamVolume(..., 0)` when
 * [current] drops below [floor], or `null` when no write is required.
 *
 * Never lowers a user-initiated raise above [floor], and clamps [floor] to [0, max].
 */
fun floorRestoreTarget(current: Int, floor: Int, max: Int): Int? {
    if (max <= 0) return null
    val clampedFloor = floor.coerceIn(0, max)
    return if (current < clampedFloor) clampedFloor else null
}

internal data class AlarmVolumeSnapshot(
    val volume: Int,
    val max: Int,
    val ratio: Float?,
    val tooQuiet: Boolean,
)

internal object AlarmVolumeGuard {
    /**
     * Reads the current `STREAM_ALARM` volume and max from [AudioManager].
     *
     * Limitation: Android's public SDK `AudioManager.getStreamVolume(STREAM_ALARM)` returns the
     * volume for the currently active output route (e.g. wired/Bluetooth headphones if connected),
     * not specifically the built-in speaker that alarms use at ring time. Public Android APIs do
     * not expose per-device stream volumes for inactive routes, so we also re-read and record the
     * volume ratio at ring time in [AlarmReceiver] and re-sample on route changes during a ring.
     */
    fun readAlarmVolume(context: Context): AlarmVolumeSnapshot {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AlarmVolumeSnapshot(volume = 0, max = 0, ratio = null, tooQuiet = false)
        val volume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val ratio = if (max > 0) (volume.coerceAtLeast(0).toFloat() / max.toFloat()).coerceIn(0f, 1f) else null
        return AlarmVolumeSnapshot(
            volume = volume,
            max = max,
            ratio = ratio,
            tooQuiet = isTooQuiet(volume, max),
        )
    }

    /**
     * Returns a stable signature of connected output sinks so the service watchdog can detect a
     * route switch (e.g. speaker -> wired/BT headset) and avoid forcing a speaker floor onto
     * headphones.
     */
    fun outputRouteSignature(am: AudioManager): String =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { "${it.type}:${it.id}" }
            .sorted()
            .joinToString(",")

    /** Non-built-in headphone/BT sink types where applying speaker floor would be unsafe. */
    fun hasExternalHeadsetSink(am: AudioManager): Boolean =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { dev ->
            dev.isSink && when (dev.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> true
                else -> false
            }
        }

    fun isAlarmTooQuiet(context: Context): Boolean = readAlarmVolume(context).tooQuiet
}
