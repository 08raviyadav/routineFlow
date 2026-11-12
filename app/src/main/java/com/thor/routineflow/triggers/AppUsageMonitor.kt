package com.thor.routineflow.triggers

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.thor.routineflow.model.TriggerEvent
import com.thor.routineflow.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Foreground-app tracking via UsageStats. The engine polls [pollOnce] every
 * few seconds; each poll returns the APP_OPENED / APP_CLOSED events since the
 * previous poll, and updates [lastForegroundPackage] for the snapshot.
 *
 * Returns an empty list when usage access is not granted — the app-opened and
 * app-closed triggers simply never match until the user grants it.
 */
object AppUsageMonitor {

    private const val TAG = "AppUsageMonitor"

    @Volatile
    var lastForegroundPackage: String? = null
        private set

    @Volatile
    private var lastPollEnd = 0L

    suspend fun pollOnce(context: Context): List<TriggerEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<TriggerEvent>()
        if (!Permissions.hasUsageAccess(context)) return@withContext events
        val usageStats = context.getSystemService(UsageStatsManager::class.java)
            ?: return@withContext events

        val now = System.currentTimeMillis()
        // First poll: no backlog — only report changes from now on.
        val begin = if (lastPollEnd == 0L) now else lastPollEnd
        lastPollEnd = now
        if (begin >= now) return@withContext events

        try {
            val usageEvents = usageStats.queryEvents(begin, now)
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val packageName = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastForegroundPackage = packageName
                        events += TriggerEvent(
                            type = "app_opened",
                            at = event.timeStamp,
                            data = mapOf("packageName" to packageName)
                        )
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (lastForegroundPackage == packageName) lastForegroundPackage = null
                        events += TriggerEvent(
                            type = "app_closed",
                            at = event.timeStamp,
                            data = mapOf("packageName" to packageName)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Usage stats query failed", e)
        }
        events
    }
}
