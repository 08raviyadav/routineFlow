package com.thor.routineflow.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.thor.routineflow.engine.RuleEngine
import com.thor.routineflow.engine.SunCalculator
import com.thor.routineflow.model.Routine
import java.util.Calendar

/**
 * Schedules the clock-based triggers (time of day, RoutineFlow alarm,
 * sunrise/sunset) with AlarmManager.
 *
 * - TIME_OF_DAY: `setExactAndAllowWhileIdle` — fires on time even in Doze,
 *   but only when the user has granted the exact-alarm permission (Android 12+).
 * - ALARM: `setAlarmClock` — shows in the status bar and fires in Doze; it
 *   does not need the exact-alarm permission.
 * - SUNRISE/SUNSET: exact alarm at the computed event (SunCalculator), today
 *   or tomorrow, plus the configured offset.
 *
 * Every scheduled alarm re-arms itself: when it fires, [AutomationReceiver]
 * calls [rescheduleAll] again, so the next occurrence is always queued.
 * Scheduled request codes are persisted so [cancelAll] can remove them.
 */
object TimeTriggerScheduler {

    private const val TAG = "TimeTriggerScheduler"
    private const val PREFS_NAME = "routineflow_alarms"
    private const val KEY_CODES = "scheduled_codes"

    fun rescheduleAll(context: Context, routines: List<Routine>) {
        cancelAll(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val canScheduleExact = Build.VERSION.SDK_INT < 31 ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        val scheduled = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        for (routine in routines) {
            if (!routine.enabled) continue
            for ((index, trigger) in routine.triggers.withIndex()) {
                when (trigger.type) {
                    "time_of_day" -> {
                        if (!canScheduleExact) {
                            Log.w(TAG, "Exact alarms not granted; skipping time trigger for '${routine.name}'")
                            continue
                        }
                        val minutes = RuleEngine.parseTime(trigger.params["time"] ?: "08:00")
                        val days = RuleEngine.parseDays(trigger.params["days"].orEmpty())
                        val at = nextTimeOfDay(minutes, days, now)
                        scheduleExact(
                            context, alarmManager, AutomationReceiver.ACTION_TIME_TRIGGER,
                            routine, index, trigger.type, at, scheduled
                        )
                    }
                    "alarm" -> {
                        val minutes = RuleEngine.parseTime(trigger.params["time"] ?: "07:00")
                        val days = RuleEngine.parseDays(trigger.params["days"].orEmpty())
                        val at = nextTimeOfDay(minutes, days, now)
                        scheduleAlarmClock(context, alarmManager, routine, index, at, scheduled)
                    }
                    "sunrise", "sunset" -> {
                        if (!canScheduleExact) {
                            Log.w(TAG, "Exact alarms not granted; skipping sun trigger for '${routine.name}'")
                            continue
                        }
                        val location = RuleEngine.parseLocation(trigger.params["location"].orEmpty())
                        if (location == null) {
                            Log.w(TAG, "No location saved for sun trigger in '${routine.name}'; skipping")
                            continue
                        }
                        val offsetMinutes = trigger.params["offsetMinutes"]?.toIntOrNull() ?: 0
                        val at = nextSunEvent(
                            location, trigger.type == "sunrise", offsetMinutes, now
                        )
                        if (at == null) {
                            Log.w(TAG, "Sun does not rise/set for '${routine.name}' location; skipping")
                            continue
                        }
                        scheduleExact(
                            context, alarmManager, AutomationReceiver.ACTION_SUN_TRIGGER,
                            routine, index, trigger.type, at, scheduled
                        )
                    }
                }
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CODES, scheduled)
            .apply()
        Log.i(TAG, "Scheduled ${scheduled.size} time trigger alarm(s)")
    }

    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        if (codes.isEmpty()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (entry in codes) {
            val parts = entry.split("|")
            val requestCode = parts.getOrNull(0)?.toIntOrNull() ?: continue
            val action = parts.getOrNull(1) ?: continue
            val intent = Intent(context, AutomationReceiver::class.java).setAction(action)
            val pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: continue
            runCatching { alarmManager?.cancel(pending) }
            runCatching { pending.cancel() }
        }
        prefs.edit().remove(KEY_CODES).apply()
    }

    private fun scheduleExact(
        context: Context,
        alarmManager: AlarmManager,
        action: String,
        routine: Routine,
        triggerIndex: Int,
        triggerType: String,
        atMillis: Long,
        scheduled: MutableSet<String>
    ) {
        val requestCode = requestCodeFor(routine.id, triggerIndex, triggerType)
        val intent = Intent(context, AutomationReceiver::class.java)
            .setAction(action)
            .putExtra(AutomationReceiver.EXTRA_TRIGGER_TYPE, triggerType)
            .putExtra(AutomationReceiver.EXTRA_ROUTINE_ID, routine.id)
            .putExtra(AutomationReceiver.EXTRA_TRIGGER_INDEX, triggerIndex)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            scheduled.add("$requestCode|$action")
        }.onFailure { Log.w(TAG, "Failed to schedule $triggerType for '${routine.name}'", it) }
    }

    private fun scheduleAlarmClock(
        context: Context,
        alarmManager: AlarmManager,
        routine: Routine,
        triggerIndex: Int,
        atMillis: Long,
        scheduled: MutableSet<String>
    ) {
        val requestCode = requestCodeFor(routine.id, triggerIndex, "alarm")
        val intent = Intent(context, AutomationReceiver::class.java)
            .setAction(AutomationReceiver.ACTION_ALARM_TRIGGER)
            .putExtra(AutomationReceiver.EXTRA_TRIGGER_TYPE, "alarm")
            .putExtra(AutomationReceiver.EXTRA_ROUTINE_ID, routine.id)
            .putExtra(AutomationReceiver.EXTRA_TRIGGER_INDEX, triggerIndex)
        val operation = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.let { launch ->
                PendingIntent.getActivity(
                    context, requestCode, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(atMillis, showIntent),
                operation
            )
            scheduled.add("$requestCode|${AutomationReceiver.ACTION_ALARM_TRIGGER}")
        }.onFailure { Log.w(TAG, "Failed to schedule alarm for '${routine.name}'", it) }
    }

    /** Next wall-clock occurrence of a HH:mm time (minutes since midnight) on an allowed day. */
    private fun nextTimeOfDay(timeMinutes: Int, days: Set<Int>, now: Long): Long {
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, timeMinutes / 60)
                set(Calendar.MINUTE, timeMinutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis <= now) continue
            if (days.isEmpty() || RuleEngine.dayOfWeek(candidate.timeInMillis) in days) {
                return candidate.timeInMillis
            }
        }
        // Unreachable when days is empty; fallback is tomorrow at the same time.
        return Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, timeMinutes / 60)
            set(Calendar.MINUTE, timeMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Today's sunrise/sunset (+ offset), or tomorrow's when today's already passed. Null near the poles. */
    private fun nextSunEvent(
        location: Pair<Double, Double>,
        sunrise: Boolean,
        offsetMinutes: Int,
        now: Long
    ): Long? {
        val offsetMs = offsetMinutes * 60_000L
        val today = SunCalculator.sunTimes(location.first, location.second, now)
        val todayEvent = today?.let { if (sunrise) it.sunriseMillis else it.sunsetMillis }
            ?.plus(offsetMs)
        if (todayEvent != null && todayEvent > now) return todayEvent
        val tomorrow = SunCalculator.sunTimes(
            location.first, location.second, now + 24 * 60 * 60 * 1000L
        )
        return tomorrow?.let { if (sunrise) it.sunriseMillis else it.sunsetMillis }
            ?.plus(offsetMs)
    }

    private fun requestCodeFor(routineId: String, triggerIndex: Int, triggerType: String): Int =
        ("$routineId|$triggerIndex|$triggerType".hashCode() and 0x7FFFFFFF)
}
