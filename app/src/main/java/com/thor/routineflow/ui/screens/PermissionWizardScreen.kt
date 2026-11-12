package com.thor.routineflow.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thor.routineflow.admin.RoutineFlowDeviceAdmin
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.ui.theme.StatusSuccess
import com.thor.routineflow.ui.viewmodel.AppViewModelFactory
import com.thor.routineflow.ui.viewmodel.SettingsViewModel
import com.thor.routineflow.util.Permissions
import kotlinx.coroutines.launch

/**
 * First-run setup wizard. Every step is optional and skippable — a routine
 * that needs a permission the user didn't grant simply reports that honestly
 * in History instead of pretending to work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionWizardScreen(
    container: AppContainer,
    onFinish: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory(container))
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val steps = remember { buildWizardSteps() }
    var page by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf<Boolean?>(null) }
    val onDonePage = page >= steps.size

    fun refreshStatus() {
        if (!onDonePage) {
            granted = steps[page].check(context)
        }
    }

    LaunchedEffect(page) {
        refreshStatus()
    }

    // Returning from a settings screen re-checks the current step.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, page) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    fun openIntent(intent: Intent?) {
        if (intent == null) return
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("Couldn't open that settings screen on this phone.") }
        }
    }

    fun grant(step: WizardStep) {
        when (val action = step.action) {
            is GrantAction.Runtime -> {
                if (action.permissions.size == 1) {
                    singlePermissionLauncher.launch(action.permissions[0])
                } else {
                    multiplePermissionLauncher.launch(action.permissions.toTypedArray())
                }
            }
            is GrantAction.Settings -> {
                val intent = action.permissionKey?.let {
                    Permissions.openSettingsIntent(context, it)
                } ?: action.fallback(context)
                openIntent(intent)
            }
            GrantAction.DeviceAdmin -> {
                openIntent(RoutineFlowDeviceAdmin.activationIntent(context))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set up RoutineFlow") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            LinearProgressIndicator(
                progress = { (page + 1).toFloat() / (steps.size + 1) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (onDonePage) "All done" else "Step ${page + 1} of ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            if (onDonePage) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = StatusSuccess,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "You're all set",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Every step was optional — anything you skipped can be granted " +
                        "later from Settings → Diagnostics. Routines that need a missing " +
                        "permission will say so in History instead of failing silently.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        settingsViewModel.setOnboardingDone(true)
                        onFinish()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finish")
                }
            } else {
                val step = steps[page]
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = step.why,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                when (granted) {
                    true -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSuccess
                                )
                                Text(
                                    text = "Already granted — nice.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                    false -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null
                                )
                                Text(
                                    text = "Not granted yet. You can grant it now or skip — " +
                                        "it's your call.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                    null -> Unit
                }
                Spacer(Modifier.weight(1f))
                if (granted != true) {
                    Button(
                        onClick = { grant(step) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(step.grantLabel)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { if (page > 0) page-- },
                        enabled = page > 0
                    ) { Text("Back") }
                    TextButton(onClick = { page++ }) {
                        Text(if (granted == true) "Next" else "Skip")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

private sealed interface GrantAction {
    data class Runtime(val permissions: List<String>) : GrantAction
    data class Settings(
        val permissionKey: String?,
        val fallback: (Context) -> Intent?
    ) : GrantAction

    data object DeviceAdmin : GrantAction
}

private data class WizardStep(
    val id: String,
    val title: String,
    val why: String,
    val grantLabel: String,
    val check: (Context) -> Boolean,
    val action: GrantAction
)

private fun buildWizardSteps(): List<WizardStep> {
    val steps = mutableListOf<WizardStep>()

    // 1. Notifications
    steps += WizardStep(
        id = "notifications",
        title = "Notifications",
        why = "Routines can show you a notification when they run, and the engine " +
            "needs to post a quiet background notification to keep running.",
        grantLabel = "Allow notifications",
        check = { context -> NotificationManagerCompat.from(context).areNotificationsEnabled() },
        action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantAction.Runtime(listOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            GrantAction.Settings(Permissions.KEY_NOTIFICATIONS) { context ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
        }
    )

    // 2. Exact alarms
    steps += WizardStep(
        id = "exact_alarm",
        title = "Exact alarms",
        why = "Time, alarm and sunrise/sunset triggers fire exactly on time only " +
            "with this. Without it they may run late.",
        grantLabel = "Allow exact alarms",
        check = { context ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
            } else {
                true
            }
        },
        action = GrantAction.Settings(Permissions.KEY_EXACT_ALARMS) { context ->
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    )

    // 3. Battery exemption
    steps += WizardStep(
        id = "battery",
        title = "Run in the background",
        why = "Some phones kill background apps aggressively. Allowing RoutineFlow " +
            "to ignore battery optimisations keeps your routines running overnight.",
        grantLabel = "Allow background running",
        check = { context ->
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        },
        action = GrantAction.Settings(Permissions.KEY_BATTERY_EXEMPTION) { context ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    )

    // 4. Location
    steps += WizardStep(
        id = "location",
        title = "Location",
        why = "Needed for arrive/leave triggers and for sunrise/sunset times. " +
            "Your location never leaves your phone.",
        grantLabel = "Allow location",
        check = { context ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        },
        action = GrantAction.Runtime(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    )

    // 5. Bluetooth
    steps += WizardStep(
        id = "bluetooth",
        title = "Bluetooth",
        why = "Needed for “when a Bluetooth device connects” triggers, like your " +
            "car or headphones.",
        grantLabel = "Allow Bluetooth",
        check = { context ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        },
        action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GrantAction.Runtime(listOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            GrantAction.Settings(Permissions.KEY_BLUETOOTH_CONNECT) { context ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
        }
    )

    // 6. Do Not Disturb
    steps += WizardStep(
        id = "dnd",
        title = "Do Not Disturb access",
        why = "Lets routines turn Do Not Disturb on and off — the heart of the " +
            "Bedtime and Driving routines.",
        grantLabel = "Allow Do Not Disturb access",
        check = { context ->
            context.getSystemService(NotificationManager::class.java)
                ?.isNotificationPolicyAccessGranted ?: false
        },
        action = GrantAction.Settings(Permissions.KEY_DND_ACCESS) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    )

    // 7. Usage access
    steps += WizardStep(
        id = "usage_access",
        title = "Usage access",
        why = "Lets RoutineFlow see which app is in the foreground, so “when an app " +
            "opens” triggers work. Nothing about your usage is recorded or shared.",
        grantLabel = "Allow usage access",
        check = { context ->
            val appOps = context.getSystemService(AppOpsManager::class.java)
            appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        },
        action = GrantAction.Settings(Permissions.KEY_USAGE_ACCESS) {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
    )

    // 8. Notification listener
    steps += WizardStep(
        id = "notification_listener",
        title = "Notification access",
        why = "Lets RoutineFlow react to notifications from other apps, control " +
            "media playback and clear notifications. It only reads what your " +
            "routines ask about.",
        grantLabel = "Allow notification access",
        check = { context ->
            val listeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            listeners?.contains(context.packageName) == true
        },
        action = GrantAction.Settings(Permissions.KEY_NOTIFICATION_LISTENER) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    )

    // 9. Device admin (optional — lock screen action only)
    steps += WizardStep(
        id = "device_admin",
        title = "Device admin (optional)",
        why = "Only needed for the “Lock screen” action. RoutineFlow sets no password " +
            "or camera policies — it can only lock the screen when you ask it to.",
        grantLabel = "Set as device admin",
        check = { context -> RoutineFlowDeviceAdmin.isActive(context) },
        action = GrantAction.DeviceAdmin
    )

    return steps
}
