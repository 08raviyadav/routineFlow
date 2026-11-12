package com.thor.routineflow.triggers

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.thor.routineflow.model.TriggerEvent
import com.thor.routineflow.service.EngineService

/**
 * Notification listener: feeds the NOTIFICATION trigger and the snapshot's
 * last-notification fields, and backs the CLEAR_NOTIFICATIONS action and the
 * media play/pause action (both need this service enabled).
 *
 * RoutineFlow's own notifications are ignored so a routine that posts a
 * notification cannot trigger itself.
 */
class RoutineFlowNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == applicationContext.packageName) return
        val extras = notification.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val combined = listOf(title, text).filter { it.isNotEmpty() }.joinToString(" ").take(500)
        lastPackage = notification.packageName
        lastText = combined
        lastAt = notification.postTime
        EngineService.onExternalEvent(
            applicationContext,
            TriggerEvent(
                type = "notification",
                data = mapOf("packageName" to notification.packageName, "text" to combined)
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Nothing to do: removal is not a trigger.
    }

    companion object {
        @Volatile var lastPackage: String? = null
            private set
        @Volatile var lastText: String? = null
            private set
        @Volatile var lastAt: Long? = null
            private set

        @Volatile
        private var instance: RoutineFlowNotificationListener? = null

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                // No public constant exists for this setting key; the support
                // library reads the same raw key.
                "enabled_notification_listeners"
            ).orEmpty()
            val component = ComponentName(context, RoutineFlowNotificationListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == component }
        }

        /** Dismisses all notifications. Returns false when the listener is not running. */
        fun cancelAllNotifications(context: Context): Boolean {
            val service = instance ?: return false
            return runCatching {
                service.cancelAllNotifications()
                true
            }.getOrDefault(false)
        }
    }
}
