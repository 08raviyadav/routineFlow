package com.thor.routineflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.triggers.GeofenceManager
import com.thor.routineflow.triggers.TimeTriggerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores the engine after boot or an app update: re-arms time alarms and
 * geofences (both are lost on reboot), restarts the engine when it is
 * enabled, and makes sure the watchdog is scheduled.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as? RoutineFlowApp
                val container = app?.container ?: AppContainer(context.applicationContext)
                val routines = runCatching { container.routineRepository.getAll() }
                    .getOrDefault(emptyList())
                TimeTriggerScheduler.rescheduleAll(context, routines)
                GeofenceManager.syncGeofences(context, routines)
                WatchdogWorker.schedule(context)
                val engineEnabled = runCatching { container.preferences.isEngineEnabled() }
                    .getOrDefault(true)
                if (engineEnabled) {
                    runCatching { EngineService.start(context) }
                        .onFailure { Log.w(TAG, "Could not restart engine after boot", it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Boot restore failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
