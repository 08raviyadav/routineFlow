package com.thor.routineflow.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.BackupFile
import com.thor.routineflow.model.Routine
import com.thor.routineflow.ui.components.SectionHeader
import com.thor.routineflow.ui.viewmodel.AppViewModelFactory
import com.thor.routineflow.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    importUri: String?,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToWizard: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory(container))
    val engineEnabled by viewModel.engineEnabled.collectAsState()
    val favouriteRoutineId by viewModel.favouriteRoutineId.collectAsState()
    val homeLocation by viewModel.homeLocation.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var homeLocationDraft by remember(homeLocation) { mutableStateOf(homeLocation ?: "") }
    var showFavouritePicker by remember { mutableStateOf(false) }
    var importHandledFor by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = viewModel.exportToUri(uri.toString())
                snackbarHostState.showSnackbar(
                    if (ok) "Backup exported." else "Couldn't write the backup file."
                )
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val parsed = viewModel.importFromUri(uri.toString())
                if (parsed == null) {
                    snackbarHostState.showSnackbar("Couldn't read that backup file.")
                }
            }
        }
    }

    // A backup file shared into the app (ACTION_VIEW) arrives here as a Uri.
    LaunchedEffect(importUri) {
        if (!importUri.isNullOrBlank() && importUri != importHandledFor) {
            importHandledFor = importUri
            val decoded = if (importUri.contains("%")) Uri.decode(importUri) else importUri
            val parsed = viewModel.importFromUri(decoded)
            if (parsed == null) {
                snackbarHostState.showSnackbar("Couldn't read that backup file.")
            }
        }
    }

    val favouriteRoutine = routines.firstOrNull { it.id == favouriteRoutineId }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // -- Engine --
            item {
                SectionHeader("Engine")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Engine", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (engineEnabled) "Routines are running." else "Routines are paused.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = engineEnabled,
                            onCheckedChange = { viewModel.setEngineEnabled(it) }
                        )
                    }
                }
            }

            // -- Favourite routine --
            item {
                SectionHeader("Favourite routine")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "The Quick Settings tile and home-screen widget run this routine.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = favouriteRoutine?.name ?: "No favourite set",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { showFavouritePicker = true }) {
                                Text("Choose")
                            }
                        }
                    }
                }
            }

            // -- Home location --
            item {
                SectionHeader("Home location")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Used as the default location for sunrise and sunset triggers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = homeLocationDraft,
                            onValueChange = { homeLocationDraft = it },
                            label = { Text("Home location") },
                            placeholder = { Text("27.7172, 85.3240") },
                            singleLine = true,
                            supportingText = { Text("Latitude, longitude. Leave blank to clear it.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.setHomeLocation(homeLocationDraft)
                                scope.launch { snackbarHostState.showSnackbar("Home location saved.") }
                            }
                        ) {
                            Text("Save location")
                        }
                    }
                }
            }

            // -- Backup --
            item {
                SectionHeader("Backup")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Export your routines and modes as a JSON file you can " +
                                "keep, share or import on another phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { exportLauncher.launch("routineflow-backup.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export JSON")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Import JSON")
                            }
                        }
                    }
                }
            }

            // -- About --
            item {
                SectionHeader("About")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("RoutineFlow 1.0.0", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Local only — no account, no data leaves your phone " +
                                "except your own webhooks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsLinkRow(
                            title = "Diagnostics",
                            subtitle = "Engine status, permissions and battery setup",
                            onClick = onNavigateToDiagnostics
                        )
                        SettingsLinkRow(
                            title = "Permission wizard",
                            subtitle = "Grant the permissions routines need, step by step",
                            onClick = onNavigateToWizard
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showFavouritePicker) {
        FavouritePickerDialog(
            routines = routines,
            selectedId = favouriteRoutineId,
            onPick = { routine ->
                viewModel.setFavouriteRoutineId(routine?.id)
                showFavouritePicker = false
            },
            onDismiss = { showFavouritePicker = false }
        )
    }

    pendingImport?.let { backup ->
        ImportConfirmDialog(
            backup = backup,
            onConfirm = {
                scope.launch {
                    val (routineCount, modeCount) = viewModel.confirmImport()
                    snackbarHostState.showSnackbar(
                        "Imported $routineCount routine${if (routineCount == 1) "" else "s"} " +
                            "and $modeCount mode${if (modeCount == 1) "" else "s"}."
                    )
                }
            },
            onDismiss = { viewModel.dismissImport() }
        )
    }
}

@Composable
private fun SettingsLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun FavouritePickerDialog(
    routines: List<Routine>,
    selectedId: String?,
    onPick: (Routine?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Favourite routine") },
        text = {
            if (routines.isEmpty()) {
                Text("Create a routine first — there is nothing to pick yet.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(null) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "No favourite",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selectedId == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    items(routines, key = { it.id }) { routine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(routine) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = routine.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (routine.id == selectedId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
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
private fun ImportConfirmDialog(
    backup: BackupFile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import backup?") },
        text = {
            Text(
                "This backup contains ${backup.routines.size} routine" +
                    "${if (backup.routines.size == 1) "" else "s"} and " +
                    "${backup.modes.size} mode${if (backup.modes.size == 1) "" else "s"}. " +
                    "Anything with the same ID as an existing routine or mode will be " +
                    "imported as a copy — nothing will be overwritten."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
