package com.uri.lee.dl.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Uses the existing DataStore file and keys, so settings saved by earlier versions carry over. */
internal class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val scanSettings: Flow<ScanSettings> = dataStore.data
        .map { prefs ->
            ScanSettings(
                minConfidence = prefs[CONFIDENCE_LEVEL] ?: ScanSettings.DEFAULT_MIN_CONFIDENCE,
                detectObjectsInSingleImage = prefs[IS_OBJECTS_MODE_SINGLE_IMAGE] ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun setMinConfidence(value: Float) {
        dataStore.edit { it[CONFIDENCE_LEVEL] = value }
    }

    override suspend fun setDetectObjectsInSingleImage(enabled: Boolean) {
        dataStore.edit { it[IS_OBJECTS_MODE_SINGLE_IMAGE] = enabled }
    }

    companion object {
        // Persisted key names. Renaming one silently resets the user's setting.
        val IS_OBJECTS_MODE_SINGLE_IMAGE = booleanPreferencesKey("IS_OBJECTS_MODE")
        val CONFIDENCE_LEVEL = floatPreferencesKey("CONFIDENCE_LEVEL")
    }
}
