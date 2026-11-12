package com.thor.routineflow.engine

import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.ActionType
import com.thor.routineflow.model.ConditionConfig
import com.thor.routineflow.model.ConditionType
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.TriggerConfig
import com.thor.routineflow.model.TriggerEvent
import com.thor.routineflow.model.TriggerSnapshot
import com.thor.routineflow.model.TriggerType
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Pure-Kotlin rule engine. No Android imports — unit-testable on the JVM.
 *
 * Matching semantics (Samsung-style AND):
 * - Every trigger in a routine must match for the routine to fire.
 * - Event triggers match if a matching event arrived within [EVENT_WINDOW_MS].
 * - State triggers match against the live [TriggerSnapshot].
 * - Every routine-level condition must also hold at fire time.
 *
 * The platform layer ([com.thor.routineflow.service.EngineService]) owns cooldowns
 * (a routine does not re-fire within 5 minutes of its last run) and re-evaluation.
 */
object RuleEngine {

    /** How long an event trigger stays "fresh" for AND matching. */
    const val EVENT_WINDOW_MS: Long = 90_000L

    /** Minimum gap between two automatic runs of the same routine. */
    const val ROUTINE_COOLDOWN_MS: Long = 5 * 60_000L

    fun routineMatches(
        routine: Routine,
        snapshot: TriggerSnapshot,
        recentEvents: List<TriggerEvent>,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!routine.enabled || routine.triggers.isEmpty()) return false
        if (routine.triggers.any { !triggerMatches(it, snapshot, recentEvents, now) }) return false
        if (routine.conditions.any { !conditionHolds(it, snapshot, now) }) return false
        return true
    }

    fun triggerMatches(
        trigger: TriggerConfig,
        snapshot: TriggerSnapshot,
        recentEvents: List<TriggerEvent>,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val type = TriggerType.fromKey(trigger.type) ?: return false
        val p = trigger.params
        return when (type) {
            // -- Event triggers: need a fresh, matching event. --
            TriggerType.TIME_OF_DAY,
            TriggerType.ALARM,
            TriggerType.SUNRISE,
            TriggerType.SUNSET,
            TriggerType.LOCATION_ARRIVE,
            TriggerType.LOCATION_LEAVE,
            TriggerType.APP_OPENED,
            TriggerType.APP_CLOSED,
            TriggerType.SCREEN_ON,
            TriggerType.SCREEN_OFF,
            TriggerType.UNLOCKED,
            TriggerType.CALL_STATE,
            TriggerType.CLIPBOARD_CHANGED -> {
                recentEvents.any { event ->
                    event.type == trigger.type &&
                        now - event.at <= EVENT_WINDOW_MS &&
                        eventMatchesParams(trigger, event)
                }
            }

            TriggerType.NOTIFICATION -> {
                recentEvents.any { event ->
                    event.type == trigger.type &&
                        now - event.at <= EVENT_WINDOW_MS &&
                        eventMatchesParams(trigger, event)
                } || run {
                    // Fallback: snapshot's last notification (covers events delivered
                    // through the snapshot path instead of the event bus).
                    val at = snapshot.lastNotificationAt ?: return@run false
                    if (now - at > EVENT_WINDOW_MS) return@run false
                    val pkg = p["packageName"].orEmpty()
                    if (pkg.isNotEmpty() && snapshot.lastNotificationPackage != pkg) return@run false
                    val text = p["textContains"].orEmpty()
                    if (text.isNotEmpty() &&
                        !(snapshot.lastNotificationText.orEmpty().contains(text, ignoreCase = true))
                    ) return@run false
                    true
                }
            }

            // -- State triggers: evaluated against the snapshot. --
            TriggerType.BATTERY_LEVEL -> {
                val level = snapshot.batteryPercent ?: return false
                val percent = p["percent"]?.toIntOrNull() ?: 20
                when (p["direction"] ?: "below") {
                    "above" -> level >= percent
                    else -> level <= percent
                }
            }
            TriggerType.CHARGING -> {
                val charging = snapshot.isCharging ?: return false
                (p["state"] ?: "charging") == "charging" == charging
            }
            TriggerType.WIFI_CONNECTED -> {
                val ssid = snapshot.wifiSsid ?: return false
                val wanted = p["ssid"].orEmpty()
                wanted.isEmpty() || ssid == wanted
            }
            TriggerType.WIFI_DISCONNECTED -> snapshot.wifiSsid == null
            TriggerType.BLUETOOTH_CONNECTED -> {
                val name = snapshot.bluetoothDeviceName ?: return false
                val wanted = p["deviceName"].orEmpty()
                wanted.isEmpty() || name.contains(wanted, ignoreCase = true)
            }
            TriggerType.BLUETOOTH_DISCONNECTED -> snapshot.bluetoothDeviceName == null
            TriggerType.HEADPHONES -> {
                val connected = snapshot.isHeadphonesConnected ?: return false
                (p["state"] ?: "connected") == "connected" == connected
            }
            TriggerType.AIRPLANE_MODE -> {
                val on = snapshot.isAirplaneMode ?: return false
                (p["state"] ?: "on") == "on" == on
            }
            TriggerType.DND_CHANGED -> {
                val on = snapshot.isDndOn ?: return false
                (p["state"] ?: "on") == "on" == on
            }
        }
    }

    /** Checks an event's data against a trigger's params (package, text, state…). */
    private fun eventMatchesParams(trigger: TriggerConfig, event: TriggerEvent): Boolean {
        val p = trigger.params
        val d = event.data
        // Package-scoped triggers.
        p["packageName"]?.takeIf { it.isNotEmpty() }?.let {
            if (d["packageName"] != it) return false
        }
        // Text-contains triggers (notification / clipboard).
        p["textContains"]?.takeIf { it.isNotEmpty() }?.let {
            if (!(d["text"].orEmpty().contains(it, ignoreCase = true))) return false
        }
        // State-carrying events (call state).
        p["state"]?.takeIf { it.isNotEmpty() }?.let {
            if (d["state"] != null && d["state"] != it) return false
        }
        // Named geofence triggers.
        p["name"]?.takeIf { it.isNotEmpty() }?.let {
            if (d["name"] != null && d["name"] != it) return false
        }
        return true
    }

    fun conditionHolds(
        condition: ConditionConfig,
        snapshot: TriggerSnapshot,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val type = ConditionType.fromKey(condition.type) ?: return false
        val p = condition.params
        return when (type) {
            ConditionType.BATTERY_BELOW ->
                (snapshot.batteryPercent ?: return false) <= (p["percent"]?.toIntOrNull() ?: 20)
            ConditionType.BATTERY_ABOVE ->
                (snapshot.batteryPercent ?: return false) >= (p["percent"]?.toIntOrNull() ?: 80)
            ConditionType.WIFI_CONNECTED -> snapshot.wifiSsid != null
            ConditionType.WIFI_DISCONNECTED -> snapshot.wifiSsid == null
            ConditionType.IS_CHARGING -> snapshot.isCharging == true
            ConditionType.TIME_BETWEEN -> {
                val start = parseTime(p["startTime"] ?: "22:00")
                val end = parseTime(p["endTime"] ?: "07:00")
                val current = minutesOfDay(now)
                if (start <= end) current in start..end else current >= start || current <= end
            }
            ConditionType.DAY_IS -> {
                val days = parseDays(p["days"].orEmpty())
                if (days.isEmpty()) true else dayOfWeek(now) in days
            }
            ConditionType.DND_ON -> snapshot.isDndOn == true
            ConditionType.DND_OFF -> snapshot.isDndOn == false
            ConditionType.SCREEN_ON -> snapshot.isScreenOn == true
            ConditionType.APP_FOREGROUND ->
                snapshot.foregroundPackage == p["packageName"]
            ConditionType.HEADPHONES_CONNECTED -> snapshot.isHeadphonesConnected == true
            ConditionType.BLUETOOTH_CONNECTED -> snapshot.bluetoothDeviceName != null
        }
    }

    /** "HH:mm" -> minutes since midnight. */
    fun parseTime(value: String): Int {
        val parts = value.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59))
    }

    /** "1,2,3" (Mon=1..Sun=7, ISO) -> set. Empty = every day. */
    fun parseDays(value: String): Set<Int> =
        value.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    fun dayOfWeek(now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        // Calendar: Sunday=1..Saturday=7. Convert to ISO: Monday=1..Sunday=7.
        return ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
    }

    fun minutesOfDay(now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** "lat, lng" -> Pair, or null when unparsable. */
    fun parseLocation(value: String): Pair<Double, Double>? {
        val parts = value.split(",").map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }
}

// ---------------------------------------------------------------------------
// Action execution — the engine owns control flow (IF/ELSE/END_IF, DELAY,
// variable substitution); leaf actions run through [ActionExecutor], which the
// platform layer implements with real Android APIs.
// ---------------------------------------------------------------------------

sealed interface ActionResult {
    data class Success(val message: String = "Done") : ActionResult
    data class Failed(val reason: String) : ActionResult
    data class Skipped(val reason: String) : ActionResult
}

data class ExecutionContext(
    val routine: Routine,
    val variables: MutableMap<String, String> = mutableMapOf(),
    val snapshot: TriggerSnapshot,
    /** Runs another routine by id (used by RUN_ROUTINE). */
    val runRoutine: suspend (routineId: String) -> ActionResult,
    /** Enables/disables another routine (used by SET_ROUTINE_ENABLED). */
    val setRoutineEnabled: suspend (routineId: String, enabled: Boolean) -> ActionResult
) {
    /** Replaces ${battery}, ${time} and ${name} placeholders in action text. */
    fun substitute(text: String): String {
        var out = text
        out = out.replace("\${battery}", (snapshot.batteryPercent?.toString() ?: "?") + "%")
        val cal = Calendar.getInstance().apply { timeInMillis = snapshot.now }
        val time = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        out = out.replace("\${time}", time)
        out = out.replace("\${routine}", routine.name)
        for ((key, value) in variables) out = out.replace("\${$key}", value)
        return out
    }
}

interface ActionExecutor {
    suspend fun execute(action: ActionConfig, context: ExecutionContext): ActionResult
}

data class RoutineRunOutcome(
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val messages: List<String>
) {
    val allSucceeded: Boolean get() = failed == 0
}

/**
 * Executes a routine's action list, honouring IF/ELSE/END_IF blocks and DELAY.
 * IF blocks may nest; a malformed list (ELSE without IF) fails that step only.
 */
class RoutineRunner(private val executor: ActionExecutor) {

    suspend fun run(routine: Routine, context: ExecutionContext): RoutineRunOutcome {
        var succeeded = 0
        var failed = 0
        var skipped = 0
        val messages = mutableListOf<String>()

        // Stack of frames: each frame tracks whether its branch is currently active.
        data class Frame(val conditionHeld: Boolean, var inElse: Boolean)
        val stack = ArrayDeque<Frame>()
        fun isActive(): Boolean = stack.all { frame ->
            if (!frame.inElse) frame.conditionHeld else !frame.conditionHeld
        }

        for (action in routine.actions) {
            val type = ActionType.fromKey(action.type)
            when (action.type) {
                "if" -> {
                    val condition = ConditionConfig(
                        type = action.params["conditionType"] ?: "battery_below",
                        params = mapOf(
                            "percent" to (action.params["c_percent"] ?: "20"),
                            "ssid" to (action.params["c_ssid"] ?: ""),
                            "startTime" to (action.params["c_startTime"] ?: "22:00"),
                            "endTime" to (action.params["c_endTime"] ?: "07:00"),
                            "days" to (action.params["c_days"] ?: ""),
                            "packageName" to (action.params["c_packageName"] ?: "")
                        )
                    )
                    // Battery direction: IF params carry c_direction for battery conditions.
                    val held = when (condition.type) {
                        "battery_below", "battery_above" -> {
                            val dir = action.params["c_direction"] ?: "below"
                            val level = context.snapshot.batteryPercent
                            if (level == null) false
                            else if (dir == "above") level >= (condition.params["percent"]?.toIntOrNull() ?: 20)
                            else level <= (condition.params["percent"]?.toIntOrNull() ?: 20)
                        }
                        else -> RuleEngine.conditionHolds(condition, context.snapshot)
                    }
                    stack.addLast(Frame(conditionHeld = held, inElse = false))
                }
                "else" -> {
                    val frame = stack.lastOrNull()
                    if (frame == null) {
                        failed++
                        messages += "Else without If — skipped."
                    } else {
                        frame.inElse = true
                    }
                }
                "end_if" -> {
                    if (stack.isEmpty()) {
                        failed++
                        messages += "End if without If — skipped."
                    } else {
                        stack.removeLast()
                    }
                }
                "delay" -> {
                    if (!isActive()) {
                        skipped++
                    } else {
                        val seconds = (action.params["seconds"]?.toIntOrNull() ?: 5)
                            .coerceIn(0, 300)
                        delay(seconds * 1000L)
                        succeeded++
                    }
                }
                else -> {
                    if (!isActive()) {
                        skipped++
                        continue
                    }
                    if (type == null) {
                        failed++
                        messages += "Unknown action '${action.type}' — skipped."
                        continue
                    }
                    when (val result = executor.execute(action, context)) {
                        is ActionResult.Success -> {
                            succeeded++
                            messages += "${type.title}: ${result.message}"
                        }
                        is ActionResult.Failed -> {
                            failed++
                            messages += "${type.title} failed: ${result.reason}"
                        }
                        is ActionResult.Skipped -> {
                            skipped++
                            messages += "${type.title} skipped: ${result.reason}"
                        }
                    }
                }
            }
        }
        if (stack.isNotEmpty()) {
            failed++
            messages += "Unclosed If block — check the routine."
        }
        return RoutineRunOutcome(succeeded, failed, skipped, messages)
    }
}
