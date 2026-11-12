package com.thor.routineflow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.thor.routineflow.catalog.RoutineTemplates
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.service.WatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoutineFlowApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        appScope.launch {
            seedBuiltInModesIfNeeded()
            WatchdogWorker.schedule(this@RoutineFlowApp)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val engine = NotificationChannel(
            CHANNEL_ENGINE,
            "RoutineFlow engine",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps your routines running in the background." }
        val routines = NotificationChannel(
            CHANNEL_ROUTINES,
            "Routine notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifications posted by your routines." }
        manager.createNotificationChannel(engine)
        manager.createNotificationChannel(routines)
    }

    /** Seeds the five built-in Modes on first launch (Sleep, Driving, Work, Focus, Relax). */
    private suspend fun seedBuiltInModesIfNeeded() {
        val prefs = container.preferences
        if (prefs.areModesSeeded()) return
        val existing = container.modeRepository.getAll().map { it.id }.toSet()
        for (mode in RoutineTemplates.builtInModes) {
            if (mode.id !in existing) container.modeRepository.save(mode)
        }
        prefs.setModesSeeded()
    }

    companion object {
        const val CHANNEL_ENGINE = "routineflow_engine"
        const val CHANNEL_ROUTINES = "routineflow_routines"
    }
}
