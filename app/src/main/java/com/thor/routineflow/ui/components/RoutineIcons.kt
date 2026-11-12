package com.thor.routineflow.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps the icon keys used in the model catalogue and templates to Material icons.
 * Unknown keys fall back to [Icons.Filled.Bolt] so a bad key never crashes a list.
 */
object RoutineIcons {

    /** Icon keys offered in the routine/mode icon picker. */
    val pickerIcons: List<String> = listOf(
        "bolt", "moon", "sunrise", "sunset", "alarm", "schedule",
        "battery", "charging", "wifi", "bluetooth", "home", "work",
        "car", "headphones", "focus", "relax", "video", "call"
    )

    /** Colour swatches offered in the routine/mode colour picker (ARGB longs). */
    val pickerColors: List<Long> = listOf(
        0xFF6750A4, 0xFF5B5BD6, 0xFF2E7D5B, 0xFFE8A33D,
        0xFFD64545, 0xFF8E4EC6, 0xFF0288D1, 0xFF5F6368
    )

    fun iconForKey(iconKey: String): ImageVector = when (iconKey) {
        "bolt" -> Icons.Filled.Bolt
        "moon" -> Icons.Filled.Bedtime
        "sunrise" -> Icons.Filled.WbSunny
        "sunset" -> Icons.Filled.NightsStay
        "battery" -> Icons.Filled.BatteryStd
        "charging" -> Icons.Filled.BatteryChargingFull
        "wifi" -> Icons.Filled.Wifi
        "wifi_off" -> Icons.Filled.WifiOff
        "bluetooth" -> Icons.Filled.Bluetooth
        "bluetooth_off" -> Icons.Filled.BluetoothDisabled
        "arrive" -> Icons.Filled.LocationOn
        "leave" -> Icons.Filled.Logout
        "app_open" -> Icons.Filled.Launch
        "app_close" -> Icons.Filled.Close
        "screen_on" -> Icons.Filled.PhoneAndroid
        "screen_off" -> Icons.Filled.PhoneDisabled
        "headphones" -> Icons.Filled.Headphones
        "notification" -> Icons.Filled.Notifications
        "airplane" -> Icons.Filled.AirplanemodeActive
        "dnd" -> Icons.Filled.DoNotDisturbOn
        "unlock" -> Icons.Filled.LockOpen
        "call" -> Icons.Filled.Call
        "clipboard" -> Icons.Filled.ContentPaste
        "schedule" -> Icons.Filled.Schedule
        "alarm" -> Icons.Filled.Alarm
        "work" -> Icons.Filled.Work
        "car" -> Icons.Filled.DirectionsCar
        "home" -> Icons.Filled.Home
        "video" -> Icons.Filled.Videocam
        "focus" -> Icons.Filled.CenterFocusStrong
        "relax" -> Icons.Filled.Spa
        "volume" -> Icons.Filled.VolumeUp
        "brightness" -> Icons.Filled.Brightness6
        "brightness_auto" -> Icons.Filled.BrightnessAuto
        "timer" -> Icons.Filled.Timer
        "wallpaper" -> Icons.Filled.Wallpaper
        "rotate" -> Icons.Filled.ScreenRotation
        "flashlight" -> Icons.Filled.FlashlightOn
        "lock" -> Icons.Filled.Lock
        "media" -> Icons.Filled.PlayArrow
        "clear" -> Icons.Filled.ClearAll
        "location" -> Icons.Filled.LocationOn
        "link" -> Icons.Filled.Link
        "nfc" -> Icons.Filled.Nfc
        "data" -> Icons.Filled.SignalCellular4Bar
        "speak" -> Icons.Filled.RecordVoiceOver
        "vibrate" -> Icons.Filled.Vibration
        "toast" -> Icons.Filled.Message
        "sound" -> Icons.Filled.MusicNote
        "web" -> Icons.Filled.Public
        "run" -> Icons.Filled.PlayArrow
        "toggle" -> Icons.Filled.ToggleOn
        "wait" -> Icons.Filled.HourglassEmpty
        "branch" -> Icons.Filled.CallSplit
        "dark" -> Icons.Filled.DarkMode
        "stop" -> Icons.Filled.Block
        "sun" -> Icons.Filled.WbSunny
        else -> Icons.Filled.Bolt
    }

    @Composable
    fun IconFor(
        iconKey: String,
        modifier: Modifier = Modifier,
        tint: Color = Color.Unspecified,
        contentDescription: String? = null
    ) {
        val actualTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
        Icon(
            imageVector = iconForKey(iconKey),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = actualTint
        )
    }
}
