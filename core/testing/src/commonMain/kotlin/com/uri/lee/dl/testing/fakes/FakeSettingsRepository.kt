package com.uri.lee.dl.testing.fakes

import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: ScanSettings = ScanSettings()) : SettingsRepository {
    override val scanSettings = MutableStateFlow(initial)

    override suspend fun setMinConfidence(value: Float) = scanSettings.update { it.copy(minConfidence = value) }

    override suspend fun setDetectObjectsInSingleImage(enabled: Boolean) =
        scanSettings.update { it.copy(detectObjectsInSingleImage = enabled) }

    override val usageStatistics = MutableStateFlow(true)

    override suspend fun setUsageStatistics(enabled: Boolean) {
        usageStatistics.value = enabled
    }

    override val identifyNoticeSeen = MutableStateFlow(false)

    override suspend fun setIdentifyNoticeSeen() {
        identifyNoticeSeen.value = true
    }
}
