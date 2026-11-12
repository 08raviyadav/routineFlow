package com.thor.routineflow.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.triggers.GeofenceManager
import com.thor.routineflow.triggers.TimeTriggerScheduler
import java.util.concurrent.TimeUnit

/**
 * Periodic self-healing (every 15 minutes, the shortest WorkManager
 * interval): re-arms time alarms and geofences, restarts the engine when it
 * is enabled but not running, prunes run logs older than 30 days, and records
 * the last run for the diagnostics screen.
 *
 * WorkManager timing is inexact by design (Doze, battery savers) — this is a
 * safety net, not a timing guarantee.
 */
class WatchdogWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? RoutineFlowApp
            val container = app?.container ?: AppContainer(applicationContext)
            val routines = runCatching { container.routineRepository.getAll() }
                .getOrDefault(emptyList())
            TimeTriggerScheduler.rescheduleAll(applicationContext, routines)
            GeofenceManager.syncGeofences(applicationContext, routines)

            val engineEnabled = runCatching { container.preferences.isEngineEnabled() }
                .getOrDefault(false)
            if (engineEnabled && !EngineService.isRunning) {
                runCatching { EngineService.start(applicationContext) }
                    .onFailure { Log.w(TAG, "Watchdog could not restart the engine", it) }
            }

            container.runLogRepository.pruneOld()
            container.preferences.setLastWatchdogRun(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog run failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WatchdogWorker"
        private const val UNIQUE_WORK_NAME = "routineflow_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { Log.w(TAG, "Failed to schedule watchdog", it) }
        }
    }
}
