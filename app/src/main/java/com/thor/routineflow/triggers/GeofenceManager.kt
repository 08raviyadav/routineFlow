package com.thor.routineflow.triggers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.thor.routineflow.engine.RuleEngine
import com.thor.routineflow.model.Routine

/**
 * Registers arrive/leave geofences for LOCATION_ARRIVE / LOCATION_LEAVE
 * triggers with Play Services' GeofencingClient.
 *
 * - Needs ACCESS_FINE_LOCATION; without it, geofences are skipped (logged, no crash).
 * - Leave transitions additionally need ACCESS_BACKGROUND_LOCATION; without it
 *   only arrive triggers are registered.
 * - Geofences never expire and use a ~5 minute notification responsiveness,
 *   which is the honest trade-off: geofence delivery is approximate and can
 *   lag, especially in Doze.
 */
object GeofenceManager {

    private const val TAG = "GeofenceManager"
    private const val NOTIFICATION_RESPONSIVENESS_MS = 300_000

    fun syncGeofences(context: Context, routines: List<Routine>) {
        val appContext = context.applicationContext
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Fine location not granted; skipping geofences")
            return
        }
        val backgroundGranted =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        val geofences = mutableListOf<Geofence>()
        for (routine in routines) {
            if (!routine.enabled) continue
            for ((index, trigger) in routine.triggers.withIndex()) {
                val transition = when (trigger.type) {
                    "location_arrive" -> Geofence.GEOFENCE_TRANSITION_ENTER
                    "location_leave" -> {
                        if (!backgroundGranted) {
                            Log.i(TAG, "Background location not granted; skipping leave trigger for '${routine.name}'")
                            continue
                        }
                        Geofence.GEOFENCE_TRANSITION_EXIT
                    }
                    else -> continue
                }
                val location = RuleEngine.parseLocation(trigger.params["location"].orEmpty())
                if (location == null) {
                    Log.w(TAG, "Unparsable location for geofence in '${routine.name}'; skipping")
                    continue
                }
                val radius = (trigger.params["radiusMeters"]?.toFloatOrNull() ?: 200f)
                    .coerceIn(100f, 100_000f)
                geofences += Geofence.Builder()
                    .setRequestId("${routine.id}|$index")
                    .setCircularRegion(location.first, location.second, radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(transition)
                    .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
                    .build()
            }
        }

        val pendingIntent = geofencePendingIntent(appContext)
        val client = try {
            LocationServices.getGeofencingClient(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Play Services unavailable; cannot register geofences", e)
            return
        }
        try {
            client.removeGeofences(pendingIntent).addOnCompleteListener {
                if (geofences.isEmpty()) {
                    Log.i(TAG, "No geofences to register")
                    return@addOnCompleteListener
                }
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(0)
                    .addGeofences(geofences)
                    .build()
                client.addGeofences(request, pendingIntent)
                    .addOnSuccessListener {
                        Log.i(TAG, "Registered ${geofences.size} geofence(s)")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to register geofences", e)
                    }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing location permission for geofences", e)
        } catch (e: Exception) {
            Log.w(TAG, "Geofence sync failed", e)
        }
    }

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutomationReceiver::class.java)
            .setAction(AutomationReceiver.ACTION_GEOFENCE)
        // Geofencing requires a mutable PendingIntent: the system fills in the event.
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
