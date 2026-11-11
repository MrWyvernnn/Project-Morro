package com.morro.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * BatteryDefenseManager: Implements the Slice 3 battery defense stack.
 * Handles Android 12/13/14/15 exact alarm checks, battery optimization exemptions,
 * and OEM-specific auto-start guidance for Nothing OS, Samsung, Xiaomi, Oppo, OnePlus, etc.
 */
object BatteryDefenseManager {

    private const val TAG = "BatteryDefenseManager"

    // --- Diagnostic helpers ---

    /** True if the app is ignoring battery optimizations. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** True if the app can schedule exact alarms (Android 12+). */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            try { am.canScheduleExactAlarms() } catch (e: Exception) { true }
        } else { true }
    }

    /** Intent to request battery optimization exemption. */
    fun getBatteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    /** Fallback intent to battery optimization settings (no direct grant). */
    fun getBatterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Intent to request exact alarm permission (Android 12+). */
    fun getExactAlarmSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else null

    // --- OEM Auto-Start helpers ---

    /**
     * Returns a sequence of intents to open OEM-specific background/auto-start settings.
     * The first available one should be opened. Returns empty list if OEM is not detected.
     */
    fun getOemBackgroundSettingsIntents(context: Context): List<Intent> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = mutableListOf<Intent>()

        // Xiaomi / POCO
        if (manufacturer.contains("xiaomi") || manufacturer.contains("poco")) {
            intents.addAll(listOf(
                Intent().setComponent(ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )),
                Intent().setComponent(ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                ))
            ))
        }

        // Samsung
        if (manufacturer.contains("samsung")) {
            intents.addAll(listOf(
                Intent().setComponent(ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )),
                Intent().setComponent(ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                ))
            ))
        }

        // OPPO / Realme
        if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            intents.add(Intent().setComponent(ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )))
        }

        // OnePlus
        if (manufacturer.contains("oneplus")) {
            intents.add(Intent().setComponent(ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.ChainLaunchAppListActivity"
            )))
        }

        // Huawei / Honor
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            intents.add(Intent().setComponent(ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )))
        }

        // Vivo
        if (manufacturer.contains("vivo")) {
            intents.add(Intent().setComponent(ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )))
        }

        // Nothing OS — generally respects AlarmClock API well, but still check battery
        // General Android battery settings as last resort
        intents.add(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))

        return intents.filter { ctx ->
            ctx.resolveActivity(context.packageManager) != null
        }
    }

    /**
     * Opens the first available OEM background settings.
     * Returns true if an intent was found and launched.
     */
    fun openOemBackgroundSettings(context: Context): Boolean {
        val intents = getOemBackgroundSettingsIntents(context)
        if (intents.isEmpty()) return false
        return try {
            intents.first().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intents.first())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not open OEM settings: ${e.message}")
            false
        }
    }

    /** Returns a human-readable device description. */
    fun getDeviceDescription(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.SDK_INT})"
    }

    /** Returns true if the device is known to aggressively kill background apps. */
    fun isAggressiveOem(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("xiaomi") || m.contains("samsung") ||
               m.contains("oppo") || m.contains("oneplus") ||
               m.contains("huawei") || m.contains("honor") ||
               m.contains("vivo") || m.contains("poco") ||
               m.contains("realme") || m.contains("nothing")
    }
}