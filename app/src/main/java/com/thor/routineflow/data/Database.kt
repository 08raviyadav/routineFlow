package com.thor.routineflow.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Entities — routines and modes store their trigger/action lists as versioned
// JSON (see RuleJson), so the rule model can evolve without DB migrations.
// ---------------------------------------------------------------------------

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val icon: String,
    val color: Long,
    val triggersJson: String,
    val conditionsJson: String,
    val actionsJson: String,
    val isTemplate: Boolean,
    val runCount: Int,
    val lastRunAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "modes")
data class ModeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val settingsJson: String,
    val autoTriggersJson: String,
    val isActive: Boolean,
    val isBuiltIn: Boolean,
    val revertSnapshotJson: String?,
    val createdAt: Long
)

@Entity(tableName = "run_logs")
data class RunLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: String,
    val routineName: String,
    val startedAt: Long,
    val finishedAt: Long,
    val status: String,
    val detail: String
)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY updatedAt DESC")
    suspend fun getAll(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE enabled = 1")
    suspend fun getEnabled(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoutineEntity)

    @Update
    suspend fun update(entity: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE routines SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE routines SET runCount = runCount + 1, lastRunAt = :at WHERE id = :id")
    suspend fun recordRun(id: String, at: Long)
}

@Dao
interface ModeDao {
    @Query("SELECT * FROM modes ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ModeEntity>>

    @Query("SELECT * FROM modes ORDER BY createdAt ASC")
    suspend fun getAll(): List<ModeEntity>

    @Query("SELECT * FROM modes WHERE id = :id")
    suspend fun getById(id: String): ModeEntity?

    @Query("SELECT * FROM modes WHERE isActive = 1")
    suspend fun getActive(): List<ModeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModeEntity)

    @Update
    suspend fun update(entity: ModeEntity)

    @Query("DELETE FROM modes WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface RunLogDao {
    @Query("SELECT * FROM run_logs ORDER BY startedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<RunLogEntity>>

    @Insert
    suspend fun insert(entity: RunLogEntity)

    @Query("DELETE FROM run_logs")
    suspend fun clearAll()

    @Query("DELETE FROM run_logs WHERE startedAt < :before")
    suspend fun pruneBefore(before: Long)
}

@Database(
    entities = [RoutineEntity::class, ModeEntity::class, RunLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RoutineFlowDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun modeDao(): ModeDao
    abstract fun runLogDao(): RunLogDao

    companion object {
        @Volatile
        private var instance: RoutineFlowDatabase? = null

        fun get(context: Context): RoutineFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutineFlowDatabase::class.java,
                    "routineflow.db"
                ).build().also { instance = it }
            }
    }
}
