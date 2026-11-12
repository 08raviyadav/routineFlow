package com.thor.routineflow.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thor.routineflow.model.ParamKind
import com.thor.routineflow.model.ParamSpec
import com.thor.routineflow.model.Routine
import com.thor.routineflow.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Renders the editor for one [ParamSpec], based on its [ParamKind].
 *
 * @param routines the routines available for ROUTINE params (id -> name dropdown).
 */
@Composable
fun ParamEditor(
    spec: ParamSpec,
    value: String,
    onValueChange: (String) -> Unit,
    context: Context,
    routines: List<Routine> = emptyList(),
    modifier: Modifier = Modifier
) {
    val kind = runCatching { ParamKind.valueOf(spec.kind) }.getOrDefault(ParamKind.TEXT)
    Column(modifier = modifier.fillMaxWidth()) {
        when (kind) {
            ParamKind.TEXT -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(spec.label) },
                    placeholder = { if (spec.hint.isNotBlank()) Text(spec.hint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParamKind.LONG_TEXT -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(spec.label) },
                    placeholder = { if (spec.hint.isNotBlank()) Text(spec.hint) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParamKind.NUMBER -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '-' }) {
                            onValueChange(newValue)
                        }
                    },
                    label = { Text(spec.label) },
                    placeholder = { if (spec.hint.isNotBlank()) Text(spec.hint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParamKind.TIME -> {
                val invalid = value.isNotBlank() && !timeRegex.matches(value.trim())
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(spec.label) },
                    placeholder = { Text("07:30") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = {
                        Text(if (invalid) "Use 24-hour format, e.g. 07:30" else "24-hour format, e.g. 07:30")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParamKind.DAYS -> {
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                val selected = value.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .toSet()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { day ->
                                FilterChip(
                                    selected = day in selected,
                                    onClick = {
                                        val updated = if (day in selected) selected - day else selected + day
                                        onValueChange(updated.sorted().joinToString(","))
                                    },
                                    label = { Text(dayLabels[day - 1]) }
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (selected.isEmpty()) "No days picked — applies every day." else formatDays(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ParamKind.CHOICE -> {
                val options = spec.options.mapNotNull { raw ->
                    val parts = raw.split("|", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                DropdownField(
                    label = spec.label,
                    options = options,
                    selectedValue = value.ifBlank { spec.defaultValue },
                    onValueChange = onValueChange
                )
            }

            ParamKind.BOOLEAN -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(spec.label, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = value == "true",
                        onCheckedChange = { onValueChange(if (it) "true" else "false") }
                    )
                }
            }

            ParamKind.APP -> {
                AppParamField(spec = spec, value = value, onValueChange = onValueChange, context = context)
            }

            ParamKind.COLOR -> {
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(value) ?: Color.Gray)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = { Text("#6750A4") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("#6750A4", "#2E7D5B", "#0288D1", "#E8A33D", "#D64545", "#1C1B1F").forEach { hex ->
                        val swatch = parseHexColor(hex) ?: Color.Gray
                        val selectedSwatch = value.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (selectedSwatch) 3.dp else 1.dp,
                                    color = if (selectedSwatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { onValueChange(hex) }
                        )
                    }
                }
            }

            ParamKind.SLIDER -> {
                val percent = value.toIntOrNull()?.coerceIn(0, 100) ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = spec.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Slider(
                    value = percent.toFloat(),
                    onValueChange = { onValueChange(it.toInt().toString()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParamKind.LOCATION -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(spec.label) },
                    placeholder = { Text("27.7172, 85.3240") },
                    singleLine = true,
                    supportingText = { Text("Latitude, longitude — e.g. 27.7172, 85.3240") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ParamKind.ROUTINE -> {
                if (routines.isEmpty()) {
                    Text(
                        text = "${spec.label}: no routines yet — create one first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DropdownField(
                        label = spec.label,
                        options = routines.map { it.id to it.name },
                        selectedValue = value,
                        onValueChange = onValueChange
                    )
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color? {
    return try {
        val parsed = AndroidColor.parseColor(hex.trim())
        Color(parsed)
    } catch (_: IllegalArgumentException) {
        null
    }
}

// ---------------------------------------------------------------------------
// App picker
// ---------------------------------------------------------------------------

@Composable
private fun AppParamField(
    spec: ParamSpec,
    value: String,
    onValueChange: (String) -> Unit,
    context: Context
) {
    var showPicker by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApps.AppInfo>?>(null) }
    val scope = rememberCoroutineScope()

    val label = when {
        value.isBlank() -> "Choose an app"
        else -> InstalledApps.labelFor(context, value)
    }
    Column {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                showPicker = true
                if (apps == null) {
                    scope.launch {
                        apps = withContext(Dispatchers.IO) { InstalledApps.launcherApps(context) }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1)
        }
    }
    if (showPicker) {
        AppPickerDialog(
            apps = apps ?: emptyList(),
            loading = apps == null,
            onPick = { app ->
                onValueChange(app.packageName)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
fun AppPickerDialog(
    apps: List<InstalledApps.AppInfo>,
    onPick: (InstalledApps.AppInfo) -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    when {
                        loading -> Text(
                            "Loading apps…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                        filtered.isEmpty() -> Text(
                            "No apps match your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                        else -> LazyColumn {
                            items(filtered, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(app) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------
// Icon + colour picker (routines and modes)
// ---------------------------------------------------------------------------

@Composable
fun IconColorPicker(
    selectedIcon: String,
    selectedColor: Long,
    onIcon: (String) -> Unit,
    onColor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Icon",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutineIcons.pickerIcons.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { iconKey ->
                        val selected = iconKey == selectedIcon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onIcon(iconKey) },
                            contentAlignment = Alignment.Center
                        ) {
                            RoutineIcons.IconFor(
                                iconKey = iconKey,
                                modifier = Modifier.size(24.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Colour",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoutineIcons.pickerColors.forEach { colorLong ->
                val selected = colorLong == selectedColor
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(colorLong))
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onColor(colorLong) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
