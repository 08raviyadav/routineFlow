package com.thor.routineflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.thor.routineflow.R
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.service.EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget: shows the favourite routine's name and runs it on one
 * tap. When no favourite is set the title reads "RoutineFlow" and tapping
 * opens the app instead.
 *
 * The layout (`R.layout.widget_routine`, ids `widget_root`, `widget_title`,
 * `widget_run_button`) is owned by the resources layer.
 */
class RoutineWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action != ACTION_WIDGET_RUN) return
        scope.launch {
            val container = (context.applicationContext as RoutineFlowApp).container
            val favouriteId = runCatching { container.preferences.getFavouriteRoutineId() }
                .getOrNull()
            if (favouriteId != null) {
                EngineService.runRoutineNow(context, favouriteId)
            } else {
                val launch = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launch != null) context.startActivity(launch)
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_RUN = "com.thor.routineflow.ACTION_WIDGET_RUN"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RoutineWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            scope.launch {
                val container = (context.applicationContext as RoutineFlowApp).container
                val favouriteId = runCatching { container.preferences.getFavouriteRoutineId() }
                    .getOrNull()
                val routine = favouriteId?.let {
                    runCatching { container.routineRepository.getById(it) }.getOrNull()
                }
                val title = routine?.name ?: "RoutineFlow"
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_routine)
                    views.setTextViewText(R.id.widget_title, title)
                    val runIntent = Intent(context, RoutineWidgetProvider::class.java)
                        .setAction(ACTION_WIDGET_RUN)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, 0, runIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_run_button, pendingIntent)
                    manager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
