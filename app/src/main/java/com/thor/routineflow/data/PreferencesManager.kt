package com.thor.routineflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "routineflow_prefs")

/** Small app settings (DataStore). Routines themselves live in Room. */
class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val KEY_ENGINE_ENABLED = booleanPreferencesKey("engine_enabled")
        private val KEY_FAVOURITE_ROUTINE_ID = stringPreferencesKey("favourite_routine_id")
        private val KEY_HOME_LOCATION = stringPreferencesKey("home_location") // "lat, lng"
        private val KEY_LAST_WATCHDOG_RUN = longPreferencesKey("last_watchdog_run")
        private val KEY_TEMPLATES_SEEDED = booleanPreferencesKey("templates_seeded")
        private val KEY_MODES_SEEDED = booleanPreferencesKey("modes_seeded")
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }
    val engineEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENGINE_ENABLED] ?: true }
    val favouriteRoutineId: Flow<String?> =
        context.dataStore.data.map { it[KEY_FAVOURITE_ROUTINE_ID] }
    val homeLocation: Flow<String?> = context.dataStore.data.map { it[KEY_HOME_LOCATION] }

    suspend fun isOnboardingDone(): Boolean = onboardingDone.first()
    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun isEngineEnabled(): Boolean = engineEnabled.first()
    suspend fun setEngineEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENGINE_ENABLED] = enabled }
    }

    suspend fun getFavouriteRoutineId(): String? = favouriteRoutineId.first()
    suspend fun setFavouriteRoutineId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(KEY_FAVOURITE_ROUTINE_ID) else it[KEY_FAVOURITE_ROUTINE_ID] = id
        }
    }

    suspend fun getHomeLocation(): String? = homeLocation.first()
    suspend fun setHomeLocation(value: String?) {
        context.dataStore.edit {
            if (value == null) it.remove(KEY_HOME_LOCATION) else it[KEY_HOME_LOCATION] = value
        }
    }

    suspend fun getLastWatchdogRun(): Long =
        context.dataStore.data.map { it[KEY_LAST_WATCHDOG_RUN] ?: 0L }.first()

    suspend fun setLastWatchdogRun(at: Long) {
        context.dataStore.edit { it[KEY_LAST_WATCHDOG_RUN] = at }
    }

    suspend fun areTemplatesSeeded(): Boolean =
        context.dataStore.data.map { it[KEY_TEMPLATES_SEEDED] ?: false }.first()

    suspend fun setTemplatesSeeded() {
        context.dataStore.edit { it[KEY_TEMPLATES_SEEDED] = true }
    }

    suspend fun areModesSeeded(): Boolean =
        context.dataStore.data.map { it[KEY_MODES_SEEDED] ?: false }.first()

    suspend fun setModesSeeded() {
        context.dataStore.edit { it[KEY_MODES_SEEDED] = true }
    }
}
