package com.thor.routineflow.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Device admin receiver used for one thing only: the LOCK_SCREEN action
 * ([DevicePolicyManager.lockNow]). No password, camera or wipe policies are
 * declared — the `@xml/device_admin` policy file requests force-lock only.
 */
class RoutineFlowDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) = Unit

    override fun onDisabled(context: Context, intent: Intent) = Unit

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, RoutineFlowDeviceAdmin::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching { dpm.isAdminActive(componentName(context)) }.getOrDefault(false)
        }

        /** Locks the device now. Returns false when the admin is not active. */
        fun lockNow(context: Context): Boolean {
            if (!isActive(context)) return false
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return runCatching {
                dpm.lockNow()
                true
            }.getOrDefault(false)
        }

        /** Intent that opens the system device-admin activation screen. */
        fun activationIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    componentName(context)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "RoutineFlow uses device admin only to lock your screen when a routine asks it to."
                )
            }
    }
}
