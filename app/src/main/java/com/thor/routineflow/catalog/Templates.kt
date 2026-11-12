package com.thor.routineflow.catalog

import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.ModeProfile
import com.thor.routineflow.model.ModeSettings
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.TriggerConfig

/**
 * Built-in template routines and modes. Every template is clearly labelled as a
 * template in the UI, uses only Play-safe triggers/actions, and contains no
 * personal data — placeholders (home Wi-Fi name, home location, car Bluetooth
 * name) are empty on purpose and the user fills them in.
 *
 * Templates are copied into the user's routine list when tapped (never edited
 * in place), so the gallery always stays pristine.
 */
object RoutineTemplates {

    private fun t(type: String, vararg params: Pair<String, String>) =
        TriggerConfig(type, params.toMap())

    private fun a(type: String, vararg params: Pair<String, String>) =
        ActionConfig(type, params.toMap())

    val all: List<Routine> = listOf(
        Routine(
            id = "template-bedtime",
            name = "Bedtime wind-down",
            icon = "moon",
            color = 0xFF5B5BD6,
            triggers = listOf(t("time_of_day", "time" to "23:00")),
            actions = listOf(
                a("dnd_set", "enabled" to "on"),
                a("brightness", "percent" to "20"),
                a("volume_media", "percent" to "30"),
                a("screen_timeout", "timeoutMs" to "15000")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-morning",
            name = "Good morning",
            icon = "sunrise",
            color = 0xFFE8A33D,
            triggers = listOf(t("alarm", "time" to "07:00", "label" to "Wake up")),
            actions = listOf(
                a("dnd_set", "enabled" to "off"),
                a("brightness", "percent" to "80"),
                a("speak_time"),
                a("volume_media", "percent" to "60")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-low-battery",
            name = "Low battery saver",
            icon = "battery",
            color = 0xFFD64545,
            triggers = listOf(t("battery_level", "direction" to "below", "percent" to "20")),
            actions = listOf(
                a("brightness", "percent" to "30"),
                a("screen_timeout", "timeoutMs" to "15000"),
                a("show_notification", "title" to "Battery low", "text" to "Battery below 20% — power-saving settings applied.")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-driving",
            name = "Driving mode",
            icon = "car",
            color = 0xFF2E7D5B,
            triggers = listOf(t("bluetooth_connected", "deviceName" to "")),
            actions = listOf(
                a("dnd_set", "enabled" to "on"),
                a("volume_media", "percent" to "80"),
                a("speak_text", "text" to "Driving mode on. Stay safe.")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-work-focus",
            name = "Work focus",
            icon = "work",
            color = 0xFF6750A4,
            triggers = listOf(t("time_of_day", "time" to "09:00", "days" to "1,2,3,4,5")),
            actions = listOf(
                a("dnd_set", "enabled" to "on"),
                a("ringer_mode", "mode" to "vibrate"),
                a("volume_notification", "percent" to "20")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-home-wifi",
            name = "Welcome home",
            icon = "home",
            color = 0xFF2E7D5B,
            triggers = listOf(t("wifi_connected", "ssid" to "")),
            actions = listOf(
                a("ringer_mode", "mode" to "normal"),
                a("auto_brightness", "enabled" to "on"),
                a("volume_media", "percent" to "60")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-headphones",
            name = "Headphones in, music up",
            icon = "headphones",
            color = 0xFF8E4EC6,
            triggers = listOf(t("headphones", "state" to "connected")),
            actions = listOf(
                a("volume_media", "percent" to "70"),
                a("media_play_pause")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-meeting",
            name = "Silence for video calls",
            icon = "video",
            color = 0xFF6750A4,
            triggers = listOf(t("app_opened", "packageName" to "")),
            actions = listOf(
                a("dnd_set", "enabled" to "on"),
                a("volume_notification", "percent" to "0")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-night-charging",
            name = "Night charging care",
            icon = "charging",
            color = 0xFF5B5BD6,
            triggers = listOf(t("charging", "state" to "charging")),
            conditions = listOf(
                com.thor.routineflow.model.ConditionConfig(
                    "time_between",
                    mapOf("startTime" to "23:00", "endTime" to "06:00")
                )
            ),
            actions = listOf(
                a("brightness", "percent" to "10"),
                a("screen_timeout", "timeoutMs" to "15000")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-leaving-home",
            name = "Leaving home reminder",
            icon = "leave",
            color = 0xFFE8A33D,
            triggers = listOf(
                t("location_leave", "name" to "Home", "location" to "", "radiusMeters" to "200")
            ),
            actions = listOf(
                a("show_notification", "title" to "Leaving home", "text" to "Doors locked? Lights off? Have a good trip.")
            ),
            isTemplate = true
        ),
        Routine(
            id = "template-call-silence",
            name = "Pause media on calls",
            icon = "call",
            color = 0xFF2E7D5B,
            triggers = listOf(t("call_state", "state" to "ringing")),
            actions = listOf(a("media_play_pause")),
            isTemplate = true
        ),
        Routine(
            id = "template-clipboard-speak",
            name = "Read my clipboard aloud",
            icon = "clipboard",
            color = 0xFF8E4EC6,
            triggers = listOf(t("clipboard_changed")),
            actions = listOf(
                a("speak_text", "text" to "Copied.")
            ),
            isTemplate = true
        )
    )

    /** Built-in Modes, seeded on first launch. */
    val builtInModes: List<ModeProfile> = listOf(
        ModeProfile(
            id = "mode-sleep",
            name = "Sleep",
            icon = "moon",
            color = 0xFF5B5BD6,
            settings = ModeSettings(
                dnd = true,
                ringerMode = "silent",
                brightness = 25,
                mediaVolume = 20,
                screenTimeoutMs = 15_000
            ),
            isBuiltIn = true
        ),
        ModeProfile(
            id = "mode-driving",
            name = "Driving",
            icon = "car",
            color = 0xFF2E7D5B,
            settings = ModeSettings(
                dnd = true,
                mediaVolume = 80,
                ringVolume = 80
            ),
            autoTriggers = listOf(t("bluetooth_connected", "deviceName" to "")),
            isBuiltIn = true
        ),
        ModeProfile(
            id = "mode-work",
            name = "Work",
            icon = "work",
            color = 0xFF6750A4,
            settings = ModeSettings(
                dnd = true,
                ringerMode = "vibrate",
                ringVolume = 30
            ),
            isBuiltIn = true
        ),
        ModeProfile(
            id = "mode-focus",
            name = "Focus",
            icon = "focus",
            color = 0xFFE8A33D,
            settings = ModeSettings(
                dnd = true,
                ringerMode = "silent",
                mediaVolume = 30
            ),
            isBuiltIn = true
        ),
        ModeProfile(
            id = "mode-relax",
            name = "Relax",
            icon = "relax",
            color = 0xFF2E7D5B,
            settings = ModeSettings(
                dnd = false,
                ringerMode = "normal",
                brightness = 128,
                mediaVolume = 50
            ),
            isBuiltIn = true
        )
    )
}
