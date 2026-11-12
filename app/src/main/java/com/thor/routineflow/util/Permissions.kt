package com.thor.routineflow.util

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.thor.routineflow.admin.RoutineFlowDeviceAdmin
import com.thor.routineflow.triggers.RoutineFlowNotificationListener

/**
 * Single source of truth for every permission / special-access grant RoutineFlow
 * can need, and the Settings screen that grants each one. The UI renders
 * [statusList] as a checklist; tapping an item opens [openSettingsIntent].
 *
 * Runtime permissions (location, camera, …) are requested in-app with the
 * normal permission dialog — their "settings" deep link is the app-details
 * page, used when the user has permanently denied the dialog.
 */
object Permissions {

    enum class Kind { RUNTIME, SPECIAL, INFO }

    data class PermissionStatus(
        val key: String,
        val title: String,
        val description: String,
        val granted: Boolean,
        val kind: Kind
    )

    const val KEY_NOTIFICATIONS = "notifications"
    const val KEY_EXACT_ALARMS = "exact_alarms"
    const val KEY_BATTERY_EXEMPTION = "battery_exemption"
    const val KEY_LOCATION_FINE = "location_fine"
    const val KEY_BACKGROUND_LOCATION = "background_location"
    const val KEY_BLUETOOTH_CONNECT = "bluetooth_connect"
    const val KEY_BLUETOOTH_SCAN = "bluetooth_scan"
    const val KEY_DND_ACCESS = "dnd_access"
    const val KEY_WRITE_SETTINGS = "write_settings"
    const val KEY_USAGE_ACCESS = "usage_access"
    const val KEY_NOTIFICATION_LISTENER = "notification_listener"
    const val KEY_DEVICE_ADMIN = "device_admin"
    const val KEY_CAMERA = "camera"
    const val KEY_PHONE_STATE = "phone_state"

    fun statusList(context: Context): List<PermissionStatus> {
        val appContext = context.applicationContext
        return listOf(
            PermissionStatus(
                KEY_NOTIFICATIONS,
                "Notifications",
                "Lets routines show notifications and keeps the engine's status visible.",
                isGranted(appContext, Manifest.permission.POST_NOTIFICATIONS) || Build.VERSION.SDK_INT < 33,
                Kind.RUNTIME
            ),
            PermissionStatus(
                KEY_EXACT_ALARMS,
                "Exact alarms",
                "Lets time-of-day, alarm and sunrise/sunset triggers fire on time.",
                canScheduleExactAlarms(appContext),
                Kind.SPECIAL
            ),
            PermissionStatus(
                KEY_BATTERY_EXEMPTION,
                "Ignore battery optimisations",
                "Keeps Android from killing the engine in the background.",
                isBatteryExempt(appContext),
                Kind.SPECIAL
            ),
            PermissionStatus(
                KEY_LOCATION_FINE,
                "Precise location",
                "Needed for arrive/leave geofence triggers and Wi-Fi network names.",
                isGranted(appContext, Manifest.permission.ACCESS_FINE_LOCATION),
                Kind.RUNTIME
            ),
            PermissionStatus(
                KEY_BACKGROUND_LOCATION,
                "Location in the background",
                "Needed for 'leave a place' triggers, which fire while the app is closed.",
                isGranted(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                Kind.RUNTIME
            ),
            PermissionStatus(
                KEY_BLUETOOTH_CONNECT,
                "Bluetooth (connect)",
                "Needed to see connected Bluetooth devices and toggle Bluetooth.",
                isGranted(appContext, Manifest.permission.BLUETOOTH_CONNECT) || Build.VERSION.SDK_INT < 31,
                Kind.RUNTIME
            ),
            PermissionStatus(
                KEY_BLUETOOTH_SCAN,
                "Bluetooth (scan)",
                "Lets RoutineFlow see nearby Bluetooth devices.",
                isGranted(appContext, Manifest.permission.BLUETOOTH_SCAN) || Build.VERSION.SDK_INT < 31,
                Kind.RUNTIME
            ),
            PermissionStatus(
                KEY_DND_ACCESS,
                "Do Not Disturb access",
                "Lets routines and Modes turn Do Not Disturb on and off.",
                hasDndAccess(appContext),
                Kind.SPECIAL
            ),
            PermissionStatus(
                KEY_WRITE_SETTINGS,
                "Modify system settings",
                "Lets routines change brightness, screen timeout and auto-rotate.",
                canWriteSettings(appContext),
                Kind.SPECIAL
            ),
            PermissionStatus(
                KEY_USAGE_ACCESS,
                "Usage access",
                "Lets RoutineFlow see which app is in the foreground (app-opened / app-closed triggers).",
                hasUsageAccess(appContext),
                Kind.SPECIAL
            ),
            PermissionStatus(
                KEY_NOTIFICATION_LISTENER,
                "Notification access",
                "Lets RoutineFlow react to notifications, control media playback and clear the shade.",
                isNotificationListenerEnabled(appContext),
                Kind.SPECIAL
            ),
            PermissionStatus(
                KEY_DEVICE_ADMIN,
                "Device admin (lock screen)",
                "Lets the 'Lock screen' action lock the device. No password or camera policies are used.",
                RoutineFlowDeviceAdmin.isActive(appContext),
                Kind.SPECIAL
            ),
            PermissionStatus(
                KEY_CAMERA,
                "Camera",
                "Needed for the flashlight action (the torch is part of the camera).",
                isGranted(appContext, Manifest.permission.CAMERA),
                Kind.RUNTIME
            ),
            PermissionStatus(
                KEY_PHONE_STATE,
                "Phone state",
                "Lets RoutineFlow see incoming calls (call-state trigger).",
                isGranted(appContext, Manifest.permission.READ_PHONE_STATE),
                Kind.RUNTIME
            )
        )
    }

    /**
     * Deep link to the Settings screen that grants [key], or null when there
     * is no such screen. Runtime permissions fall back to the app-details page.
     */
    fun openSettingsIntent(context: Context, key: String): Intent? {
        val appContext = context.applicationContext
        val packageUri = Uri.fromParts("package", appContext.packageName, null)
        val intent = when (key) {
            KEY_BATTERY_EXEMPTION -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(packageUri)
            KEY_EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(packageUri)
            } else {
                appDetailsIntent(appContext)
            }
            KEY_DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            KEY_WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(packageUri)
            KEY_USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            KEY_NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            KEY_DEVICE_ADMIN -> RoutineFlowDeviceAdmin.activationIntent(appContext)
            else -> appDetailsIntent(appContext)
        }
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun isBatteryExempt(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
            ?: return false
        return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean =
        RoutineFlowNotificationListener.isEnabled(context)

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = runCatching {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }.getOrNull() ?: AppOpsManager.MODE_IGNORED
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasDndAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }

    fun canWriteSettings(context: Context): Boolean =
        Settings.System.canWrite(context)

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
}
