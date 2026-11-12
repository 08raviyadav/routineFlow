package com.thor.routineflow.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * RoutineFlow domain model.
 *
 * The rule model is versioned JSON ([BackupFile.FORMAT_VERSION]) so routines can be
 * exported, imported, shared and (later) synced without a database migration.
 * v1.5/v2.0 features (AI builder, plugins, Shizuku pack, sync) plug into this model
 * through new [TriggerType]/[ActionType] entries and new [Capability] tiers — the
 * engine, storage and UI never need to know about them up front.
 */

/** What an action needs in order to run on a real phone. Shown as a badge in the UI. */
enum class Capability(val label: String) {
    /** Works in a normal Play Store install (may need a Settings toggle the user grants). */
    PLAY_SAFE("Play-safe"),

    /** Needs Shizuku or an ADB grant. v1 lists these badged and disabled — never faked. */
    SHIZUKU("Needs Shizuku"),

    /** Needs root. v1 lists these badged and disabled — never faked. */
    ROOT("Needs root")
}

/** Kinds of parameter the routine editor can render for a trigger/action/condition. */
enum class ParamKind {
    TEXT, LONG_TEXT, NUMBER, TIME, DAYS, CHOICE, BOOLEAN, APP, COLOR, SLIDER, LOCATION, ROUTINE
}

@Serializable
data class ParamSpec(
    val key: String,
    val label: String,
    val kind: String, // ParamKind name — stored as String so the JSON stays stable.
    val defaultValue: String = "",
    val options: List<String> = emptyList(), // CHOICE: "value|Label" pairs
    val hint: String = ""
)

/** One configured trigger inside a routine. Params are plain strings by key. */
@Serializable
data class TriggerConfig(
    val type: String,
    val params: Map<String, String> = emptyMap()
)

/** One configured action inside a routine. */
@Serializable
data class ActionConfig(
    val type: String,
    val params: Map<String, String> = emptyMap()
)

/** One configured condition (used by routine-level conditions and IF steps). */
@Serializable
data class ConditionConfig(
    val type: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val icon: String = "bolt",
    /** ARGB color as Long. */
    val color: Long = 0xFF6750A4,
    val triggers: List<TriggerConfig> = emptyList(),
    /** All triggers are ANDed; all conditions must also hold at run time. */
    val conditions: List<ConditionConfig> = emptyList(),
    val actions: List<ActionConfig> = emptyList(),
    val isTemplate: Boolean = false,
    val runCount: Int = 0,
    val lastRunAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** The setting set a Mode applies. Every field is nullable: null = leave unchanged. */
@Serializable
data class ModeSettings(
    val dnd: Boolean? = null,
    /** "normal" | "vibrate" | "silent" */
    val ringerMode: String? = null,
    /** 0..255, null = unchanged. */
    val brightness: Int? = null,
    val autoBrightness: Boolean? = null,
    /** 0..100 */
    val mediaVolume: Int? = null,
    /** 0..100 */
    val ringVolume: Int? = null,
    val screenTimeoutMs: Long? = null,
    val autoRotate: Boolean? = null
)

@Serializable
data class ModeProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "moon",
    val color: Long = 0xFF6750A4,
    val settings: ModeSettings = ModeSettings(),
    /** When every auto-trigger matches, the mode activates; when any stops matching it exits. */
    val autoTriggers: List<TriggerConfig> = emptyList(),
    val isActive: Boolean = false,
    val isBuiltIn: Boolean = false,
    /** JSON snapshot of the previous values, used to revert on exit. */
    val revertSnapshotJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RunStatus { SUCCESS, PARTIAL, FAILED, SKIPPED }

@Serializable
data class RunLog(
    val id: Long = 0,
    val routineId: String,
    val routineName: String,
    val startedAt: Long,
    val finishedAt: Long,
    val status: String, // RunStatus name
    val detail: String = ""
)

/** Versioned backup/share file. `version` must be checked on import. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val routines: List<Routine> = emptyList(),
    val modes: List<ModeProfile> = emptyList()
) {
    companion object {
        const val FORMAT = "routineflow-backup"
        const val FORMAT_VERSION = 1
    }
}

// ---------------------------------------------------------------------------
// Trigger catalogue — 23 triggers (v1.0). `isEvent` triggers fire on a discrete
// event (matched against recent events); state triggers match the live snapshot.
// ---------------------------------------------------------------------------

enum class TriggerType(
    val key: String,
    val title: String,
    val description: String,
    val iconKey: String,
    val isEvent: Boolean,
    val params: List<ParamSpec> = emptyList()
) {
    TIME_OF_DAY(
        "time_of_day", "Time of day", "At a set time, on chosen days.",
        "schedule", true,
        listOf(
            ParamSpec("time", "Time", "TIME", "08:00"),
            ParamSpec("days", "Days (blank = every day)", "DAYS", "")
        )
    ),
    ALARM(
        "alarm", "Alarm (RoutineFlow)", "When this RoutineFlow alarm goes off. Set through the app — Android does not broadcast other apps' alarms.",
        "alarm", true,
        listOf(
            ParamSpec("time", "Alarm time", "TIME", "07:00"),
            ParamSpec("days", "Days (blank = every day)", "DAYS", ""),
            ParamSpec("label", "Alarm label", "TEXT", "RoutineFlow alarm")
        )
    ),
    SUNRISE(
        "sunrise", "Sunrise (approx.)", "At local sunrise, calculated from your location. Needs a saved location.",
        "sunrise", true,
        listOf(
            ParamSpec("location", "Location (lat, lng)", "LOCATION", ""),
            ParamSpec("offsetMinutes", "Offset in minutes", "NUMBER", "0")
        )
    ),
    SUNSET(
        "sunset", "Sunset (approx.)", "At local sunset, calculated from your location. Needs a saved location.",
        "sunset", true,
        listOf(
            ParamSpec("location", "Location (lat, lng)", "LOCATION", ""),
            ParamSpec("offsetMinutes", "Offset in minutes", "NUMBER", "0")
        )
    ),
    BATTERY_LEVEL(
        "battery_level", "Battery level", "When the battery crosses a level.",
        "battery", false,
        listOf(
            ParamSpec("direction", "Direction", "CHOICE", "below", listOf("below|Falls below", "above|Rises above")),
            ParamSpec("percent", "Percent", "SLIDER", "20")
        )
    ),
    CHARGING(
        "charging", "Charging state", "When the charger is plugged in or unplugged.",
        "charging", false,
        listOf(ParamSpec("state", "State", "CHOICE", "charging", listOf("charging|Charging", "discharging|Not charging")))
    ),
    WIFI_CONNECTED(
        "wifi_connected", "Wi-Fi connected", "When connected to Wi-Fi (optionally a specific network).",
        "wifi", false,
        listOf(ParamSpec("ssid", "Network name (blank = any)", "TEXT", ""))
    ),
    WIFI_DISCONNECTED(
        "wifi_disconnected", "Wi-Fi disconnected", "When Wi-Fi disconnects.",
        "wifi_off", false
    ),
    BLUETOOTH_CONNECTED(
        "bluetooth_connected", "Bluetooth connected", "When a Bluetooth device connects (optionally a specific one).",
        "bluetooth", false,
        listOf(ParamSpec("deviceName", "Device name (blank = any)", "TEXT", ""))
    ),
    BLUETOOTH_DISCONNECTED(
        "bluetooth_disconnected", "Bluetooth disconnected", "When a Bluetooth device disconnects.",
        "bluetooth_off", false
    ),
    LOCATION_ARRIVE(
        "location_arrive", "Arrive at a place", "When you enter a saved place (geofence).",
        "arrive", true,
        listOf(
            ParamSpec("name", "Place name", "TEXT", "Home"),
            ParamSpec("location", "Location (lat, lng)", "LOCATION", ""),
            ParamSpec("radiusMeters", "Radius in metres", "NUMBER", "200")
        )
    ),
    LOCATION_LEAVE(
        "location_leave", "Leave a place", "When you leave a saved place (geofence).",
        "leave", true,
        listOf(
            ParamSpec("name", "Place name", "TEXT", "Home"),
            ParamSpec("location", "Location (lat, lng)", "LOCATION", ""),
            ParamSpec("radiusMeters", "Radius in metres", "NUMBER", "200")
        )
    ),
    APP_OPENED(
        "app_opened", "App opened", "When an app comes to the foreground. Needs Usage access.",
        "app_open", true,
        listOf(ParamSpec("packageName", "App", "APP", ""))
    ),
    APP_CLOSED(
        "app_closed", "App closed", "When an app leaves the foreground. Needs Usage access.",
        "app_close", true,
        listOf(ParamSpec("packageName", "App", "APP", ""))
    ),
    SCREEN_ON(
        "screen_on", "Screen on", "When the screen turns on.", "screen_on", true
    ),
    SCREEN_OFF(
        "screen_off", "Screen off", "When the screen turns off.", "screen_off", true
    ),
    HEADPHONES(
        "headphones", "Headphones", "When headphones are plugged in or unplugged.",
        "headphones", false,
        listOf(ParamSpec("state", "State", "CHOICE", "connected", listOf("connected|Plugged in", "disconnected|Unplugged")))
    ),
    NOTIFICATION(
        "notification", "Notification received", "When an app posts a notification. Needs Notification access.",
        "notification", true,
        listOf(
            ParamSpec("packageName", "App (blank = any)", "APP", ""),
            ParamSpec("textContains", "Text contains (blank = any)", "TEXT", "")
        )
    ),
    AIRPLANE_MODE(
        "airplane_mode", "Airplane mode", "When airplane mode turns on or off.",
        "airplane", false,
        listOf(ParamSpec("state", "State", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    DND_CHANGED(
        "dnd_changed", "Do Not Disturb", "When Do Not Disturb turns on or off.",
        "dnd", false,
        listOf(ParamSpec("state", "State", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    UNLOCKED(
        "unlocked", "Device unlocked", "When the device is unlocked.", "unlock", true
    ),
    CALL_STATE(
        "call_state", "Call state", "When a call starts ringing or ends.",
        "call", true,
        listOf(ParamSpec("state", "State", "CHOICE", "ringing", listOf("ringing|Incoming call ringing", "ended|Call ended")))
    ),
    CLIPBOARD_CHANGED(
        "clipboard_changed", "Clipboard changed", "When you copy something new.",
        "clipboard", true,
        listOf(ParamSpec("textContains", "Text contains (blank = any)", "TEXT", ""))
    );

    companion object {
        fun fromKey(key: String): TriggerType? = entries.firstOrNull { it.key == key }
    }
}

// ---------------------------------------------------------------------------
// Action catalogue — 40 Play-safe actions + 6 badged (Shizuku/root) actions.
// Badged actions are listed so users can see the roadmap, but the executor
// refuses them with an honest explanation instead of pretending to run them.
// ---------------------------------------------------------------------------

enum class ActionCategory(val label: String) {
    SETTINGS("Settings"), APPS("Apps"), NOTIFY("Notify & speak"), NETWORK("Network"),
    FLOW("Flow control"), PRIVILEGED("Needs Shizuku / root")
}

enum class ActionType(
    val key: String,
    val title: String,
    val description: String,
    val iconKey: String,
    val category: ActionCategory,
    val capability: Capability = Capability.PLAY_SAFE,
    val params: List<ParamSpec> = emptyList(),
    /** Plain-language note shown when the action needs a Settings grant. */
    val requiresNote: String? = null
) {
    // -- Settings (Play-safe) --
    WIFI_PANEL(
        "wifi_panel", "Open Wi-Fi settings", "Opens the Wi-Fi settings panel so you can toggle it. Android 10+ does not let apps toggle Wi-Fi directly.",
        "wifi", ActionCategory.SETTINGS
    ),
    BLUETOOTH_TOGGLE(
        "bluetooth_toggle", "Bluetooth on/off", "Turns Bluetooth on or off. On Android 13+ the system may ask you to confirm once.",
        "bluetooth", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("enabled", "Turn Bluetooth", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    DND_SET(
        "dnd_set", "Do Not Disturb on/off", "Turns Do Not Disturb on or off.",
        "dnd", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("enabled", "Do Not Disturb", "CHOICE", "on", listOf("on|On", "off|Off"))),
        requiresNote = "Needs Do Not Disturb access (a Settings toggle)."
    ),
    RINGER_MODE(
        "ringer_mode", "Ringer mode", "Sets the ringer to normal, vibrate or silent.",
        "volume", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("mode", "Mode", "CHOICE", "normal", listOf("normal|Normal", "vibrate|Vibrate", "silent|Silent")))
    ),
    BRIGHTNESS(
        "brightness", "Set brightness", "Sets screen brightness (0–100%).",
        "brightness", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("percent", "Brightness %", "SLIDER", "50")),
        requiresNote = "Needs 'Modify system settings' (a Settings toggle)."
    ),
    AUTO_BRIGHTNESS(
        "auto_brightness", "Auto-brightness on/off", "Turns adaptive brightness on or off.",
        "brightness_auto", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("enabled", "Auto-brightness", "CHOICE", "on", listOf("on|On", "off|Off"))),
        requiresNote = "Needs 'Modify system settings' (a Settings toggle)."
    ),
    SCREEN_TIMEOUT(
        "screen_timeout", "Screen timeout", "Sets how long until the screen turns off.",
        "timer", ActionCategory.SETTINGS,
        params = listOf(
            ParamSpec(
                "timeoutMs", "Timeout", "CHOICE", "30000",
                listOf("15000|15 seconds", "30000|30 seconds", "60000|1 minute", "120000|2 minutes", "300000|5 minutes", "600000|10 minutes")
            )
        ),
        requiresNote = "Needs 'Modify system settings' (a Settings toggle)."
    ),
    WALLPAPER_COLOR(
        "wallpaper_color", "Set solid wallpaper", "Sets a solid-colour wallpaper.",
        "wallpaper", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("color", "Colour", "COLOR", "#6750A4"))
    ),
    VOLUME_RING(
        "volume_ring", "Ring volume", "Sets the ringtone volume (0–100%).",
        "volume", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("percent", "Volume %", "SLIDER", "70"))
    ),
    VOLUME_MEDIA(
        "volume_media", "Media volume", "Sets the media volume (0–100%).",
        "volume", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("percent", "Volume %", "SLIDER", "70"))
    ),
    VOLUME_ALARM(
        "volume_alarm", "Alarm volume", "Sets the alarm volume (0–100%).",
        "volume", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("percent", "Volume %", "SLIDER", "80"))
    ),
    VOLUME_NOTIFICATION(
        "volume_notification", "Notification volume", "Sets the notification volume (0–100%).",
        "volume", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("percent", "Volume %", "SLIDER", "70"))
    ),
    AUTO_ROTATE(
        "auto_rotate", "Auto-rotate on/off", "Turns screen auto-rotation on or off.",
        "rotate", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("enabled", "Auto-rotate", "CHOICE", "on", listOf("on|On", "off|Off"))),
        requiresNote = "Needs 'Modify system settings' (a Settings toggle)."
    ),
    FLASHLIGHT(
        "flashlight", "Flashlight on/off", "Turns the torch on or off.",
        "flashlight", ActionCategory.SETTINGS,
        params = listOf(ParamSpec("enabled", "Flashlight", "CHOICE", "on", listOf("on|On", "off|Off"))),
        requiresNote = "Needs the Camera permission (the torch is part of the camera)."
    ),
    LOCK_SCREEN(
        "lock_screen", "Lock screen", "Locks the device immediately.",
        "lock", ActionCategory.SETTINGS,
        requiresNote = "Needs RoutineFlow set as a device admin (you approve this in Settings)."
    ),
    MEDIA_PLAY_PAUSE(
        "media_play_pause", "Play / pause media", "Toggles play/pause on the active media session.",
        "media", ActionCategory.SETTINGS,
        requiresNote = "Needs Notification access to see media sessions."
    ),
    CLEAR_NOTIFICATIONS(
        "clear_notifications", "Clear notifications", "Dismisses all notifications from the shade.",
        "clear", ActionCategory.SETTINGS,
        requiresNote = "Needs Notification access."
    ),
    OPEN_BATTERY_SAVER_SETTINGS(
        "open_battery_saver_settings", "Open battery saver settings",
        "Opens the battery saver settings screen.", "battery", ActionCategory.SETTINGS
    ),
    OPEN_LOCATION_SETTINGS(
        "open_location_settings", "Open location settings",
        "Opens the location settings screen.", "location", ActionCategory.SETTINGS
    ),

    // -- Apps --
    LAUNCH_APP(
        "launch_app", "Launch app", "Opens an app.",
        "app_open", ActionCategory.APPS,
        params = listOf(ParamSpec("packageName", "App", "APP", ""))
    ),
    OPEN_URL(
        "open_url", "Open link", "Opens a web link in the browser.",
        "link", ActionCategory.APPS,
        params = listOf(ParamSpec("url", "URL", "TEXT", "https://"))
    ),
    SET_ALARM_INTENT(
        "set_alarm_intent", "Set an alarm", "Opens the clock app with an alarm ready to confirm.",
        "alarm", ActionCategory.APPS,
        params = listOf(
            ParamSpec("time", "Time", "TIME", "07:00"),
            ParamSpec("label", "Label", "TEXT", "")
        )
    ),
    SET_TIMER_INTENT(
        "set_timer_intent", "Start a timer", "Opens the clock app with a timer ready to start.",
        "timer", ActionCategory.APPS,
        params = listOf(ParamSpec("seconds", "Seconds", "NUMBER", "300"))
    ),
    OPEN_NFC_SETTINGS(
        "open_nfc_settings", "Open NFC settings", "Opens the NFC settings screen.",
        "nfc", ActionCategory.APPS
    ),
    OPEN_MOBILE_DATA_SETTINGS(
        "open_mobile_data_settings", "Open mobile data settings",
        "Opens the mobile network settings screen. A direct toggle needs Shizuku — see the badged action below.",
        "data", ActionCategory.APPS
    ),

    // -- Notify & speak --
    SHOW_NOTIFICATION(
        "show_notification", "Show notification", "Posts a RoutineFlow notification.",
        "notification", ActionCategory.NOTIFY,
        params = listOf(
            ParamSpec("title", "Title", "TEXT", "RoutineFlow"),
            ParamSpec("text", "Message", "LONG_TEXT", "")
        )
    ),
    SPEAK_TEXT(
        "speak_text", "Speak text", "Reads text aloud (text-to-speech). Supports \${battery} and \${time}.",
        "speak", ActionCategory.NOTIFY,
        params = listOf(ParamSpec("text", "Text to speak", "LONG_TEXT", ""))
    ),
    SPEAK_TIME(
        "speak_time", "Speak the time", "Reads the current time aloud.",
        "speak", ActionCategory.NOTIFY
    ),
    SPEAK_BATTERY(
        "speak_battery", "Speak battery level", "Reads the battery level aloud.",
        "speak", ActionCategory.NOTIFY
    ),
    VIBRATE(
        "vibrate", "Vibrate", "Vibrates for a short burst.",
        "vibrate", ActionCategory.NOTIFY,
        params = listOf(ParamSpec("durationMs", "Duration (ms)", "NUMBER", "200"))
    ),
    SHOW_TOAST(
        "show_toast", "Show a message", "Shows a short on-screen message.",
        "toast", ActionCategory.NOTIFY,
        params = listOf(ParamSpec("text", "Message", "TEXT", ""))
    ),
    PLAY_SOUND(
        "play_sound", "Play a sound", "Plays the default notification sound.",
        "sound", ActionCategory.NOTIFY
    ),
    COPY_CLIPBOARD(
        "copy_clipboard", "Copy to clipboard", "Copies text to the clipboard.",
        "clipboard", ActionCategory.NOTIFY,
        params = listOf(ParamSpec("text", "Text", "LONG_TEXT", ""))
    ),

    // -- Network --
    HTTP_GET(
        "http_get", "Web request (GET)", "Sends an HTTP GET to your own URL — e.g. a Home Assistant webhook or smart plug. No data goes anywhere else.",
        "web", ActionCategory.NETWORK,
        params = listOf(ParamSpec("url", "URL", "TEXT", "https://"))
    ),

    // -- Flow control --
    RUN_ROUTINE(
        "run_routine", "Run another routine", "Runs a different routine right away.",
        "run", ActionCategory.FLOW,
        params = listOf(ParamSpec("routineId", "Routine", "ROUTINE", ""))
    ),
    SET_ROUTINE_ENABLED(
        "set_routine_enabled", "Enable / disable a routine", "Turns another routine on or off.",
        "toggle", ActionCategory.FLOW,
        params = listOf(
            ParamSpec("routineId", "Routine", "ROUTINE", ""),
            ParamSpec("enabled", "Set to", "CHOICE", "on", listOf("on|Enabled", "off|Disabled"))
        )
    ),
    DELAY(
        "delay", "Wait", "Pauses the routine before the next action.",
        "wait", ActionCategory.FLOW,
        params = listOf(ParamSpec("seconds", "Seconds (max 300)", "NUMBER", "5"))
    ),
    IF(
        "if", "If…", "Runs the following actions only when the condition holds. Pair with Else / End if.",
        "branch", ActionCategory.FLOW,
        params = listOf(
            ParamSpec("conditionType", "Condition", "CHOICE", "battery_below",
                ConditionType.entries.map { "${it.key}|${it.title}" }),
            ParamSpec("c_direction", "Direction (battery)", "CHOICE", "below", listOf("below|Below", "above|Above")),
            ParamSpec("c_percent", "Percent (battery)", "SLIDER", "20"),
            ParamSpec("c_ssid", "Network name (Wi-Fi)", "TEXT", ""),
            ParamSpec("c_startTime", "From (time)", "TIME", "22:00"),
            ParamSpec("c_endTime", "Until (time)", "TIME", "07:00"),
            ParamSpec("c_days", "Days", "DAYS", ""),
            ParamSpec("c_packageName", "App", "APP", "")
        )
    ),
    ELSE(
        "else", "Else", "Runs the following actions when the If above did not hold.",
        "branch", ActionCategory.FLOW
    ),
    END_IF(
        "end_if", "End if", "Ends an If block.", "branch", ActionCategory.FLOW
    ),

    // -- Privileged: listed, badged, and disabled in v1 (never faked) --
    MOBILE_DATA_TOGGLE(
        "mobile_data_toggle", "Toggle mobile data (direct)", "Direct mobile-data toggle. Android does not allow this from a normal app.",
        "data", ActionCategory.PRIVILEGED, Capability.SHIZUKU,
        params = listOf(ParamSpec("enabled", "Mobile data", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    AIRPLANE_TOGGLE(
        "airplane_toggle", "Toggle airplane mode (direct)", "Direct airplane-mode toggle. Android does not allow this from a normal app.",
        "airplane", ActionCategory.PRIVILEGED, Capability.SHIZUKU,
        params = listOf(ParamSpec("enabled", "Airplane mode", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    WIFI_TOGGLE_DIRECT(
        "wifi_toggle_direct", "Toggle Wi-Fi (direct)", "Direct Wi-Fi toggle without opening settings. Android 10+ blocks this for normal apps.",
        "wifi", ActionCategory.PRIVILEGED, Capability.SHIZUKU,
        params = listOf(ParamSpec("enabled", "Wi-Fi", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    DARK_MODE(
        "dark_mode", "Dark mode on/off (direct)", "Switches the system dark theme directly. Needs a secure-settings write.",
        "dark", ActionCategory.PRIVILEGED, Capability.SHIZUKU,
        params = listOf(ParamSpec("enabled", "Dark mode", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    GPS_TOGGLE(
        "gps_toggle", "Toggle GPS (direct)", "Direct GPS toggle. Blocked even with a secure-settings grant on Android 10+ — root only.",
        "location", ActionCategory.PRIVILEGED, Capability.ROOT,
        params = listOf(ParamSpec("enabled", "GPS", "CHOICE", "on", listOf("on|On", "off|Off")))
    ),
    FORCE_STOP_APP(
        "force_stop_app", "Force-stop an app", "Force-stops another app. Only possible with root.",
        "stop", ActionCategory.PRIVILEGED, Capability.ROOT,
        params = listOf(ParamSpec("packageName", "App", "APP", ""))
    );

    companion object {
        fun fromKey(key: String): ActionType? = entries.firstOrNull { it.key == key }

        /** Play-safe actions only, in catalogue order. */
        val playSafe: List<ActionType> get() = entries.filter { it.capability == Capability.PLAY_SAFE }

        /** Badged actions (Shizuku/root) shown disabled with an explanation. */
        val privileged: List<ActionType> get() = entries.filter { it.capability != Capability.PLAY_SAFE }
    }
}

// ---------------------------------------------------------------------------
// Conditions — used by routine-level conditions and IF steps.
// ---------------------------------------------------------------------------

enum class ConditionType(
    val key: String,
    val title: String,
    val params: List<ParamSpec> = emptyList()
) {
    BATTERY_BELOW(
        "battery_below", "Battery below %",
        listOf(ParamSpec("percent", "Percent", "SLIDER", "20"))
    ),
    BATTERY_ABOVE(
        "battery_above", "Battery above %",
        listOf(ParamSpec("percent", "Percent", "SLIDER", "80"))
    ),
    WIFI_CONNECTED("wifi_connected", "Wi-Fi is connected"),
    WIFI_DISCONNECTED("wifi_disconnected", "Wi-Fi is disconnected"),
    IS_CHARGING("is_charging", "Device is charging"),
    TIME_BETWEEN(
        "time_between", "Time is between",
        listOf(
            ParamSpec("startTime", "From", "TIME", "22:00"),
            ParamSpec("endTime", "Until", "TIME", "07:00")
        )
    ),
    DAY_IS(
        "day_is", "Today is one of",
        listOf(ParamSpec("days", "Days", "DAYS", ""))
    ),
    DND_ON("dnd_on", "Do Not Disturb is on"),
    DND_OFF("dnd_off", "Do Not Disturb is off"),
    SCREEN_ON("screen_on", "Screen is on"),
    APP_FOREGROUND(
        "app_foreground", "App is in the foreground",
        listOf(ParamSpec("packageName", "App", "APP", ""))
    ),
    HEADPHONES_CONNECTED("headphones_connected", "Headphones are connected"),
    BLUETOOTH_CONNECTED("bluetooth_connected", "A Bluetooth device is connected");

    companion object {
        fun fromKey(key: String): ConditionType? = entries.firstOrNull { it.key == key }
    }
}

// ---------------------------------------------------------------------------
// Engine inputs — plain Kotlin, no Android dependencies, unit-testable.
// ---------------------------------------------------------------------------

/** A point-in-time view of the phone, assembled by the platform layer. */
data class TriggerSnapshot(
    val now: Long = System.currentTimeMillis(),
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val wifiSsid: String? = null, // null = not connected
    val bluetoothDeviceName: String? = null,
    val bluetoothDeviceAddress: String? = null,
    val isHeadphonesConnected: Boolean? = null,
    val isAirplaneMode: Boolean? = null,
    val isDndOn: Boolean? = null,
    val isScreenOn: Boolean? = null,
    val foregroundPackage: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val clipboardText: String? = null,
    val lastNotificationPackage: String? = null,
    val lastNotificationText: String? = null,
    val lastNotificationAt: Long? = null,
    /** "ringing" | "offhook" | "idle" */
    val callState: String? = null
)

/** A discrete event the engine recently saw (type key + time + data). */
data class TriggerEvent(
    val type: String,
    val at: Long = System.currentTimeMillis(),
    val data: Map<String, String> = emptyMap()
)
