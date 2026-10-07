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

    /** Whether the user has read Identify's note that the herb model is a research preview. */
    val identifyNoticeSeen: Flow<Boolean>

    suspend fun setIdentifyNoticeSeen()

    /** Whether the user accepted the terms for sharing models with everyone (Train tab). */
    val sharingTermsAccepted: Flow<Boolean>

    suspend fun acceptSharingTerms()
}
