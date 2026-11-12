package com.thor.routineflow.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.ActionType
import com.thor.routineflow.model.Capability
import com.thor.routineflow.model.ConditionConfig
import com.thor.routineflow.model.ConditionType
import com.thor.routineflow.model.ModeProfile
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.TriggerConfig
import com.thor.routineflow.model.TriggerType
import com.thor.routineflow.ui.theme.StatusError
import com.thor.routineflow.ui.theme.StatusErrorContainer
import com.thor.routineflow.ui.theme.StatusSuccess
import com.thor.routineflow.ui.theme.StatusSuccessContainer
import com.thor.routineflow.ui.theme.StatusWarning
import com.thor.routineflow.ui.theme.StatusWarningContainer
import com.thor.routineflow.util.InstalledApps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

private val dayShortNames = mapOf(
    1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun"
)

/** "1,2,3,4,5" -> "Weekdays"; "6,7" -> "Weekends"; "1,3" -> "Mon, Wed". Blank -> "". */
fun formatDays(csv: String): String {
    val days = csv.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
    if (days.isEmpty()) return ""
    if (days.size == 7) return "Every day"
    if (days == setOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (days == setOf(6, 7)) return "Weekends"
    return days.sorted().mapNotNull { dayShortNames[it] }.joinToString(", ")
}

fun formatDateTime(atMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(atMillis))

fun formatTime(atMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(atMillis))

// ---------------------------------------------------------------------------
// One-line summaries (plain text, so templates and cards can reuse them)
// ---------------------------------------------------------------------------

fun triggerSummaryText(trigger: TriggerConfig, context: Context): String {
    val p = trigger.params
    return when (trigger.type) {
        "time_of_day" -> {
            val days = formatDays(p["days"].orEmpty())
            "At ${p["time"].orEmpty().ifBlank { "??:??" }}" + if (days.isNotEmpty()) " · $days" else ""
        }
        "alarm" -> {
            val days = formatDays(p["days"].orEmpty())
            "Alarm at ${p["time"].orEmpty().ifBlank { "??:??" }}" + if (days.isNotEmpty()) " · $days" else ""
        }
        "sunrise" -> "At sunrise" + offsetSuffix(p["offsetMinutes"])
        "sunset" -> "At sunset" + offsetSuffix(p["offsetMinutes"])
        "battery_level" -> {
            val dir = if (p["direction"] == "above") "rises above" else "falls below"
            "Battery $dir ${p["percent"].orEmpty().ifBlank { "?" }}%"
        }
        "charging" -> if (p["state"] == "discharging") "Charger unplugged" else "Charger plugged in"
        "wifi_connected" -> {
            val ssid = p["ssid"].orEmpty().ifBlank { "any" }
            "Wi-Fi connected ($ssid)"
        }
        "wifi_disconnected" -> "Wi-Fi disconnected"
        "bluetooth_connected" -> {
            val name = p["deviceName"].orEmpty().ifBlank { "any device" }
            "Bluetooth connected ($name)"
        }
        "bluetooth_disconnected" -> "Bluetooth disconnected"
        "location_arrive" -> "Arrive at ${p["name"].orEmpty().ifBlank { "place" }}"
        "location_leave" -> "Leave ${p["name"].orEmpty().ifBlank { "place" }}"
        "app_opened" -> "App opened: ${appLabel(context, p["packageName"])}"
        "app_closed" -> "App closed: ${appLabel(context, p["packageName"])}"
        "screen_on" -> "Screen turns on"
        "screen_off" -> "Screen turns off"
        "headphones" -> if (p["state"] == "disconnected") "Headphones unplugged" else "Headphones plugged in"
        "notification" -> {
            val app = p["packageName"].orEmpty()
            val text = p["textContains"].orEmpty()
            buildString {
                append("Notification received")
                if (app.isNotBlank()) append(" from ${appLabel(context, app)}")
                if (text.isNotBlank()) append(" containing \"$text\"")
            }
        }
        "airplane_mode" -> "Airplane mode ${p["state"].orEmpty().ifBlank { "on" }}"
        "dnd_changed" -> "Do Not Disturb ${p["state"].orEmpty().ifBlank { "on" }}"
        "unlocked" -> "Device unlocked"
        "call_state" -> if (p["state"] == "ended") "Call ended" else "Incoming call ringing"
        "clipboard_changed" -> {
            val text = p["textContains"].orEmpty()
            "Clipboard changed" + if (text.isNotBlank()) " containing \"$text\"" else ""
        }
        else -> TriggerType.fromKey(trigger.type)?.title ?: trigger.type
    }
}

fun actionSummaryText(action: ActionConfig, context: Context): String {
    val p = action.params
    val title = ActionType.fromKey(action.type)?.title ?: action.type
    return when (action.type) {
        "if" -> {
            val cond = ConditionType.fromKey(p["conditionType"].orEmpty())
            "If " + if (cond != null) {
                ifConditionText(cond.key, p)
            } else {
                "condition holds"
            }
        }
        "else" -> "Else"
        "end_if" -> "End if"
        "delay" -> "Wait ${p["seconds"].orEmpty().ifBlank { "?" }} seconds"
        "launch_app" -> "Launch ${appLabel(context, p["packageName"])}"
        "force_stop_app" -> "Force-stop ${appLabel(context, p["packageName"])}"
        "open_url" -> "Open ${p["url"].orEmpty().ifBlank { "link" }}"
        "http_get" -> "Web request to ${p["url"].orEmpty().ifBlank { "URL" }}"
        "show_notification" -> "Notify: ${p["title"].orEmpty().ifBlank { "RoutineFlow" }}"
        "show_toast" -> "Message: ${p["text"].orEmpty().ifBlank { "…" }}"
        "speak_text" -> "Speak: ${p["text"].orEmpty().ifBlank { "…" }.take(40)}"
        "copy_clipboard" -> "Copy to clipboard"
        "vibrate" -> "Vibrate ${p["durationMs"].orEmpty().ifBlank { "200" }} ms"
        "run_routine", "set_routine_enabled" -> title
        "brightness", "volume_ring", "volume_media", "volume_alarm", "volume_notification" ->
            "$title: ${p["percent"].orEmpty().ifBlank { "?" }}%"
        "screen_timeout" -> "$title: ${timeoutLabel(p["timeoutMs"])}"
        "wallpaper_color" -> "$title: ${p["color"].orEmpty().ifBlank { "?" }}"
        "set_alarm_intent" -> "Set alarm at ${p["time"].orEmpty().ifBlank { "??:??" }}"
        "set_timer_intent" -> "Start a ${p["seconds"].orEmpty().ifBlank { "?" }}s timer"
        else -> {
            val enabled = p["enabled"]
            val mode = p["mode"]
            when {
                enabled != null -> "$title: ${if (enabled == "on") "On" else "Off"}"
                mode != null -> "$title: ${mode.replaceFirstChar { it.uppercase() }}"
                else -> title
            }
        }
    }
}

fun conditionSummaryText(condition: ConditionConfig, context: Context): String {
    val p = condition.params
    return when (condition.type) {
        "battery_below" -> "Battery below ${p["percent"].orEmpty().ifBlank { "?" }}%"
        "battery_above" -> "Battery above ${p["percent"].orEmpty().ifBlank { "?" }}%"
        "wifi_connected" -> "Wi-Fi is connected"
        "wifi_disconnected" -> "Wi-Fi is disconnected"
        "is_charging" -> "Device is charging"
        "time_between" -> "Time is between ${p["startTime"].orEmpty()} and ${p["endTime"].orEmpty()}"
        "day_is" -> "Today is ${formatDays(p["days"].orEmpty()).ifBlank { "…" }}"
        "dnd_on" -> "Do Not Disturb is on"
        "dnd_off" -> "Do Not Disturb is off"
        "screen_on" -> "Screen is on"
        "app_foreground" -> "${appLabel(context, p["packageName"])} is in the foreground"
        "headphones_connected" -> "Headphones are connected"
        "bluetooth_connected" -> "A Bluetooth device is connected"
        else -> ConditionType.fromKey(condition.type)?.title ?: condition.type
    }
}

/** Human-readable text for an IF step's inline condition (uses the c_* params). */
fun ifConditionText(conditionTypeKey: String, params: Map<String, String>): String {
    return when (conditionTypeKey) {
        "battery_below", "battery_above" -> {
            val dir = if (params["c_direction"] == "above") "above" else "below"
            "battery is $dir ${params["c_percent"].orEmpty().ifBlank { "?" }}%"
        }
        "wifi_connected" -> {
            val ssid = params["c_ssid"].orEmpty()
            "Wi-Fi is connected" + if (ssid.isNotBlank()) " to $ssid" else ""
        }
        "wifi_disconnected" -> "Wi-Fi is disconnected"
        "is_charging" -> "device is charging"
        "time_between" -> "time is between ${params["c_startTime"].orEmpty()} and ${params["c_endTime"].orEmpty()}"
        "day_is" -> "today is ${formatDays(params["c_days"].orEmpty()).ifBlank { "…" }}"
        "dnd_on" -> "Do Not Disturb is on"
        "dnd_off" -> "Do Not Disturb is off"
        "screen_on" -> "screen is on"
        "app_foreground" -> "${params["c_packageName"].orEmpty().ifBlank { "app" }} is in the foreground"
        "headphones_connected" -> "headphones are connected"
        "bluetooth_connected" -> "a Bluetooth device is connected"
        else -> ConditionType.fromKey(conditionTypeKey)?.title ?: conditionTypeKey
    }
}

/** Which c_* param keys are relevant for an IF step's chosen condition type. */
fun ifRelevantParamKeys(conditionTypeKey: String): List<String> = when (conditionTypeKey) {
    "battery_below", "battery_above" -> listOf("c_direction", "c_percent")
    "wifi_connected" -> listOf("c_ssid")
    "time_between" -> listOf("c_startTime", "c_endTime")
    "day_is" -> listOf("c_days")
    "app_foreground" -> listOf("c_packageName")
    else -> emptyList()
}

private fun offsetSuffix(offsetMinutes: String?): String {
    val offset = offsetMinutes?.toIntOrNull() ?: 0
    return when {
        offset == 0 -> ""
        offset > 0 -> " +$offset min"
        else -> " $offset min"
    }
}

private fun timeoutLabel(timeoutMs: String?): String = when (timeoutMs) {
    "15000" -> "15 seconds"
    "30000" -> "30 seconds"
    "60000" -> "1 minute"
    "120000" -> "2 minutes"
    "300000" -> "5 minutes"
    "600000" -> "10 minutes"
    else -> "${timeoutMs.orEmpty()} ms"
}

private fun appLabel(context: Context, packageName: String?): String {
    if (packageName.isNullOrBlank()) return "any app"
    return InstalledApps.labelFor(context, packageName)
}

// ---------------------------------------------------------------------------
// Composable one-line summaries
// ---------------------------------------------------------------------------

@Composable
fun TriggerSummary(trigger: TriggerConfig, modifier: Modifier = Modifier) {
    Text(
        text = triggerSummaryText(trigger, LocalContext.current),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun ActionSummary(action: ActionConfig, modifier: Modifier = Modifier) {
    Text(
        text = actionSummaryText(action, LocalContext.current),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun ConditionSummary(condition: ConditionConfig, modifier: Modifier = Modifier) {
    Text(
        text = conditionSummaryText(condition, LocalContext.current),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
fun RoutineCard(
    routine: Routine,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(routine.color).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                RoutineIcons.IconFor(
                    iconKey = routine.icon,
                    modifier = Modifier.size(24.dp),
                    tint = Color(routine.color)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${routine.triggers.size} trigger${if (routine.triggers.size == 1) "" else "s"} · " +
                        "${routine.actions.size} action${if (routine.actions.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val runInfo = if (routine.runCount > 0) {
                    val last = routine.lastRunAt?.let { " · Last ${formatDateTime(it)}" } ?: ""
                    "Ran ${routine.runCount} time${if (routine.runCount == 1) "" else "s"}$last"
                } else {
                    "Never run"
                }
                Text(
                    text = runInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = routine.enabled,
                onCheckedChange = onToggle
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Routine options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Run now") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRun()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ModeCard(
    mode: ModeProfile,
    onToggle: (ModeProfile) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(mode.color).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                RoutineIcons.IconFor(
                    iconKey = mode.icon,
                    modifier = Modifier.size(24.dp),
                    tint = Color(mode.color)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = modeSettingsSummary(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (mode.autoTriggers.isNotEmpty()) {
                    Text(
                        text = "Auto: ${mode.autoTriggers.size} trigger${if (mode.autoTriggers.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (mode.isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mode.isActive) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = mode.isActive,
                    onCheckedChange = { onToggle(mode) }
                )
            }
        }
    }
}

fun modeSettingsSummary(mode: ModeProfile): String {
    val s = mode.settings
    val parts = mutableListOf<String>()
    s.dnd?.let { parts += if (it) "DND on" else "DND off" }
    s.ringerMode?.let { parts += "Ringer: $it" }
    s.brightness?.let { parts += "Brightness ${it * 100 / 255}%" }
    s.autoBrightness?.let { parts += if (it) "Auto-brightness on" else "Auto-brightness off" }
    s.mediaVolume?.let { parts += "Media $it%" }
    s.ringVolume?.let { parts += "Ring $it%" }
    s.screenTimeoutMs?.let { parts += "Timeout ${it / 1000}s" }
    s.autoRotate?.let { parts += if (it) "Auto-rotate on" else "Auto-rotate off" }
    return if (parts.isEmpty()) "No settings changed" else parts.joinToString(" · ")
}

// ---------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------

@Composable
fun CapabilityBadge(capability: Capability, modifier: Modifier = Modifier) {
    val (containerColor, contentColor) = when (capability) {
        Capability.PLAY_SAFE -> StatusSuccessContainer to StatusSuccess
        Capability.SHIZUKU -> StatusWarningContainer to StatusWarning
        Capability.ROOT -> StatusErrorContainer to StatusError
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = capability.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * A labelled dropdown built on an [OutlinedButton] + [DropdownMenu].
 * Options are (value to label) pairs.
 */
@Composable
fun DropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
        ?: selectedValue.ifBlank { "Choose…" }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onValueChange(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
