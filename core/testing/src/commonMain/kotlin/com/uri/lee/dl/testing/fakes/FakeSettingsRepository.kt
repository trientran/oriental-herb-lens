package com.uri.lee.dl.testing.fakes

import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.model.SurveyProgress
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

    override val sharingTermsAccepted = MutableStateFlow(false)

    override suspend fun acceptSharingTerms() {
        sharingTermsAccepted.value = true
    }

    override val surveyProgress = MutableStateFlow(SurveyProgress())

    override suspend fun recordAppOpen() = surveyProgress.update { it.copy(appOpens = it.appOpens + 1) }

    override suspend fun postponeSurvey() = surveyProgress.update {
        if (it.askAtOpen > SurveyProgress.FIRST_ASK) it.copy(finished = true) else it.copy(askAtOpen = it.appOpens + SurveyProgress.ASK_AGAIN_AFTER)
    }

    override suspend fun finishSurveyInvite() = surveyProgress.update { it.copy(finished = true) }

    override val creditName = MutableStateFlow<String?>(null)

    override suspend fun setCreditName(name: String?) {
        creditName.value = name?.trim()?.ifEmpty { null }
    }

    override val seenBadges = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun markBadgesSeen(badges: Set<String>) = seenBadges.update { it + badges }
}
