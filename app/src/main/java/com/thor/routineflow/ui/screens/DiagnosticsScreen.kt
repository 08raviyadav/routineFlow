package com.thor.routineflow.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.ui.components.SectionHeader
import com.thor.routineflow.ui.components.formatDateTime
import com.thor.routineflow.ui.theme.StatusError
import com.thor.routineflow.ui.theme.StatusErrorContainer
import com.thor.routineflow.ui.theme.StatusSuccess
import com.thor.routineflow.ui.theme.StatusSuccessContainer
import com.thor.routineflow.ui.viewmodel.AppViewModelFactory
import com.thor.routineflow.ui.viewmodel.DiagnosticsViewModel
import com.thor.routineflow.util.OemGuide
import com.thor.routineflow.util.Permissions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onNavigateToWizard: () -> Unit
) {
    val viewModel: DiagnosticsViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun openIntent(intent: Intent?) {
        if (intent == null) return
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("Couldn't open that settings screen on this phone.") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Re-check")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.loading && state.permissions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Checking…", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // -- Engine status --
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Engine", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.engineEnabled) {
                                "The engine is on. Routines run in the background while it stays on."
                            } else {
                                "The engine is off. No routines will run until you turn it back on."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.setEngineEnabled(!state.engineEnabled) }
                        ) {
                            Text(if (state.engineEnabled) "Stop engine" else "Start engine")
                        }
                    }
                }
            }

            // -- Exact alarms --
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.exactAlarmAllowed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (state.exactAlarmAllowed) StatusSuccess else StatusError
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text("Exact alarms", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (state.exactAlarmAllowed) {
                                    "Allowed — time, alarm and sunrise/sunset triggers fire on time."
                                } else {
                                    "Not allowed — time-based triggers may fire late."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!state.exactAlarmAllowed) {
                            TextButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    openIntent(intent)
                                }
                            ) { Text("Fix") }
                        }
                    }
                }
            }

            // -- Permission health --
            item { SectionHeader("Permissions") }
            if (state.permissions.isEmpty()) {
                item {
                    Text(
                        text = "No permission information available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.permissions, key = { it.key }) { permission ->
                PermissionRow(
                    permission = permission,
                    onFix = { openIntent(Permissions.openSettingsIntent(context, permission.key)) }
                )
            }

            // -- OEM battery setup --
            item { SectionHeader("Battery & background") }
            item {
                state.brand?.let { brand ->
                    OemCard(
                        brand = brand,
                        onOpenBatterySettings = {
                            brand.settingsAction?.let { action ->
                                openIntent(Intent(action))
                            }
                        }
                    )
                }
            }

            // -- Watchdog --
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Watchdog", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.lastWatchdogRun > 0) {
                                "Last ran ${formatDateTime(state.lastWatchdogRun)}. " +
                                    "The watchdog re-arms alarms and geofences in the background."
                            } else {
                                "Hasn't run yet. The watchdog re-arms alarms and geofences " +
                                    "in the background — it runs on its own schedule."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateToWizard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open permission wizard")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Re-check")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: Permissions.PermissionStatus,
    onFix: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (permission.granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (permission.granted) StatusSuccess else StatusError
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(permission.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = if (permission.granted) StatusSuccessContainer else StatusErrorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (permission.granted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (permission.granted) StatusSuccess else StatusError,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (!permission.granted && permission.kind != Permissions.Kind.INFO) {
                TextButton(onClick = onFix) { Text("Fix") }
            }
        }
    }
}

@Composable
private fun OemCard(
    brand: OemGuide.OemBrand,
    onOpenBatterySettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Detected: ${brand.label}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Some phones kill background apps aggressively. Follow these steps " +
                    "so your routines keep running:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            brand.steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "${index + 1}. ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (brand.settingsAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onOpenBatterySettings) {
                    Text("Open battery settings")
                }
            }
        }
    }
}
