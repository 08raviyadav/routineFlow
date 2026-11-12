package com.thor.routineflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.ModeProfile
import com.thor.routineflow.model.ModeSettings
import com.thor.routineflow.model.TriggerConfig
import com.thor.routineflow.model.TriggerType
import com.thor.routineflow.ui.components.DropdownField
import com.thor.routineflow.ui.components.EmptyState
import com.thor.routineflow.ui.components.IconColorPicker
import com.thor.routineflow.ui.components.ModeCard
import com.thor.routineflow.ui.components.ParamEditor
import com.thor.routineflow.ui.components.SectionHeader
import com.thor.routineflow.ui.viewmodel.AppViewModelFactory
import com.thor.routineflow.ui.viewmodel.ModesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(container: AppContainer) {
    val viewModel: ModesViewModel = viewModel(factory = AppViewModelFactory(container))
    val modes by viewModel.modes.collectAsState()
    var editingMode by remember { mutableStateOf<ModeProfile?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Modes") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingMode = ModeProfile(name = "")
                    editingIsNew = true
                    showEditor = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New mode") }
            )
        }
    ) { padding ->
        if (modes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Filled.Bedtime,
                    title = "No modes yet",
                    body = "Modes change a group of settings at once — Sleep silences the " +
                        "phone and dims the screen, Driving turns DND on. " +
                        "Settings restore when the mode ends.",
                    actionLabel = "Create a mode",
                    onAction = {
                        editingMode = ModeProfile(name = "")
                        editingIsNew = true
                        showEditor = true
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Tap a mode to turn it on or off. Settings restore when the mode ends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(modes, key = { it.id }) { mode ->
                    ModeCard(
                        mode = mode,
                        onToggle = { viewModel.toggleMode(it) },
                        onClick = {
                            editingMode = mode
                            editingIsNew = false
                            showEditor = true
                        }
                    )
                }
                item { Spacer(Modifier.height(64.dp)) }
            }
        }
    }

    if (showEditor) {
        editingMode?.let { mode ->
            ModeEditorDialog(
                mode = mode,
                isNew = editingIsNew,
                onDismiss = { showEditor = false },
                onSave = { updated ->
                    viewModel.saveMode(updated)
                    showEditor = false
                },
                onDelete = if (!mode.isBuiltIn) {
                    {
                        viewModel.deleteMode(mode.id)
                        showEditor = false
                    }
                } else null
            )
        }
    }
}

/** Trigger types offered as simple auto-triggers for a mode. */
private val modeTriggerTypes = listOf(
    TriggerType.TIME_OF_DAY,
    TriggerType.BLUETOOTH_CONNECTED,
    TriggerType.WIFI_CONNECTED
)

@Composable
private fun ModeEditorDialog(
    mode: ModeProfile,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ModeProfile) -> Unit,
    onDelete: (() -> Unit)?
) {
    var draft by remember { mutableStateOf(mode) }
    var nameError by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settings = draft.settings

    fun updateSettings(transform: (ModeSettings) -> ModeSettings) {
        draft = draft.copy(settings = transform(draft.settings))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New mode" else "Edit mode") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        draft = draft.copy(name = it)
                        nameError = false
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = { if (nameError) Text("Give the mode a name.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                IconColorPicker(
                    selectedIcon = draft.icon,
                    selectedColor = draft.color,
                    onIcon = { draft = draft.copy(icon = it) },
                    onColor = { draft = draft.copy(color = it) }
                )

                SectionHeader("Settings")
                Text(
                    text = "Leave a setting on “Unchanged” and the mode won't touch it. " +
                        "Settings restore when the mode ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                TriStateChoice(
                    label = "Do Not Disturb",
                    value = settings.dnd,
                    onChange = { updateSettings { it.copy(dnd = it) } }
                )
                Spacer(Modifier.height(12.dp))
                DropdownField(
                    label = "Ringer mode",
                    options = listOf(
                        "" to "Leave unchanged",
                        "normal" to "Normal",
                        "vibrate" to "Vibrate",
                        "silent" to "Silent"
                    ),
                    selectedValue = settings.ringerMode ?: "",
                    onValueChange = { updateSettings { it.copy(ringerMode = it.ifBlank { null }) } }
                )
                Spacer(Modifier.height(12.dp))
                TriStateChoice(
                    label = "Auto-brightness",
                    value = settings.autoBrightness,
                    onChange = { updateSettings { it.copy(autoBrightness = it) } }
                )
                Spacer(Modifier.height(12.dp))
                OptionalSlider(
                    label = "Brightness",
                    enabled = settings.brightness != null,
                    onEnabledChange = { on ->
                        updateSettings { it.copy(brightness = if (on) 128 else null) }
                    },
                    percent = ((settings.brightness ?: 128) * 100 / 255),
                    onPercentChange = { percent ->
                        updateSettings { it.copy(brightness = percent * 255 / 100) }
                    }
                )
                Spacer(Modifier.height(12.dp))
                OptionalSlider(
                    label = "Media volume",
                    enabled = settings.mediaVolume != null,
                    onEnabledChange = { on ->
                        updateSettings { it.copy(mediaVolume = if (on) 50 else null) }
                    },
                    percent = settings.mediaVolume ?: 50,
                    onPercentChange = { percent ->
                        updateSettings { it.copy(mediaVolume = percent) }
                    }
                )
                Spacer(Modifier.height(12.dp))
                OptionalSlider(
                    label = "Ring volume",
                    enabled = settings.ringVolume != null,
                    onEnabledChange = { on ->
                        updateSettings { it.copy(ringVolume = if (on) 50 else null) }
                    },
                    percent = settings.ringVolume ?: 50,
                    onPercentChange = { percent ->
                        updateSettings { it.copy(ringVolume = percent) }
                    }
                )
                Spacer(Modifier.height(12.dp))
                DropdownField(
                    label = "Screen timeout",
                    options = listOf(
                        "" to "Leave unchanged",
                        "15000" to "15 seconds",
                        "30000" to "30 seconds",
                        "60000" to "1 minute",
                        "120000" to "2 minutes",
                        "300000" to "5 minutes",
                        "600000" to "10 minutes"
                    ),
                    selectedValue = settings.screenTimeoutMs?.toString() ?: "",
                    onValueChange = {
                        updateSettings { it.copy(screenTimeoutMs = it.toLongOrNull()) }
                    }
                )
                Spacer(Modifier.height(12.dp))
                TriStateChoice(
                    label = "Auto-rotate",
                    value = settings.autoRotate,
                    onChange = { updateSettings { it.copy(autoRotate = it) } }
                )

                SectionHeader("Turn on automatically")
                Text(
                    text = "Optional. When every trigger below matches, the mode turns on; " +
                        "when any stops matching, it turns off again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                draft.autoTriggers.forEachIndexed { index, trigger ->
                    val type = TriggerType.fromKey(trigger.type)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = type?.title ?: trigger.type,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        draft = draft.copy(
                                            autoTriggers = draft.autoTriggers.filterIndexed { i, _ -> i != index }
                                        )
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove trigger")
                                }
                            }
                            type?.params?.forEach { spec ->
                                ParamEditor(
                                    spec = spec,
                                    value = trigger.params[spec.key] ?: spec.defaultValue,
                                    onValueChange = { newValue ->
                                        val list = draft.autoTriggers.toMutableList()
                                        val current = list[index]
                                        list[index] = current.copy(
                                            params = current.params + (spec.key to newValue)
                                        )
                                        draft = draft.copy(autoTriggers = list)
                                    },
                                    context = context
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { showTriggerPicker = true }) {
                    Text("Add auto-trigger")
                }
                if (showTriggerPicker) {
                    Column {
                        modeTriggerTypes.forEach { type ->
                            TextButton(
                                onClick = {
                                    val defaults = type.params.associate { it.key to it.defaultValue }
                                    draft = draft.copy(
                                        autoTriggers = draft.autoTriggers +
                                            TriggerConfig(type.key, defaults)
                                    )
                                    showTriggerPicker = false
                                }
                            ) {
                                Text(type.title)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (draft.name.isBlank()) {
                        nameError = true
                    } else {
                        onSave(draft)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Unchanged / On / Off choice for a nullable Boolean setting. */
@Composable
private fun TriStateChoice(
    label: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = value == null,
                onClick = { onChange(null) },
                label = { Text("Unchanged") }
            )
            FilterChip(
                selected = value == true,
                onClick = { onChange(true) },
                label = { Text("On") }
            )
            FilterChip(
                selected = value == false,
                onClick = { onChange(false) },
                label = { Text("Off") }
            )
        }
    }
}

/** A slider guarded by an on/off switch; off means "leave unchanged" (null). */
@Composable
private fun OptionalSlider(
    label: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    percent: Int,
    onPercentChange: (Int) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enabled) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
        }
        if (enabled) {
            Slider(
                value = percent.toFloat(),
                onValueChange = { onPercentChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
