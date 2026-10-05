package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.ScanSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val scanSettings: Flow<ScanSettings>

    suspend fun setMinConfidence(value: Float)

    suspend fun setDetectObjectsInSingleImage(enabled: Boolean)

    /** Whether anonymous usage statistics are sent (Profile → Share usage statistics). On by default. */
    val usageStatistics: Flow<Boolean>

    suspend fun setUsageStatistics(enabled: Boolean)
}
