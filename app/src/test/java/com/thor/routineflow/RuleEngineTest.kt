package com.thor.routineflow

import com.thor.routineflow.engine.ActionExecutor
import com.thor.routineflow.engine.ActionResult
import com.thor.routineflow.engine.ExecutionContext
import com.thor.routineflow.engine.RoutineRunner
import com.thor.routineflow.engine.RuleEngine
import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.ConditionConfig
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.TriggerConfig
import com.thor.routineflow.model.TriggerEvent
import com.thor.routineflow.model.TriggerSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the pure-Kotlin rule engine ([RuleEngine]) and the action
 * runner ([RoutineRunner]). No Android APIs — runs on the JVM.
 */
class RuleEngineTest {

    private val now = System.currentTimeMillis()

    /** Epoch millis for a fixed local time today (same default time zone the engine uses). */
    private fun timeToday(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun contextFor(routine: Routine, snapshot: TriggerSnapshot) =
        ExecutionContext(
            routine = routine,
            snapshot = snapshot,
            runRoutine = { ActionResult.Success("ran") },
            setRoutineEnabled = { _, _ -> ActionResult.Success("set") }
        )

    // -- (a) Time trigger matches only with a fresh event. --

    @Test
    fun timeTrigger_matchesFreshEvent() {
        val trigger = TriggerConfig("time_of_day", mapOf("time" to "08:00"))
        val events = listOf(TriggerEvent("time_of_day", at = now))
        assertTrue(RuleEngine.triggerMatches(trigger, TriggerSnapshot(), events, now))
    }

    @Test
    fun timeTrigger_doesNotMatchWithoutEvent() {
        val trigger = TriggerConfig("time_of_day", mapOf("time" to "08:00"))
        assertFalse(RuleEngine.triggerMatches(trigger, TriggerSnapshot(), emptyList(), now))
    }

    // -- (b) Stale event (>90s) does not match. --

    @Test
    fun timeTrigger_doesNotMatchStaleEvent() {
        val trigger = TriggerConfig("time_of_day", mapOf("time" to "08:00"))
        val stale = listOf(TriggerEvent("time_of_day", at = now - 91_000L))
        assertFalse(RuleEngine.triggerMatches(trigger, TriggerSnapshot(), stale, now))
    }

    @Test
    fun timeTrigger_matchesEventJustInsideWindow() {
        val trigger = TriggerConfig("time_of_day", mapOf("time" to "08:00"))
        val fresh = listOf(TriggerEvent("time_of_day", at = now - 89_000L))
        assertTrue(RuleEngine.triggerMatches(trigger, TriggerSnapshot(), fresh, now))
    }

    // -- (c) State trigger battery-below matches snapshot. --

    @Test
    fun batteryBelow_matchesSnapshot() {
        val trigger = TriggerConfig(
            "battery_level",
            mapOf("direction" to "below", "percent" to "20")
        )
        assertTrue(
            RuleEngine.triggerMatches(
                trigger, TriggerSnapshot(batteryPercent = 15), emptyList(), now
            )
        )
        assertFalse(
            RuleEngine.triggerMatches(
                trigger, TriggerSnapshot(batteryPercent = 25), emptyList(), now
            )
        )
        // Unknown battery level never matches.
        assertFalse(
            RuleEngine.triggerMatches(
                trigger, TriggerSnapshot(batteryPercent = null), emptyList(), now
            )
        )
    }

    // -- (d) AND semantics: wifi-connected + charging. --

    @Test
    fun routineMatches_requiresAllTriggers() {
        val routine = Routine(
            name = "Home charging",
            triggers = listOf(
                TriggerConfig("wifi_connected", mapOf("ssid" to "")),
                TriggerConfig("charging", mapOf("state" to "charging"))
            )
        )
        val both = TriggerSnapshot(wifiSsid = "Home", isCharging = true)
        assertTrue(RuleEngine.routineMatches(routine, both, emptyList(), now))

        val wifiOnly = TriggerSnapshot(wifiSsid = "Home", isCharging = false)
        assertFalse(RuleEngine.routineMatches(routine, wifiOnly, emptyList(), now))

        val chargingOnly = TriggerSnapshot(wifiSsid = null, isCharging = true)
        assertFalse(RuleEngine.routineMatches(routine, chargingOnly, emptyList(), now))
    }

    @Test
    fun routineMatches_disabledOrTriggerlessRoutineNeverFires() {
        val disabled = Routine(
            name = "Off",
            enabled = false,
            triggers = listOf(TriggerConfig("wifi_connected"))
        )
        assertFalse(
            RuleEngine.routineMatches(
                disabled, TriggerSnapshot(wifiSsid = "Home"), emptyList(), now
            )
        )
        val noTriggers = Routine(name = "Empty", triggers = emptyList())
        assertFalse(
            RuleEngine.routineMatches(
                noTriggers, TriggerSnapshot(wifiSsid = "Home"), emptyList(), now
            )
        )
    }

    // -- (e) Conditions: overnight time-between window. --

    @Test
    fun timeBetween_overnightWindow() {
        val condition = ConditionConfig(
            "time_between",
            mapOf("startTime" to "22:00", "endTime" to "07:00")
        )
        assertTrue(
            RuleEngine.conditionHolds(condition, TriggerSnapshot(), timeToday(23, 30))
        )
        assertTrue(
            RuleEngine.conditionHolds(condition, TriggerSnapshot(), timeToday(3, 0))
        )
        assertFalse(
            RuleEngine.conditionHolds(condition, TriggerSnapshot(), timeToday(12, 0))
        )
        assertFalse(
            RuleEngine.conditionHolds(condition, TriggerSnapshot(), timeToday(8, 0))
        )
    }

    // -- (f) IF/ELSE runner with a recording executor. --

    private class RecordingExecutor : ActionExecutor {
        val executed = mutableListOf<String>()
        override suspend fun execute(
            action: ActionConfig,
            context: ExecutionContext
        ): ActionResult {
            executed += action.type
            return ActionResult.Success("ok")
        }
    }

    private fun ifBatteryRoutine() = Routine(
        name = "Battery branch",
        actions = listOf(
            ActionConfig(
                "if",
                mapOf(
                    "conditionType" to "battery_below",
                    "c_direction" to "below",
                    "c_percent" to "20"
                )
            ),
            ActionConfig("show_notification", mapOf("title" to "Low", "text" to "Battery low")),
            ActionConfig("else"),
            ActionConfig("show_toast", mapOf("text" to "Battery fine")),
            ActionConfig("end_if")
        )
    )

    @Test
    fun ifElse_runsThenBranchWhenBatteryLow() = runTest {
        val executor = RecordingExecutor()
        val routine = ifBatteryRoutine()
        val outcome = RoutineRunner(executor).run(
            routine,
            contextFor(routine, TriggerSnapshot(batteryPercent = 10))
        )
        assertEquals(listOf("show_notification"), executor.executed)
        assertEquals(1, outcome.succeeded)
        assertEquals(1, outcome.skipped) // else-branch action
        assertEquals(0, outcome.failed)
    }

    @Test
    fun ifElse_runsElseBranchWhenBatteryHigh() = runTest {
        val executor = RecordingExecutor()
        val routine = ifBatteryRoutine()
        val outcome = RoutineRunner(executor).run(
            routine,
            contextFor(routine, TriggerSnapshot(batteryPercent = 90))
        )
        assertEquals(listOf("show_toast"), executor.executed)
        assertEquals(1, outcome.succeeded)
        assertEquals(1, outcome.skipped) // then-branch action
        assertEquals(0, outcome.failed)
    }

    // -- (g) DELAY with 0 seconds completes. --

    @Test
    fun delay_zeroSecondsCompletes() = runTest {
        val routine = Routine(
            name = "No wait",
            actions = listOf(ActionConfig("delay", mapOf("seconds" to "0")))
        )
        val outcome = RoutineRunner(RecordingExecutor()).run(
            routine,
            contextFor(routine, TriggerSnapshot())
        )
        assertEquals(1, outcome.succeeded)
        assertEquals(0, outcome.failed)
    }

    // -- (h) Unknown action type is reported failed, not crashing. --

    @Test
    fun unknownAction_isReportedFailed() = runTest {
        val routine = Routine(
            name = "Bogus",
            actions = listOf(
                ActionConfig("not_a_real_action"),
                ActionConfig("show_toast", mapOf("text" to "still runs"))
            )
        )
        val executor = RecordingExecutor()
        val outcome = RoutineRunner(executor).run(
            routine,
            contextFor(routine, TriggerSnapshot())
        )
        assertEquals(1, outcome.failed)
        assertEquals(1, outcome.succeeded)
        assertEquals(listOf("show_toast"), executor.executed)
        assertTrue(outcome.messages.any { it.contains("Unknown action") })
    }

    // -- (i) parseDays / parseTime helpers. --

    @Test
    fun parseTime_parsesHoursAndMinutes() {
        assertEquals(510, RuleEngine.parseTime("08:30"))
        assertEquals(1320, RuleEngine.parseTime("22:00"))
        assertEquals(0, RuleEngine.parseTime("00:00"))
        assertEquals(1439, RuleEngine.parseTime("23:59"))
    }

    @Test
    fun parseTime_badInputDefaultsToZero() {
        assertEquals(0, RuleEngine.parseTime("not-a-time"))
        assertEquals(0, RuleEngine.parseTime(""))
    }

    @Test
    fun parseDays_parsesIsoWeekdays() {
        assertEquals(setOf(1, 2, 3), RuleEngine.parseDays("1,2,3"))
        assertEquals(setOf(6, 7), RuleEngine.parseDays("6,7"))
        assertEquals(emptySet<Int>(), RuleEngine.parseDays(""))
    }

    @Test
    fun parseDays_ignoresInvalidEntries() {
        assertEquals(setOf(1, 5), RuleEngine.parseDays("1, 8, x, 5"))
    }
}
