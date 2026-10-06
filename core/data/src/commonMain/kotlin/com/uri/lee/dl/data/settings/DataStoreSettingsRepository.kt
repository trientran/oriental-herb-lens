package com.uri.lee.dl.data.settings

import com.uri.lee.dl.core.datastore.KeyValueStore
import com.uri.lee.dl.core.datastore.booleanKey
import com.uri.lee.dl.core.datastore.floatKey
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Uses the existing DataStore file and keys, so settings saved by earlier versions carry over. */
internal class DataStoreSettingsRepository(
    private val dataStore: KeyValueStore,
    /** Until the user chooses: on in the apps (pending the ethics approval), off on the web, which asks first (cookies). */
    private val usageStatisticsByDefault: Boolean = true,
) : SettingsRepository {

    override val scanSettings: Flow<ScanSettings> = dataStore.data
        .map { prefs ->
            ScanSettings(
                minConfidence = prefs[CONFIDENCE_LEVEL] ?: ScanSettings.DEFAULT_MIN_CONFIDENCE,
                detectObjectsInSingleImage = prefs[IS_OBJECTS_MODE_SINGLE_IMAGE] ?: ScanSettings().detectObjectsInSingleImage,
            )
        }
        .distinctUntilChanged()

    override suspend fun setMinConfidence(value: Float) {
        dataStore.edit { it[CONFIDENCE_LEVEL] = value }
    }

    override suspend fun setDetectObjectsInSingleImage(enabled: Boolean) {
        dataStore.edit { it[IS_OBJECTS_MODE_SINGLE_IMAGE] = enabled }
    }

    override val usageStatistics: Flow<Boolean> = dataStore.data.map { it[USAGE_STATISTICS] ?: usageStatisticsByDefault }.distinctUntilChanged()

    override suspend fun setUsageStatistics(enabled: Boolean) {
        dataStore.edit { it[USAGE_STATISTICS] = enabled }
    }

    companion object {
        // Persisted key names. Renaming one silently resets the user's setting.
        val IS_OBJECTS_MODE_SINGLE_IMAGE = booleanKey("IS_OBJECTS_MODE")
        val CONFIDENCE_LEVEL = floatKey("CONFIDENCE_LEVEL")
        val USAGE_STATISTICS = booleanKey("usage_statistics")
    }
}
