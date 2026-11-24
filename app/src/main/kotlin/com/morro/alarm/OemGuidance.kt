package com.morro.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * The manufacturer families whose software blocks alarms by default.
 *
 * On these skins an alarm is only allowed to start after a reboot — or after being swiped away — if
 * the app is on an autostart allowlist that the person has to grant themselves. No amount of
 * correct `AlarmManager` code changes that. This is the single most common reason a phone alarm
 * "did not ring", which is why Morro spends a screen on it.
 *
 * The guidance is deliberately plain and factual: what the phone does, and which switch to flip. It
 * is not the person's fault, and the copy never implies that it is.
 */
enum class OemFamily {
    XIAOMI,
    OPPO,
    VIVO,
    HUAWEI,
    SAMSUNG,
    GENERIC,
}

/**
 * One family's instructions.
 *
 * `family` is a value rather than a set of intents so the mapping from `Build.MANUFACTURER` can be
 * unit-tested on the JVM; the fragile OEM component names live in [OemReliability] instead, where
 * they cannot be confused for something testable.
 */
data class OemGuidance(
    val family: OemFamily,
    /** What to call the phone on screen: "Xiaomi", "OnePlus", … */
    val brandLabel: String,
    /** One plain sentence about what this skin does. */
    val headline: String,
    /** The switches to flip, in the order a person should look for them. */
    val steps: List<String>,
)

/**
 * Maps a `Build.MANUFACTURER` (or brand) string onto a family.
 *
 * Aliases matter: a Redmi or a POCO reports "Xiaomi", a Realme reports "realme", an iQOO reports
 * "vivo", and Honor reports "HONOR". Matching on lowercase substrings keeps that from needing a
 * list that goes stale.
 */
internal fun guidanceFor(manufacturer: String, brand: String = manufacturer): OemGuidance {
    val haystack = "$manufacturer $brand".lowercase()

    fun matches(vararg needles: String) = needles.any { haystack.contains(it) }

    return when {
        matches("xiaomi", "redmi", "poco") -> OemGuidance(
            family = OemFamily.XIAOMI,
            brandLabel = "Xiaomi",
            headline = "Xiaomi phones stop background apps from starting after a reboot unless autostart is allowed.",
            steps = listOf(
                "Allow Morro under Autostart.",
                "Set Battery saver for Morro to No restrictions.",
                "Lock Morro in the recents screen so HyperOS keeps it alive."
            )
        )

        matches("oppo", "realme", "oneplus") -> OemGuidance(
            family = OemFamily.OPPO,
            brandLabel = if (matches("oneplus")) "OnePlus" else "OPPO / Realme",
            headline = "ColorOS and OxygenOS hold apps back from starting in the background by default.",
            steps = listOf(
                "Allow Morro in Startup manager.",
                "Allow Morro to run in the background under Battery.",
                "Turn off any sleep or freeze setting applied to Morro."
            )
        )

        matches("vivo", "iqoo") -> OemGuidance(
            family = OemFamily.VIVO,
            brandLabel = "Vivo",
            headline = "Vivo and iQOO limit background startup unless you allow the app yourself.",
            steps = listOf(
                "Allow Morro under Background startup.",
                "Allow high background power usage for Morro.",
                "Exclude Morro from any auto-cleanup."
            )
        )

        matches("huawei", "honor") -> OemGuidance(
            family = OemFamily.HUAWEI,
            brandLabel = if (matches("honor")) "Honor" else "Huawei",
            headline = "EMUI and MagicOS manage app launches manually by default.",
            steps = listOf(
                "Open App launch and set Morro to Manage manually.",
                "Turn on all three toggles: Auto-launch, Secondary launch, Run in background.",
                "Set Morro to allowed in Battery settings."
            )
        )

        matches("samsung") -> OemGuidance(
            family = OemFamily.SAMSUNG,
            brandLabel = "Samsung",
            headline = "Samsung puts unused apps to sleep, which cancels the alarms they scheduled.",
            steps = listOf(
                "Open Settings, then Battery, then Background usage limits.",
                "Remove Morro from Sleeping apps.",
                "Remove Morro from Deep sleeping apps.",
                "Turn off Put unused apps to sleep."
            )
        )

        else -> OemGuidance(
            family = OemFamily.GENERIC,
            brandLabel = "This phone",
            headline = "Android can pause background apps to save power, which can delay an alarm.",
            steps = listOf(
                "Allow Morro to run without battery restrictions.",
                "Turn off any setting that puts unused apps to sleep."
            )
        )
    }
}

/**
 * Opening the right settings screen, defensively.
 *
 * These OEM activities are undocumented and move between OS versions, so every candidate is tried
 * in turn and the last resort is always the app's own info screen. It never throws and never does
 * nothing: a person who taps the button always ends up somewhere they can act.
 */
object OemReliability {

    private const val TAG = "OemReliability"

    /** What this device needs, based on its reported manufacturer and brand. */
    fun current(): OemGuidance = guidanceFor(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())

    /**
     * True for the families where a switch outside the app decides whether alarms survive.
     *
     * Used to decide whether the onboarding card is worth its space: on stock Android the battery
     * optimisation row already says everything there is to say.
     */
    fun needsGuidance(guidance: OemGuidance = current()): Boolean =
        guidance.family != OemFamily.GENERIC

    /** Opens the autostart/startup screen, or the app's info screen if the OEM path is gone. */
    fun openAutostartSettings(context: Context): Boolean {
        for (component in autostartCandidates(current().family)) {
            if (tryStart(context, Intent().setComponent(component))) return true
        }
        return openAppInfo(context)
    }

    /**
     * Opens the system battery screen. Used as a second door for families whose autostart screen we
     * cannot reach, and as the only door on stock Android.
     */
    fun openBatterySettings(context: Context): Boolean {
        if (tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return true
        return openAppInfo(context)
    }

    fun openAppInfo(context: Context): Boolean =
        tryStart(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        // Expected on most devices: the candidate does not exist on this OS version.
        Log.d(TAG, "Settings activity unavailable: ${intent.component ?: intent.action}")
        false
    }

    /**
     * Candidate activities, most specific first. Several skins ship more than one of these across
     * versions, which is why each family gets a list rather than a single name.
     */
    private fun autostartCandidates(family: OemFamily): List<ComponentName> = when (family) {
        OemFamily.XIAOMI -> listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        )

        OemFamily.OPPO -> listOf(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        )

        OemFamily.VIVO -> listOf(
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
        )

        OemFamily.HUAWEI -> listOf(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        )

        // Samsung exposes no documented autostart activity; the battery screen is the real door.
        OemFamily.SAMSUNG, OemFamily.GENERIC -> emptyList()
    }
}
