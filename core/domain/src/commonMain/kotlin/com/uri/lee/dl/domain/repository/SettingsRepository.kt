package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.model.SurveyProgress
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

    /** The research survey invitation: how often the app was opened, and whether to ask. */
    val surveyProgress: Flow<SurveyProgress>

    /** Counts one more opening of the app. */
    suspend fun recordAppOpen()

    /** "Not now": asks again a few opens later the first time, never after the second. */
    suspend fun postponeSurvey()

    /** The user opened the survey: the invitation isn't shown again. */
    suspend fun finishSurveyInvite()
}
