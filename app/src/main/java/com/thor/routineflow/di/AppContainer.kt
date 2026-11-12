package com.thor.routineflow.di

import android.content.Context
import com.thor.routineflow.data.ModeRepository
import com.thor.routineflow.data.PreferencesManager
import com.thor.routineflow.data.RoutineFlowDatabase
import com.thor.routineflow.data.RoutineRepository
import com.thor.routineflow.data.RunLogRepository

/**
 * Manual dependency container (no DI framework in v1 — keeps the build simple
 * and the graph obvious). Created once by [com.thor.routineflow.RoutineFlowApp]
 * and shared by the UI, the engine service and the workers.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: RoutineFlowDatabase = RoutineFlowDatabase.get(appContext)
    val routineRepository = RoutineRepository(database)
    val modeRepository = ModeRepository(database)
    val runLogRepository = RunLogRepository(database)
    val preferences = PreferencesManager(appContext)
}
