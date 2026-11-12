package com.thor.routineflow.data

import com.thor.routineflow.model.ModeProfile
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.RunLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Maps between Room entities and the domain model. */
object Mappers {
    fun Routine.toEntity() = RoutineEntity(
        id = id,
        name = name,
        enabled = enabled,
        icon = icon,
        color = color,
        triggersJson = RuleJson.encodeTriggers(triggers),
        conditionsJson = RuleJson.encodeConditions(conditions),
        actionsJson = RuleJson.encodeActions(actions),
        isTemplate = isTemplate,
        runCount = runCount,
        lastRunAt = lastRunAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun RoutineEntity.toRoutine() = Routine(
        id = id,
        name = name,
        enabled = enabled,
        icon = icon,
        color = color,
        triggers = RuleJson.decodeTriggers(triggersJson),
        conditions = RuleJson.decodeConditions(conditionsJson),
        actions = RuleJson.decodeActions(actionsJson),
        isTemplate = isTemplate,
        runCount = runCount,
        lastRunAt = lastRunAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun ModeProfile.toEntity() = ModeEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        settingsJson = RuleJson.encodeModeSettings(settings),
        autoTriggersJson = RuleJson.encodeTriggers(autoTriggers),
        isActive = isActive,
        isBuiltIn = isBuiltIn,
        revertSnapshotJson = revertSnapshotJson,
        createdAt = createdAt
    )

    fun ModeEntity.toMode() = ModeProfile(
        id = id,
        name = name,
        icon = icon,
        color = color,
        settings = RuleJson.decodeModeSettings(settingsJson),
        autoTriggers = RuleJson.decodeTriggers(autoTriggersJson),
        isActive = isActive,
        isBuiltIn = isBuiltIn,
        revertSnapshotJson = revertSnapshotJson,
        createdAt = createdAt
    )

    fun RunLog.toEntity() = RunLogEntity(
        id = id,
        routineId = routineId,
        routineName = routineName,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
        detail = detail
    )

    fun RunLogEntity.toRunLog() = RunLog(
        id = id,
        routineId = routineId,
        routineName = routineName,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
        detail = detail
    )
}

class RoutineRepository(private val db: RoutineFlowDatabase) {
    private val dao get() = db.routineDao()

    fun observeAll(): Flow<List<Routine>> = dao.observeAll().map { list ->
        list.map { with(Mappers) { it.toRoutine() } }
    }

    suspend fun getAll(): List<Routine> =
        dao.getAll().map { with(Mappers) { it.toRoutine() } }

    suspend fun getEnabled(): List<Routine> =
        dao.getEnabled().map { with(Mappers) { it.toRoutine() } }

    suspend fun getById(id: String): Routine? =
        dao.getById(id)?.let { with(Mappers) { it.toRoutine() } }

    suspend fun save(routine: Routine) {
        with(Mappers) { dao.upsert(routine.copy(updatedAt = System.currentTimeMillis()).toEntity()) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun setEnabled(id: String, enabled: Boolean) =
        dao.setEnabled(id, enabled, System.currentTimeMillis())

    suspend fun recordRun(id: String, at: Long = System.currentTimeMillis()) =
        dao.recordRun(id, at)
}

class ModeRepository(private val db: RoutineFlowDatabase) {
    private val dao get() = db.modeDao()

    fun observeAll(): Flow<List<ModeProfile>> = dao.observeAll().map { list ->
        list.map { with(Mappers) { it.toMode() } }
    }

    suspend fun getAll(): List<ModeProfile> =
        dao.getAll().map { with(Mappers) { it.toMode() } }

    suspend fun getById(id: String): ModeProfile? =
        dao.getById(id)?.let { with(Mappers) { it.toMode() } }

    suspend fun getActive(): List<ModeProfile> =
        dao.getActive().map { with(Mappers) { it.toMode() } }

    suspend fun save(mode: ModeProfile) {
        with(Mappers) { dao.upsert(mode.toEntity()) }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}

class RunLogRepository(private val db: RoutineFlowDatabase) {
    private val dao get() = db.runLogDao()

    fun observeRecent(): Flow<List<RunLog>> = dao.observeRecent().map { list ->
        list.map { with(Mappers) { it.toRunLog() } }
    }

    suspend fun add(log: RunLog) {
        with(Mappers) { dao.insert(log.toEntity()) }
    }

    suspend fun clearAll() = dao.clearAll()

    /** Drops logs older than 30 days. Called by the watchdog worker. */
    suspend fun pruneOld() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.pruneBefore(cutoff)
    }
}
