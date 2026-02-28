package com.metapurge.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.metapurge.app.domain.model.Stats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "metapurge_stats")

class StatsRepository(private val context: Context) {

    private object PreferencesKeys {
        val FILES_PURGED = intPreferencesKey("files_purged")
        val DATA_REMOVED = longPreferencesKey("data_removed")
        val GPS_FOUND = intPreferencesKey("gps_found")
    }

    val stats: Flow<Stats> = context.dataStore.data.map { preferences ->
        Stats(
            filesPurged = preferences[PreferencesKeys.FILES_PURGED] ?: 0,
            dataRemoved = preferences[PreferencesKeys.DATA_REMOVED] ?: 0L,
            gpsFound = preferences[PreferencesKeys.GPS_FOUND] ?: 0
        )
    }

    suspend fun incrementStats(filesRemoved: Int, dataRemoved: Long, gpsFound: Int) {
        context.dataStore.edit { preferences ->
            val currentFiles = preferences[PreferencesKeys.FILES_PURGED] ?: 0
            val currentData = preferences[PreferencesKeys.DATA_REMOVED] ?: 0L
            val currentGps = preferences[PreferencesKeys.GPS_FOUND] ?: 0

            preferences[PreferencesKeys.FILES_PURGED] = currentFiles + filesRemoved
            preferences[PreferencesKeys.DATA_REMOVED] = currentData + dataRemoved
            preferences[PreferencesKeys.GPS_FOUND] = currentGps + gpsFound
        }
    }

    suspend fun clearStats() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
