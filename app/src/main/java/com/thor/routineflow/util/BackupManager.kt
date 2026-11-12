package com.thor.routineflow.util

import android.content.Context
import android.net.Uri
import com.thor.routineflow.data.RuleJson
import com.thor.routineflow.model.BackupFile
import com.thor.routineflow.model.ModeProfile
import com.thor.routineflow.model.Routine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Backup import/export. The JSON format is owned by [RuleJson]; this object only moves it to and from files. */
object BackupManager {

    fun exportJson(routines: List<Routine>, modes: List<ModeProfile>): String =
        RuleJson.encodeBackup(routines, modes)

    /** Returns null when the text is not a RoutineFlow backup (wrong format or a newer version). */
    fun parseBackup(text: String): BackupFile? = RuleJson.decodeBackup(text)

    suspend fun writeToUri(context: Context, uri: Uri, text: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
        }

    suspend fun readFromUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }
}
