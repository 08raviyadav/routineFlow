package com.thor.routineflow.ui.viewmodel

import android.app.AlarmManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.thor.routineflow.admin.ModeApplier
import com.thor.routineflow.di.AppContainer
import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.ActionType
import com.thor.routineflow.model.BackupFile
import com.thor.routineflow.model.ConditionConfig
import com.thor.routineflow.model.ConditionType
import com.thor.routineflow.model.ModeProfile
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.RunLog
import com.thor.routineflow.model.TriggerConfig
import com.thor.routineflow.model.TriggerType
import com.thor.routineflow.service.EngineService
import com.thor.routineflow.triggers.GeofenceManager
import com.thor.routineflow.triggers.TimeTriggerScheduler
import com.thor.routineflow.util.BackupManager
import com.thor.routineflow.util.OemGuide
import com.thor.routineflow.util.Permissions
import com.thor.routineflow.widget.RoutineWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Re-arms alarms, geofences and the widget after any routine change. */
private suspend fun resyncAutomation(container: AppContainer) {
    val context = container.appContext
    val routines = container.routineRepository.getAll()
    TimeTriggerScheduler.rescheduleAll(context, routines)
    GeofenceManager.syncGeofences(context, routines)
    RoutineWidgetProvider.updateAll(context)
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val routines: StateFlow<List<Routine>> = container.routineRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val modes: StateFlow<List<ModeProfile>> = container.modeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val engineEnabled: StateFlow<Boolean> = container.preferences.engineEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setEngineEnabled(enabled)
            if (enabled) EngineService.start(container.appContext)
            else EngineService.stop(container.appContext)
        }
    }

    fun toggleRoutine(id: String, enabled: Boolean) {
        viewModelScope.launch {
            container.routineRepository.setEnabled(id, enabled)
            resyncAutomation(container)
        }
    }

    fun deleteRoutine(id: String) {
        viewModelScope.launch {
            container.routineRepository.delete(id)
            resyncAutomation(container)
        }
    }

    fun runNow(id: String) {
        EngineService.runRoutineNow(container.appContext, id)
    }

    /**
     * Copies a template into the user's routine list (fresh id, enabled,
     * no longer a template) and returns the new routine's id.
     */
    suspend fun useTemplate(template: Routine): String {
        val copy = template.copy(
            id = UUID.randomUUID().toString(),
            isTemplate = false,
            enabled = true,
            runCount = 0,
            lastRunAt = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        container.routineRepository.save(copy)
        resyncAutomation(container)
        return copy.id
    }
}

// ---------------------------------------------------------------------------
// Routine editor
// ---------------------------------------------------------------------------

class EditorViewModel(private val container: AppContainer) : ViewModel() {

    private val _routine = MutableStateFlow<Routine?>(null)
    val routine: StateFlow<Routine?> = _routine.asStateFlow()

    /** All routines, for the ROUTINE param dropdown (run/enable another routine). */
    val allRoutines: StateFlow<List<Routine>> = container.routineRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(id: String) {
        if (id == "new") {
            if (_routine.value == null) newRoutine()
            return
        }
        viewModelScope.launch {
            _routine.value = container.routineRepository.getById(id) ?: Routine(name = "")
        }
    }

    fun newRoutine() {
        _routine.value = Routine(name = "")
    }

    fun updateName(name: String) {
        _routine.value = _routine.value?.copy(name = name)
    }

    fun updateIcon(icon: String) {
        _routine.value = _routine.value?.copy(icon = icon)
    }

    fun updateColor(color: Long) {
        _routine.value = _routine.value?.copy(color = color)
    }

    // -- Triggers --

    fun addTrigger(type: TriggerType) {
        val current = _routine.value ?: return
        val defaults = type.params.associate { it.key to it.defaultValue }
        _routine.value = current.copy(
            triggers = current.triggers + TriggerConfig(type.key, defaults)
        )
    }

    fun removeTrigger(index: Int) {
        val current = _routine.value ?: return
        if (index !in current.triggers.indices) return
        _routine.value = current.copy(
            triggers = current.triggers.filterIndexed { i, _ -> i != index }
        )
    }

    fun moveTrigger(index: Int, direction: Int) {
        val current = _routine.value ?: return
        val target = index + direction
        if (index !in current.triggers.indices || target !in current.triggers.indices) return
        val list = current.triggers.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        _routine.value = current.copy(triggers = list)
    }

    fun updateTriggerParam(index: Int, key: String, value: String) {
        val current = _routine.value ?: return
        if (index !in current.triggers.indices) return
        val list = current.triggers.toMutableList()
        val trigger = list[index]
        list[index] = trigger.copy(params = trigger.params + (key to value))
        _routine.value = current.copy(triggers = list)
    }

    // -- Conditions --

    fun addCondition(type: ConditionType) {
        val current = _routine.value ?: return
        val defaults = type.params.associate { it.key to it.defaultValue }
        _routine.value = current.copy(
            conditions = current.conditions + ConditionConfig(type.key, defaults)
        )
    }

    fun removeCondition(index: Int) {
        val current = _routine.value ?: return
        if (index !in current.conditions.indices) return
        _routine.value = current.copy(
            conditions = current.conditions.filterIndexed { i, _ -> i != index }
        )
    }

    fun moveCondition(index: Int, direction: Int) {
        val current = _routine.value ?: return
        val target = index + direction
        if (index !in current.conditions.indices || target !in current.conditions.indices) return
        val list = current.conditions.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        _routine.value = current.copy(conditions = list)
    }

    fun updateConditionParam(index: Int, key: String, value: String) {
        val current = _routine.value ?: return
        if (index !in current.conditions.indices) return
        val list = current.conditions.toMutableList()
        val condition = list[index]
        list[index] = condition.copy(params = condition.params + (key to value))
        _routine.value = current.copy(conditions = list)
    }

    // -- Actions --

    fun addAction(type: ActionType) {
        val current = _routine.value ?: return
        val defaults = type.params.associate { it.key to it.defaultValue }
        _routine.value = current.copy(
            actions = current.actions + ActionConfig(type.key, defaults)
        )
    }

    fun removeAction(index: Int) {
        val current = _routine.value ?: return
        if (index !in current.actions.indices) return
        _routine.value = current.copy(
            actions = current.actions.filterIndexed { i, _ -> i != index }
        )
    }

    fun moveAction(index: Int, direction: Int) {
        val current = _routine.value ?: return
        val target = index + direction
        if (index !in current.actions.indices || target !in current.actions.indices) return
        val list = current.actions.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        _routine.value = current.copy(actions = list)
    }

    fun updateActionParam(index: Int, key: String, value: String) {
        val current = _routine.value ?: return
        if (index !in current.actions.indices) return
        val list = current.actions.toMutableList()
        val action = list[index]
        list[index] = action.copy(params = action.params + (key to value))
        _routine.value = current.copy(actions = list)
    }

    // -- Validation + save --

    /** Returns an error message, or null when the routine is valid. */
    fun validate(): String? {
        val current = _routine.value ?: return "Nothing to save yet."
        if (current.name.isBlank()) return "Give the routine a name."
        if (current.triggers.isEmpty()) return "Add at least one trigger (When)."
        if (current.actions.isEmpty()) return "Add at least one action (Do)."
        return null
    }

    /** Validates and saves. Returns an error message, or null on success. */
    suspend fun save(): String? {
        val error = validate()
        if (error != null) return error
        val current = _routine.value ?: return "Nothing to save yet."
        container.routineRepository.save(current)
        resyncAutomation(container)
        return null
    }

    /** Saves, then runs the routine immediately. Returns an error message, or null. */
    suspend fun testRun(): String? {
        val error = save()
        if (error != null) return error
        val current = _routine.value ?: return "Nothing to run yet."
        EngineService.runRoutineNow(container.appContext, current.id)
        return null
    }
}

// ---------------------------------------------------------------------------
// Modes
// ---------------------------------------------------------------------------

class ModesViewModel(private val container: AppContainer) : ViewModel() {

    val modes: StateFlow<List<ModeProfile>> = container.modeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Activates an inactive mode, or deactivates an active one (settings restore). */
    fun toggleMode(mode: ModeProfile) {
        viewModelScope.launch {
            val context = container.appContext
            val updated = if (mode.isActive) {
                ModeApplier.deactivate(context, mode, container)
            } else {
                ModeApplier.activate(context, mode, container)
            }
            container.modeRepository.save(updated)
        }
    }

    fun saveMode(mode: ModeProfile) {
        viewModelScope.launch {
            container.modeRepository.save(mode)
        }
    }

    fun deleteMode(id: String) {
        viewModelScope.launch {
            container.modeRepository.delete(id)
        }
    }
}

// ---------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val logs: StateFlow<List<RunLog>> = container.runLogRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch {
            container.runLogRepository.clearAll()
        }
    }
}

// ---------------------------------------------------------------------------
// Diagnostics
// ---------------------------------------------------------------------------

data class DiagnosticsState(
    val permissions: List<Permissions.PermissionStatus> = emptyList(),
    val brand: OemGuide.OemBrand? = null,
    val engineEnabled: Boolean = true,
    val lastWatchdogRun: Long = 0L,
    val exactAlarmAllowed: Boolean = true,
    val loading: Boolean = true
)

class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val context: Context = container.appContext
            val permissions = withContext(Dispatchers.IO) { Permissions.statusList(context) }
            val brand = OemGuide.detectBrand()
            val engineEnabled = container.preferences.isEngineEnabled()
            val lastWatchdogRun = container.preferences.getLastWatchdogRun()
            val exactAlarmAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
            } else {
                true
            }
            _state.value = DiagnosticsState(
                permissions = permissions,
                brand = brand,
                engineEnabled = engineEnabled,
                lastWatchdogRun = lastWatchdogRun,
                exactAlarmAllowed = exactAlarmAllowed,
                loading = false
            )
        }
    }

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setEngineEnabled(enabled)
            if (enabled) EngineService.start(container.appContext)
            else EngineService.stop(container.appContext)
            _state.value = _state.value.copy(engineEnabled = enabled)
        }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val engineEnabled: StateFlow<Boolean> = container.preferences.engineEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val favouriteRoutineId: StateFlow<String?> = container.preferences.favouriteRoutineId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val homeLocation: StateFlow<String?> = container.preferences.homeLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val onboardingDone: StateFlow<Boolean> = container.preferences.onboardingDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val routines: StateFlow<List<Routine>> = container.routineRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** A backup parsed from a file, awaiting the user's confirmation to import. */
    private val _pendingImport = MutableStateFlow<BackupFile?>(null)
    val pendingImport: StateFlow<BackupFile?> = _pendingImport.asStateFlow()

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setEngineEnabled(enabled)
            if (enabled) EngineService.start(container.appContext)
            else EngineService.stop(container.appContext)
        }
    }

    fun setFavouriteRoutineId(id: String?) {
        viewModelScope.launch {
            container.preferences.setFavouriteRoutineId(id)
            RoutineWidgetProvider.updateAll(container.appContext)
        }
    }

    fun setHomeLocation(value: String) {
        viewModelScope.launch {
            val trimmed = value.trim()
            container.preferences.setHomeLocation(trimmed.ifBlank { null })
        }
    }

    fun setOnboardingDone(done: Boolean) {
        viewModelScope.launch {
            container.preferences.setOnboardingDone(done)
        }
    }

    /** Exports the user's routines and modes as JSON to the given document Uri. */
    suspend fun exportToUri(uriString: String): Boolean {
        val routines = container.routineRepository.getAll().filter { !it.isTemplate }
        val modes = container.modeRepository.getAll()
        val text = BackupManager.exportJson(routines, modes)
        return BackupManager.writeToUri(container.appContext, Uri.parse(uriString), text)
    }

    /**
     * Reads and parses a backup file. Returns the parsed backup (also exposed
     * as [pendingImport] for the confirmation dialog), or null when the file
     * could not be read or parsed.
     */
    suspend fun importFromUri(uriString: String): BackupFile? {
        val text = BackupManager.readFromUri(container.appContext, Uri.parse(uriString))
            ?: return null
        val parsed = BackupManager.parseBackup(text)
        _pendingImport.value = parsed
        return parsed
    }

    fun dismissImport() {
        _pendingImport.value = null
    }

    /**
     * Imports the pending backup. Routines/modes whose id already exists are
     * saved with a fresh id so nothing is overwritten. Returns the number of
     * routines and modes imported.
     */
    suspend fun confirmImport(): Pair<Int, Int> {
        val backup = _pendingImport.value ?: return 0 to 0
        val existingRoutineIds = container.routineRepository.getAll().map { it.id }.toSet()
        val existingModeIds = container.modeRepository.getAll().map { it.id }.toSet()
        var routineCount = 0
        var modeCount = 0
        for (routine in backup.routines) {
            val toSave = if (routine.id in existingRoutineIds) {
                routine.copy(id = UUID.randomUUID().toString())
            } else {
                routine
            }
            container.routineRepository.save(toSave.copy(isTemplate = false))
            routineCount++
        }
        for (mode in backup.modes) {
            val toSave = if (mode.id in existingModeIds) {
                mode.copy(id = UUID.randomUUID().toString())
            } else {
                mode
            }
            // Imported modes always start inactive; their revert snapshot (if any)
            // belongs to another phone/session and must not be trusted.
            container.modeRepository.save(toSave.copy(isActive = false, revertSnapshotJson = null))
            modeCount++
        }
        _pendingImport.value = null
        resyncAutomation(container)
        return routineCount to modeCount
    }
}

// ---------------------------------------------------------------------------
// Factory (no DI framework in v1 — the container is passed in by hand)
// ---------------------------------------------------------------------------

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(container)

            modelClass.isAssignableFrom(EditorViewModel::class.java) ->
                EditorViewModel(container)

            modelClass.isAssignableFrom(ModesViewModel::class.java) ->
                ModesViewModel(container)

            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(container)

            modelClass.isAssignableFrom(DiagnosticsViewModel::class.java) ->
                DiagnosticsViewModel(container)

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(container)

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
