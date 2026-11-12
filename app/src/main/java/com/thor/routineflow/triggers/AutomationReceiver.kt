package com.thor.routineflow.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.TriggerEvent
import com.thor.routineflow.service.EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for the engine's own broadcasts: time/alarm/sun alarms from
 * [TimeTriggerScheduler], geofence transitions from [GeofenceManager], and
 * explicit evaluate requests. Parses each intent into a [TriggerEvent] and
 * hands it to [EngineService].
 *
 * Time-based alarms re-arm themselves here: after emitting the event, all
 * time triggers are rescheduled so the next occurrence is always queued.
 */
class AutomationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TIME_TRIGGER = "com.thor.routineflow.ACTION_TIME_TRIGGER"
        const val ACTION_ALARM_TRIGGER = "com.thor.routineflow.ACTION_ALARM_TRIGGER"
        const val ACTION_SUN_TRIGGER = "com.thor.routineflow.ACTION_SUN_TRIGGER"
        const val ACTION_GEOFENCE = "com.thor.routineflow.ACTION_GEOFENCE"
        const val ACTION_EVALUATE = "com.thor.routineflow.ACTION_EVALUATE"

        const val EXTRA_TRIGGER_TYPE = "trigger_type"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_TRIGGER_INDEX = "trigger_index"

        private const val TAG = "AutomationReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ACTION_EVALUATE -> EngineService.evaluateNow(context)

            ACTION_TIME_TRIGGER, ACTION_ALARM_TRIGGER, ACTION_SUN_TRIGGER -> {
                val triggerType = intent.getStringExtra(EXTRA_TRIGGER_TYPE) ?: return
                val routineId = intent.getStringExtra(EXTRA_ROUTINE_ID)
                val data = if (routineId != null) {
                    mapOf("routineId" to routineId)
                } else {
                    emptyMap()
                }
                EngineService.onExternalEvent(context, TriggerEvent(type = triggerType, data = data))
                // Re-arm the next occurrence.
                val pending = goAsync()
                scope.launch {
                    try {
                        val container = containerFor(context)
                        val routines = runCatching { container.routineRepository.getAll() }
                            .getOrDefault(emptyList())
                        TimeTriggerScheduler.rescheduleAll(context, routines)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to re-arm time triggers", e)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_GEOFENCE -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        handleGeofence(context, intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to handle geofence event", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun handleGeofence(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            Log.w(TAG, "Geofence event error: ${event?.errorCode}")
            return
        }
        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "location_arrive"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "location_leave"
            else -> {
                Log.i(TAG, "Ignoring geofence transition ${event.geofenceTransition}")
                return
            }
        }
        val requestId = event.triggeringGeofences?.firstOrNull()?.requestId ?: return
        val parts = requestId.split("|")
        val routineId = parts.getOrNull(0)
        val triggerIndex = parts.getOrNull(1)?.toIntOrNull()

        // Resolve the place name so name-scoped triggers match precisely.
        var name: String? = null
        if (routineId != null && triggerIndex != null) {
            val routine = runCatching {
                containerFor(context).routineRepository.getById(routineId)
            }.getOrNull()
            name = routine?.triggers?.getOrNull(triggerIndex)?.params?.get("name")
                ?.takeIf { it.isNotEmpty() }
        }
        val data = buildMap<String, String> {
            if (routineId != null) put("routineId", routineId)
            if (name != null) put("name", name)
        }
        EngineService.onExternalEvent(context, TriggerEvent(type = type, data = data))
    }

    private fun containerFor(context: Context): AppContainer {
        val app = context.applicationContext as? RoutineFlowApp
        return app?.container ?: AppContainer(context.applicationContext)
    }
}
