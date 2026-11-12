package com.thor.routineflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.Routine
import com.thor.routineflow.ui.components.EmptyState
import com.thor.routineflow.ui.components.RoutineCard
import com.thor.routineflow.ui.viewmodel.AppViewModelFactory
import com.thor.routineflow.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToModes: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory(container))
    val routines by viewModel.routines.collectAsState()
    val modes by viewModel.modes.collectAsState()
    val engineEnabled by viewModel.engineEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }

    val activeModes = modes.filter { it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RoutineFlow") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (engineEnabled) "Engine on" else "Engine off",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Switch(
                            checked = engineEnabled,
                            onCheckedChange = { viewModel.setEngineEnabled(it) }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToEditor("new") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New routine") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (routines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Filled.Bolt,
                    title = "No routines yet",
                    body = "Routines watch for things — a time, a place, a charger — and " +
                        "do things for you. Start from a template or build your own.",
                    actionLabel = "Browse templates",
                    onAction = onNavigateToTemplates
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
                if (activeModes.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Active modes",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                activeModes.forEach { mode ->
                                    AssistChip(
                                        onClick = onNavigateToModes,
                                        label = { Text(mode.name) }
                                    )
                                }
                            }
                        }
                    }
                }
                items(routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onToggle = { enabled -> viewModel.toggleRoutine(routine.id, enabled) },
                        onClick = { onNavigateToEditor(routine.id) },
                        onRun = {
                            viewModel.runNow(routine.id)
                            scope.launch {
                                snackbarHostState.showSnackbar("Running “${routine.name}”…")
                            }
                        },
                        onDelete = { routineToDelete = routine }
                    )
                }
                item { Spacer(Modifier.height(64.dp)) }
            }
        }
    }

    routineToDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("“${routine.name}” will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRoutine(routine.id)
                        routineToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
