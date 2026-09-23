package com.uri.lee.dl.fakes

import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: ScanSettings = ScanSettings()) : SettingsRepository {
    override val scanSettings = MutableStateFlow(initial)

    override suspend fun setMinConfidence(value: Float) = scanSettings.update { it.copy(minConfidence = value) }

    override suspend fun setDetectObjectsInSingleImage(enabled: Boolean) =
        scanSettings.update { it.copy(detectObjectsInSingleImage = enabled) }
}
