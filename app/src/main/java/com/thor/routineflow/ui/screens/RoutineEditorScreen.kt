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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.ActionCategory
import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.ActionType
import com.thor.routineflow.model.Capability
import com.thor.routineflow.model.ConditionConfig
import com.thor.routineflow.model.ConditionType
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.TriggerConfig
import com.thor.routineflow.model.TriggerType
import com.thor.routineflow.ui.components.ActionSummary
import com.thor.routineflow.ui.components.CapabilityBadge
import com.thor.routineflow.ui.components.ConditionSummary
import com.thor.routineflow.ui.components.IconColorPicker
import com.thor.routineflow.ui.components.ParamEditor
import com.thor.routineflow.ui.components.RoutineIcons
import com.thor.routineflow.ui.components.SectionHeader
import com.thor.routineflow.ui.components.TriggerSummary
import com.thor.routineflow.ui.components.ifRelevantParamKeys
import com.thor.routineflow.ui.viewmodel.AppViewModelFactory
import com.thor.routineflow.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    container: AppContainer,
    routineId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: EditorViewModel = viewModel(factory = AppViewModelFactory(container))
    val routine by viewModel.routine.collectAsState()
    val allRoutines by viewModel.allRoutines.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showConditionPicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(routineId) {
        viewModel.load(routineId)
    }

    fun saveAndThen(onSuccess: () -> Unit) {
        scope.launch {
            val error = viewModel.save()
            if (error == null) {
                onSuccess()
            } else {
                nameError = error
                snackbarHostState.showSnackbar(error)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (routineId == "new") "New routine" else "Edit routine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { saveAndThen(onSaved) }) {
                        Text("Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val current = routine
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // -- Name, icon, colour --
                item {
                    OutlinedTextField(
                        value = current.name,
                        onValueChange = {
                            viewModel.updateName(it)
                            nameError = null
                        },
                        label = { Text("Routine name") },
                        placeholder = { Text("e.g. Bedtime wind-down") },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = { nameError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    IconColorPicker(
                        selectedIcon = current.icon,
                        selectedColor = current.color,
                        onIcon = { viewModel.updateIcon(it) },
                        onColor = { viewModel.updateColor(it) }
                    )
                }

                // -- When (triggers) --
                item {
                    SectionHeader("When")
                    Text(
                        text = "The routine runs when all of these match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(current.triggers, key = { index, trigger -> "t-$index-${trigger.type}" }) { index, trigger ->
                    TriggerCard(
                        trigger = trigger,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == current.triggers.lastIndex,
                        onRemove = { viewModel.removeTrigger(index) },
                        onMove = { direction -> viewModel.moveTrigger(index, direction) },
                        onParamChange = { key, value -> viewModel.updateTriggerParam(index, key, value) },
                        routines = allRoutines
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showTriggerPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add trigger")
                    }
                }

                // -- Only if (conditions) --
                item {
                    SectionHeader("Only if")
                    Text(
                        text = "Optional. Every condition must also hold when the routine runs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(current.conditions, key = { index, condition -> "c-$index-${condition.type}" }) { index, condition ->
                    ConditionCard(
                        condition = condition,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == current.conditions.lastIndex,
                        onRemove = { viewModel.removeCondition(index) },
                        onMove = { direction -> viewModel.moveCondition(index, direction) },
                        onParamChange = { key, value -> viewModel.updateConditionParam(index, key, value) },
                        routines = allRoutines
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showConditionPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add condition")
                    }
                }

                // -- Do (actions) --
                item {
                    SectionHeader("Do")
                    Text(
                        text = "Actions run in order, top to bottom.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(current.actions, key = { index, action -> "a-$index-${action.type}" }) { index, action ->
                    ActionCard(
                        action = action,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == current.actions.lastIndex,
                        onRemove = { viewModel.removeAction(index) },
                        onMove = { direction -> viewModel.moveAction(index, direction) },
                        onParamChange = { key, value -> viewModel.updateActionParam(index, key, value) },
                        routines = allRoutines
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showActionPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add action")
                    }
                }

                // -- Test run --
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val error = viewModel.testRun()
                                if (error == null) {
                                    snackbarHostState.showSnackbar("Test run started — check History")
                                } else {
                                    nameError = error
                                    snackbarHostState.showSnackbar(error)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Test run")
                    }
                    Text(
                        text = "Test run saves the routine first, then runs it right away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showTriggerPicker) {
        TriggerPickerDialog(
            onPick = { type ->
                viewModel.addTrigger(type)
                showTriggerPicker = false
            },
            onDismiss = { showTriggerPicker = false }
        )
    }
    if (showConditionPicker) {
        ConditionPickerDialog(
            onPick = { type ->
                viewModel.addCondition(type)
                showConditionPicker = false
            },
            onDismiss = { showConditionPicker = false }
        )
    }
    if (showActionPicker) {
        ActionPickerDialog(
            onPick = { type ->
                viewModel.addAction(type)
                showActionPicker = false
            },
            onDismiss = { showActionPicker = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Configured step cards
// ---------------------------------------------------------------------------

@Composable
private fun StepCardHeader(
    iconKey: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    summary: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoutineIcons.IconFor(
            iconKey = iconKey,
            tint = MaterialTheme.colorScheme.primary
        )
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            summary()
        }
        IconButton(onClick = onMoveUp, enabled = !isFirst) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = !isLast) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove")
        }
    }
}

@Composable
private fun TriggerCard(
    trigger: TriggerConfig,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onParamChange: (String, String) -> Unit,
    routines: List<Routine>
) {
    val type = TriggerType.fromKey(trigger.type)
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            StepCardHeader(
                iconKey = type?.iconKey ?: "bolt",
                isFirst = isFirst,
                isLast = isLast,
                onMoveUp = { onMove(-1) },
                onMoveDown = { onMove(1) },
                onRemove = onRemove,
                summary = { TriggerSummary(trigger) }
            )
            if (type != null && type.params.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                type.params.forEach { spec ->
                    ParamEditor(
                        spec = spec,
                        value = trigger.params[spec.key] ?: spec.defaultValue,
                        onValueChange = { onParamChange(spec.key, it) },
                        context = context,
                        routines = routines
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ConditionCard(
    condition: ConditionConfig,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onParamChange: (String, String) -> Unit,
    routines: List<Routine>
) {
    val type = ConditionType.fromKey(condition.type)
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            StepCardHeader(
                iconKey = "branch",
                isFirst = isFirst,
                isLast = isLast,
                onMoveUp = { onMove(-1) },
                onMoveDown = { onMove(1) },
                onRemove = onRemove,
                summary = { ConditionSummary(condition) }
            )
            if (type != null && type.params.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                type.params.forEach { spec ->
                    ParamEditor(
                        spec = spec,
                        value = condition.params[spec.key] ?: spec.defaultValue,
                        onValueChange = { onParamChange(spec.key, it) },
                        context = context,
                        routines = routines
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    action: ActionConfig,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onParamChange: (String, String) -> Unit,
    routines: List<Routine>
) {
    val type = ActionType.fromKey(action.type)
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            StepCardHeader(
                iconKey = type?.iconKey ?: "bolt",
                isFirst = isFirst,
                isLast = isLast,
                onMoveUp = { onMove(-1) },
                onMoveDown = { onMove(1) },
                onRemove = onRemove,
                summary = { ActionSummary(action) }
            )
            if (type != null && type.capability != Capability.PLAY_SAFE) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CapabilityBadge(type.capability)
                    Text(
                        text = " This action can't run on this phone — it needs " +
                            if (type.capability == Capability.ROOT) "root." else "Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (type?.requiresNote != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = type.requiresNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (type != null && type.params.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                if (type == ActionType.IF) {
                    // IF steps carry a condition picker plus c_* params; only the
                    // params relevant to the chosen condition are shown.
                    val conditionSpec = type.params.firstOrNull { it.key == "conditionType" }
                    val conditionType = action.params["conditionType"]
                        ?: conditionSpec?.defaultValue.orEmpty()
                    if (conditionSpec != null) {
                        ParamEditor(
                            spec = conditionSpec,
                            value = conditionType,
                            onValueChange = { onParamChange("conditionType", it) },
                            context = context,
                            routines = routines
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val relevant = ifRelevantParamKeys(conditionType)
                    type.params.filter { it.key in relevant }.forEach { spec ->
                        ParamEditor(
                            spec = spec,
                            value = action.params[spec.key] ?: spec.defaultValue,
                            onValueChange = { onParamChange(spec.key, it) },
                            context = context,
                            routines = routines
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    type.params.forEach { spec ->
                        ParamEditor(
                            spec = spec,
                            value = action.params[spec.key] ?: spec.defaultValue,
                            onValueChange = { onParamChange(spec.key, it) },
                            context = context,
                            routines = routines
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pickers
// ---------------------------------------------------------------------------

private val triggerGroups: List<Pair<String, List<TriggerType>>> = listOf(
    "Time" to listOf(
        TriggerType.TIME_OF_DAY, TriggerType.ALARM, TriggerType.SUNRISE, TriggerType.SUNSET
    ),
    "Battery & power" to listOf(
        TriggerType.BATTERY_LEVEL, TriggerType.CHARGING
    ),
    "Connectivity" to listOf(
        TriggerType.WIFI_CONNECTED, TriggerType.WIFI_DISCONNECTED,
        TriggerType.BLUETOOTH_CONNECTED, TriggerType.BLUETOOTH_DISCONNECTED,
        TriggerType.AIRPLANE_MODE
    ),
    "Location" to listOf(
        TriggerType.LOCATION_ARRIVE, TriggerType.LOCATION_LEAVE
    ),
    "Apps & screen" to listOf(
        TriggerType.APP_OPENED, TriggerType.APP_CLOSED,
        TriggerType.SCREEN_ON, TriggerType.SCREEN_OFF,
        TriggerType.NOTIFICATION, TriggerType.CLIPBOARD_CHANGED
    ),
    "Device" to listOf(
        TriggerType.HEADPHONES, TriggerType.DND_CHANGED,
        TriggerType.UNLOCKED, TriggerType.CALL_STATE
    )
)

@Composable
private fun TriggerPickerDialog(
    onPick: (TriggerType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add trigger") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                triggerGroups.forEach { (group, types) ->
                    item {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(types) { _, type ->
                        PickerRow(
                            iconKey = type.iconKey,
                            title = type.title,
                            description = type.description,
                            onClick = { onPick(type) }
                        )
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

@Composable
private fun ConditionPickerDialog(
    onPick: (ConditionType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add condition") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(ConditionType.entries) { _, type ->
                    PickerRow(
                        iconKey = "branch",
                        title = type.title,
                        description = "",
                        onClick = { onPick(type) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ActionPickerDialog(
    onPick: (ActionType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add action") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                ActionCategory.entries.forEach { category ->
                    val actions = ActionType.entries.filter { it.category == category }
                    if (actions.isEmpty()) return@forEach
                    item {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(actions) { _, action ->
                        val privileged = action.capability != Capability.PLAY_SAFE
                        PickerRow(
                            iconKey = action.iconKey,
                            title = action.title,
                            description = action.description,
                            enabled = !privileged,
                            trailing = {
                                if (privileged) CapabilityBadge(action.capability)
                            },
                            onClick = { onPick(action) }
                        )
                    }
                    if (category == ActionCategory.PRIVILEGED) {
                        item {
                            Text(
                                text = "These actions need Shizuku or root, which this version " +
                                    "doesn't support — they're listed so you can see what's " +
                                    "planned, but they can't be added.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
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

@Composable
private fun PickerRow(
    iconKey: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoutineIcons.IconFor(
                iconKey = iconKey,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
    }
}
