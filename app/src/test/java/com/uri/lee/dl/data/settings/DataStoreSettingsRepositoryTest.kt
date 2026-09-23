package com.uri.lee.dl.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.uri.lee.dl.domain.model.ScanSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
        folder.newFile("settings.preferences_pb")
    }
    private val repository = DataStoreSettingsRepository(dataStore)

    @Test
    fun `defaults when nothing is stored`() = scope.runTest {
        assertEquals(ScanSettings(minConfidence = 0.7f, detectObjectsInSingleImage = true), repository.scanSettings.first())
    }

    @Test
    fun `values written by earlier app versions are read`() = scope.runTest {
        // The raw key name the old screens wrote directly
        dataStore.edit { it[floatPreferencesKey("CONFIDENCE_LEVEL")] = 0.42f }

        assertEquals(0.42f, repository.scanSettings.first().minConfidence)
    }

    @Test
    fun `changes are persisted`() = scope.runTest {
        repository.setMinConfidence(0.55f)
        repository.setDetectObjectsInSingleImage(false)

        assertEquals(ScanSettings(minConfidence = 0.55f, detectObjectsInSingleImage = false), repository.scanSettings.first())
    }
}
