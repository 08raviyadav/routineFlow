package com.thor.routineflow.util

import android.os.Build

/**
 * Per-brand battery-whitelisting guides. Every major Android skin kills
 * background services aggressively unless the user whitelists the app, so the
 * diagnostics screen shows the steps for the user's own phone.
 *
 * Steps only name settings paths that are well established for each skin;
 * anything that varies by version is phrased as "look for".
 */
object OemGuide {

    data class OemBrand(
        val id: String,
        val label: String,
        val steps: List<String>,
        /** Package-qualified Settings action, when the brand exposes one. Null = open the generic battery settings. */
        val settingsAction: String?
    )

    private val onePlus = OemBrand(
        id = "oneplus",
        label = "OnePlus / OxygenOS",
        steps = listOf(
            "Settings → Battery → Battery optimization → RoutineFlow → Don't optimize.",
            "Settings → Apps → RoutineFlow → Battery usage → allow background activity (turn off any battery restriction for the app).",
            "Lock RoutineFlow in recents: open the recent-apps view, tap the menu on the RoutineFlow card and choose Lock — this stops the system sweeping it away.",
            "Look for Settings → Battery → More battery settings and turn off any 'optimize when the screen is off' / sleep standby optimisation that covers RoutineFlow."
        ),
        settingsAction = null
    )

    private val xiaomi = OemBrand(
        id = "xiaomi",
        label = "Xiaomi / Redmi / POCO (HyperOS / MIUI)",
        steps = listOf(
            "Settings → Apps → Manage apps → RoutineFlow → Autostart → turn it on.",
            "On the same app screen, open Battery saver and choose No restrictions.",
            "Lock RoutineFlow in recents: open the recent-apps view, long-press the RoutineFlow card and choose Lock.",
            "Look for Settings → Battery → Additional features and make sure RoutineFlow is not listed under any app battery saver that restricts background work."
        ),
        settingsAction = null
    )

    private val samsung = OemBrand(
        id = "samsung",
        label = "Samsung / One UI",
        steps = listOf(
            "Settings → Battery → Background usage limits → Never sleeping apps → add RoutineFlow.",
            "Settings → Apps → RoutineFlow → Battery → choose Unrestricted.",
            "On the same screen, make sure 'Allow background activity' is on.",
            "Look for Device care → Battery and confirm RoutineFlow is not in Sleeping apps or Deep sleeping apps."
        ),
        settingsAction = null
    )

    private val oppo = OemBrand(
        id = "oppo",
        label = "OPPO / Realme (ColorOS)",
        steps = listOf(
            "Settings → Battery → App battery management → RoutineFlow → allow background running and auto-launch.",
            "Look for Phone Manager → Privacy permissions → Startup manager and allow RoutineFlow to auto-start.",
            "Lock RoutineFlow in recents: open the recent-apps view, tap the menu on the RoutineFlow card and choose Lock.",
            "Look for Settings → Battery → Power saving and confirm no power-saving mode is restricting RoutineFlow."
        ),
        settingsAction = null
    )

    private val vivo = OemBrand(
        id = "vivo",
        label = "Vivo / iQOO (Funtouch OS)",
        steps = listOf(
            "Open iManager → App manager → Autostart management → allow RoutineFlow.",
            "Settings → Battery → Background power consumption management → RoutineFlow → allow background running.",
            "Lock RoutineFlow in recents: open the recent-apps view, tap the menu on the RoutineFlow card and choose Lock.",
            "Look for Settings → Battery and turn off any 'smart' background restriction that covers RoutineFlow."
        ),
        settingsAction = null
    )

    private val huawei = OemBrand(
        id = "huawei",
        label = "Huawei / Honor (EMUI / MagicOS)",
        steps = listOf(
            "Settings → Apps → Apps → RoutineFlow → Power usage details → App launch → choose Manage manually and turn on Auto-launch, Secondary launch and Run in background.",
            "Look for Phone Manager (Optimizer) → Battery and make sure RoutineFlow is not restricted.",
            "Lock RoutineFlow in recents: open the recent-apps view, tap the menu on the RoutineFlow card and choose Lock."
        ),
        settingsAction = null
    )

    private val pixel = OemBrand(
        id = "pixel",
        label = "Pixel (stock Android)",
        steps = listOf(
            "Settings → Apps → RoutineFlow → App battery usage → choose Unrestricted.",
            "Settings → Apps → RoutineFlow → Battery → make sure battery optimisation is off for RoutineFlow (Don't optimize).",
            "Look for Settings → Battery → Adaptive preferences and confirm RoutineFlow is not restricted by Adaptive Battery."
        ),
        settingsAction = null
    )

    private val generic = OemBrand(
        id = "generic",
        label = "Other Android phones",
        steps = listOf(
            "Settings → Apps → RoutineFlow → Battery → choose Unrestricted (or 'Don't optimize' under Battery optimization).",
            "Look for any Autostart / Auto-launch permission for RoutineFlow in Settings → Apps and turn it on.",
            "Lock RoutineFlow in recents so the system does not sweep it away: open the recent-apps view and look for a Lock option on the RoutineFlow card.",
            "Look for a Battery / Device care section in Settings and make sure RoutineFlow is not on any sleeping-apps or restricted-apps list."
        ),
        settingsAction = null
    )

    fun allBrands(): List<OemBrand> =
        listOf(onePlus, xiaomi, samsung, oppo, vivo, huawei, pixel, generic)

    fun detectBrand(): OemBrand {
        return when (Build.MANUFACTURER.lowercase()) {
            "oneplus" -> onePlus
            "xiaomi", "redmi", "poco" -> xiaomi
            "samsung" -> samsung
            "oppo", "realme" -> oppo
            "vivo", "iqoo" -> vivo
            "huawei", "honor" -> huawei
            "google" -> pixel
            else -> generic
        }
    }
}
