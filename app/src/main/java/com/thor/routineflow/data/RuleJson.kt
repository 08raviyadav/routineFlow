package com.thor.routineflow.data

import com.thor.routineflow.model.ActionConfig
import com.thor.routineflow.model.BackupFile
import com.thor.routineflow.model.ConditionConfig
import com.thor.routineflow.model.ModeProfile
import com.thor.routineflow.model.ModeSettings
import com.thor.routineflow.model.Routine
import com.thor.routineflow.model.TriggerConfig
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Single JSON codec for the versioned rule model. Used by Room (trigger/action
 * lists are stored as JSON columns), backup import/export, and routine sharing.
 * `ignoreUnknownKeys` keeps forward-compatibility: a backup exported by a newer
 * version still imports, unknown fields are dropped.
 */
object RuleJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val compact = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encodeTriggers(value: List<TriggerConfig>): String =
        compact.encodeToString(ListSerializer(TriggerConfig.serializer()), value)

    fun decodeTriggers(value: String): List<TriggerConfig> =
        runCatching {
            compact.decodeFromString(ListSerializer(TriggerConfig.serializer()), value)
        }.getOrDefault(emptyList())

    fun encodeActions(value: List<ActionConfig>): String =
        compact.encodeToString(ListSerializer(ActionConfig.serializer()), value)

    fun decodeActions(value: String): List<ActionConfig> =
        runCatching {
            compact.decodeFromString(ListSerializer(ActionConfig.serializer()), value)
        }.getOrDefault(emptyList())

    fun encodeConditions(value: List<ConditionConfig>): String =
        compact.encodeToString(ListSerializer(ConditionConfig.serializer()), value)

    fun decodeConditions(value: String): List<ConditionConfig> =
        runCatching {
            compact.decodeFromString(ListSerializer(ConditionConfig.serializer()), value)
        }.getOrDefault(emptyList())

    fun encodeModeSettings(value: ModeSettings): String =
        compact.encodeToString(ModeSettings.serializer(), value)

    fun decodeModeSettings(value: String): ModeSettings =
        runCatching { compact.decodeFromString(ModeSettings.serializer(), value) }
            .getOrDefault(ModeSettings())

    /** Encodes a full backup file (pretty-printed, human-readable). */
    fun encodeBackup(routines: List<Routine>, modes: List<ModeProfile>): String {
        val file = BackupFile(routines = routines, modes = modes)
        return json.encodeToString(file)
    }

    /** Encodes a single routine for sharing. */
    fun encodeRoutineShare(routine: Routine): String =
        json.encodeToString(BackupFile(routines = listOf(routine)))

    /** Decodes a backup/share file. Returns null when the format is not ours. */
    fun decodeBackup(text: String): BackupFile? {
        val file = runCatching { json.decodeFromString<BackupFile>(text) }.getOrNull()
            ?: return null
        if (file.format != BackupFile.FORMAT) return null
        if (file.version > BackupFile.FORMAT_VERSION) return null
        return file
    }
}
