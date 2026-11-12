package com.thor.routineflow.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.thor.routineflow.R
import com.thor.routineflow.RoutineFlowApp
import com.thor.routineflow.actions.AndroidActionExecutor
import com.thor.routineflow.admin.ModeApplier
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.engine.ActionResult
import com.thor.routineflow.engine.ExecutionContext
import com.thor.routineflow.engine.RoutineRunner
import com.thor.routineflow.engine.RuleEngine
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.RunLog
import com.thor.routineflow.model.RunStatus
import com.thor.routineflow.model.TriggerEvent
import com.thor.routineflow.model.TriggerSnapshot
import com.thor.routineflow.triggers.AppUsageMonitor
import com.thor.routineflow.triggers.CallStateMonitor
import com.thor.routineflow.triggers.ClipboardMonitor
import com.thor.routineflow.triggers.ConnectivityMonitor
import com.thor.routineflow.triggers.GeofenceManager
import com.thor.routineflow.triggers.SnapshotBuilder
import com.thor.routineflow.triggers.TimeTriggerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

/**
 * Foreground-service orchestrator (specialUse). Owns the monitors, the
 * recent-events ring buffer, routine evaluation and Mode auto-triggers.
 *
 * Honest limits: exact alarms use `setExactAndAllowWhileIdle`, which Doze can
 * still defer by a few minutes in deep idle, and geofence delivery is
 * approximate. Nothing here claims exact timing.
 *
 * All work runs on a service-scoped coroutine scope; [onStartCommand] only
 * routes intents.
 */
class EngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val evaluateMutex = Mutex()

    private lateinit var container: AppContainer
    private lateinit var executor: AndroidActionExecutor
    private lateinit var runner: RoutineRunner

    private var connectivityMonitor: ConnectivityMonitor? = null
    private var clipboardMonitor: ClipboardMonitor? = null
    private var callStateMonitor: CallStateMonitor? = null
    private var usagePollJob: Job? = null
    private var started = false

    /** Recent events, kept for 5 minutes; the engine matches event triggers against them. */
    private val recentEvents = ArrayDeque<TriggerEvent>()

    /** Last automatic run per routine, for the cooldown. */
    private val lastAutoRun = mutableMapOf<String, Long>()

    /** Routines currently executing — also the loop guard for RUN_ROUTINE chains. */
    private val runningIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureStarted()
        when (intent?.action) {
            ACTION_EVALUATE -> scope.launch { evaluate() }
            ACTION_EXTERNAL_EVENT -> parseEvent(intent)?.let { postEvent(it) }
            ACTION_RUN_ROUTINE -> {
                val routineId = intent.getStringExtra(EXTRA_ROUTINE_ID)
                if (routineId != null) {
                    scope.launch { executeRoutineById(routineId, bypassCooldown = true) }
                }
            }
            ACTION_STOP -> stopSelf()
            // ACTION_START (and null): just ensure the service is up and evaluate once.
            else -> scope.launch { evaluate() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectivityMonitor?.unregister()
        clipboardMonitor?.unregister()
        callStateMonitor?.unregister()
        usagePollJob?.cancel()
        if (::executor.isInitialized) executor.shutdown()
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }

    // -- Startup -------------------------------------------------------------------

    private fun ensureStarted() {
        if (started) return
        started = true
        container = (application as RoutineFlowApp).container
        executor = AndroidActionExecutor(applicationContext, container)
        runner = RoutineRunner(executor)

        connectivityMonitor = ConnectivityMonitor(
            applicationContext,
            onEvent = { postEvent(it) },
            onEvaluate = { scope.launch { evaluate() } }
        ).also { it.register() }
        clipboardMonitor = ClipboardMonitor(applicationContext) { postEvent(it) }
            .also { it.register() }
        callStateMonitor = CallStateMonitor(applicationContext) { postEvent(it) }
            .also { it.register() }

        usagePollJob = scope.launch {
            while (isActive) {
                delay(USAGE_POLL_INTERVAL_MS)
                val events = runCatching {
                    AppUsageMonitor.pollOnce(applicationContext)
                }.getOrDefault(emptyList())
                for (event in events) postEvent(event)
            }
        }

        // Re-arm everything this service owns, then evaluate once.
        scope.launch {
            val routines = runCatching { container.routineRepository.getAll() }
                .getOrDefault(emptyList())
            TimeTriggerScheduler.rescheduleAll(applicationContext, routines)
            GeofenceManager.syncGeofences(applicationContext, routines)
            evaluate()
        }
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            android.app.PendingIntent.getActivity(
                this, 0, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, RoutineFlowApp.CHANNEL_ENGINE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RoutineFlow is running")
            .setContentText("Watching for your triggers")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    // -- Events & evaluation -------------------------------------------------------

    private fun postEvent(event: TriggerEvent) {
        scope.launch {
            synchronized(recentEvents) {
                recentEvents.addLast(event)
                pruneEventsLocked(System.currentTimeMillis())
            }
            evaluate()
        }
    }

    private fun pruneEventsLocked(now: Long) {
        while (recentEvents.isNotEmpty() && now - recentEvents.first().at > EVENT_RETENTION_MS) {
            recentEvents.removeFirst()
        }
    }

    private suspend fun evaluate() {
        evaluateMutex.withLock {
            if (!::container.isInitialized) return
            val now = System.currentTimeMillis()
            val snapshot = SnapshotBuilder.build(applicationContext)
            val events = synchronized(recentEvents) {
                pruneEventsLocked(now)
                recentEvents.toList()
            }
            val routines = runCatching { container.routineRepository.getEnabled() }
                .getOrDefault(emptyList())
            for (routine in routines) {
                if (!RuleEngine.routineMatches(routine, snapshot, events, now)) continue
                val lastRun = lastAutoRun[routine.id] ?: routine.lastRunAt ?: 0L
                if (now - lastRun < RuleEngine.ROUTINE_COOLDOWN_MS) continue
                lastAutoRun[routine.id] = now
                scope.launch { executeRoutine(routine, snapshot) }
            }
            evaluateModes(snapshot, events, now)
        }
    }

    private suspend fun evaluateModes(
        snapshot: TriggerSnapshot,
        events: List<TriggerEvent>,
        now: Long
    ) {
        val modes = runCatching { container.modeRepository.getAll() }.getOrDefault(emptyList())
        for (mode in modes) {
            if (mode.autoTriggers.isEmpty()) continue
            val probe = Routine(name = mode.name, triggers = mode.autoTriggers)
            val matches = RuleEngine.routineMatches(probe, snapshot, events, now)
            when {
                matches && !mode.isActive -> runCatching {
                    ModeApplier.activate(applicationContext, mode, container)
                    Log.i(TAG, "Mode '${mode.name}' auto-activated")
                }.onFailure { Log.w(TAG, "Failed to activate mode '${mode.name}'", it) }
                !matches && mode.isActive -> runCatching {
                    ModeApplier.deactivate(applicationContext, mode, container)
                    Log.i(TAG, "Mode '${mode.name}' auto-deactivated")
                }.onFailure { Log.w(TAG, "Failed to deactivate mode '${mode.name}'", it) }
            }
        }
    }

    // -- Running routines ----------------------------------------------------------

    private suspend fun executeRoutineById(routineId: String, bypassCooldown: Boolean): ActionResult {
        if (!::container.isInitialized) return ActionResult.Failed("Engine is not ready")
        val routine = runCatching { container.routineRepository.getById(routineId) }.getOrNull()
            ?: return ActionResult.Failed("Routine not found")
        if (!routine.enabled) return ActionResult.Failed("Routine '${routine.name}' is disabled")
        if (!bypassCooldown) {
            val now = System.currentTimeMillis()
            val lastRun = lastAutoRun[routine.id] ?: routine.lastRunAt ?: 0L
            if (now - lastRun < RuleEngine.ROUTINE_COOLDOWN_MS) {
                return ActionResult.Skipped("Routine '${routine.name}' ran recently — cooling down")
            }
            lastAutoRun[routine.id] = now
        }
        val snapshot = SnapshotBuilder.build(applicationContext)
        executeRoutine(routine, snapshot)
        return ActionResult.Success("Ran '${routine.name}'")
    }

    private suspend fun executeRoutine(routine: Routine, snapshot: TriggerSnapshot) {
        if (!runningIds.add(routine.id)) {
            Log.i(TAG, "Routine '${routine.name}' is already running; skipping re-entry")
            return
        }
        try {
            val startedAt = System.currentTimeMillis()
            val executionContext = ExecutionContext(
                routine = routine,
                snapshot = snapshot,
                runRoutine = { id -> executeRoutineById(id, bypassCooldown = true) },
                setRoutineEnabled = { id, enabled -> setRoutineEnabled(id, enabled) }
            )
            val outcome = runner.run(routine, executionContext)
            val finishedAt = System.currentTimeMillis()
            val status = when {
                outcome.failed == 0 && outcome.succeeded > 0 -> RunStatus.SUCCESS
                outcome.succeeded > 0 -> RunStatus.PARTIAL
                outcome.failed == 0 -> RunStatus.SKIPPED
                else -> RunStatus.FAILED
            }
            val detail = outcome.messages.joinToString("\n").take(2000)
            runCatching {
                container.runLogRepository.add(
                    RunLog(
                        routineId = routine.id,
                        routineName = routine.name,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        status = status.name,
                        detail = detail
                    )
                )
                container.routineRepository.recordRun(routine.id, startedAt)
            }.onFailure { Log.w(TAG, "Failed to record run for '${routine.name}'", it) }
            Log.i(
                TAG,
                "Routine '${routine.name}' finished: $status " +
                    "(${outcome.succeeded} ok, ${outcome.failed} failed, ${outcome.skipped} skipped)"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Routine '${routine.name}' crashed", e)
        } finally {
            runningIds.remove(routine.id)
        }
    }

    private suspend fun setRoutineEnabled(routineId: String, enabled: Boolean): ActionResult {
        return runCatching {
            container.routineRepository.setEnabled(routineId, enabled)
            // Enabling/disabling changes which alarms and geofences must exist.
            val routines = container.routineRepository.getAll()
            TimeTriggerScheduler.rescheduleAll(applicationContext, routines)
            GeofenceManager.syncGeofences(applicationContext, routines)
            ActionResult.Success("Routine ${if (enabled) "enabled" else "disabled"}")
        }.getOrElse { ActionResult.Failed(it.message ?: "Could not change the routine") }
    }

    // -- Intent plumbing -----------------------------------------------------------

    private fun parseEvent(intent: Intent): TriggerEvent? {
        val type = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: return null
        val at = intent.getLongExtra(EXTRA_EVENT_AT, System.currentTimeMillis())
        val keys = intent.getStringArrayListExtra(EXTRA_EVENT_KEYS).orEmpty()
        val values = intent.getStringArrayListExtra(EXTRA_EVENT_VALUES).orEmpty()
        val data = keys.zip(values).toMap()
        return TriggerEvent(type = type, at = at, data = data)
    }

    companion object {
        private const val TAG = "EngineService"
        private const val NOTIFICATION_ID = 1001

        /** Recent events are kept for 5 minutes. */
        private const val EVENT_RETENTION_MS = 5 * 60_000L

        /** How often the foreground-app poll runs. */
        private const val USAGE_POLL_INTERVAL_MS = 3_000L

        private const val ACTION_START = "com.thor.routineflow.engine.START"
        private const val ACTION_STOP = "com.thor.routineflow.engine.STOP"
        private const val ACTION_EVALUATE = "com.thor.routineflow.engine.EVALUATE"
        private const val ACTION_EXTERNAL_EVENT = "com.thor.routineflow.engine.EXTERNAL_EVENT"
        private const val ACTION_RUN_ROUTINE = "com.thor.routineflow.engine.RUN_ROUTINE"

        private const val EXTRA_EVENT_TYPE = "event_type"
        private const val EXTRA_EVENT_AT = "event_at"
        private const val EXTRA_EVENT_KEYS = "event_data_keys"
        private const val EXTRA_EVENT_VALUES = "event_data_values"
        private const val EXTRA_ROUTINE_ID = "routine_id"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, EngineService::class.java).setAction(ACTION_START)
            startForegroundSafe(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, EngineService::class.java).setAction(ACTION_STOP)
                )
            }.onFailure { Log.w(TAG, "Failed to stop engine", it) }
        }

        fun evaluateNow(context: Context) {
            if (isRunning) {
                runCatching {
                    context.startService(
                        Intent(context, EngineService::class.java).setAction(ACTION_EVALUATE)
                    )
                }.onFailure { Log.w(TAG, "Failed to request evaluation", it) }
            } else {
                start(context)
            }
        }

        fun onExternalEvent(context: Context, event: TriggerEvent) {
            val intent = Intent(context, EngineService::class.java)
                .setAction(ACTION_EXTERNAL_EVENT)
                .putExtra(EXTRA_EVENT_TYPE, event.type)
                .putExtra(EXTRA_EVENT_AT, event.at)
                .putStringArrayListExtra(EXTRA_EVENT_KEYS, ArrayList(event.data.keys))
                .putStringArrayListExtra(EXTRA_EVENT_VALUES, ArrayList(event.data.values))
            if (isRunning) {
                runCatching { context.startService(intent) }
                    .onFailure { Log.w(TAG, "Failed to deliver event", it) }
            } else {
                startForegroundSafe(context, intent)
            }
        }

        fun runRoutineNow(context: Context, routineId: String) {
            val intent = Intent(context, EngineService::class.java)
                .setAction(ACTION_RUN_ROUTINE)
                .putExtra(EXTRA_ROUTINE_ID, routineId)
            if (isRunning) {
                runCatching { context.startService(intent) }
                    .onFailure { Log.w(TAG, "Failed to run routine", it) }
            } else {
                startForegroundSafe(context, intent)
            }
        }

        /**
         * Starts the foreground service, tolerating the Android 12+ background
         * start restriction: when the system refuses (e.g. right after boot),
         * the watchdog retries later and opening the app starts it directly.
         */
        private fun startForegroundSafe(context: Context, intent: Intent) {
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { e ->
                Log.w(TAG, "Could not start engine service in the background", e)
            }
        }
    }
}
