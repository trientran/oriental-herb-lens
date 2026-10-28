package com.uri.lee.dl.data.settings

import com.uri.lee.dl.core.datastore.KeyValueStore
import com.uri.lee.dl.core.datastore.booleanKey
import com.uri.lee.dl.core.datastore.floatKey
import com.uri.lee.dl.core.datastore.intKey
import com.uri.lee.dl.core.datastore.stringKey
import com.uri.lee.dl.core.datastore.stringSetKey
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.model.SurveyProgress
import com.uri.lee.dl.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Uses the existing DataStore file and keys, so settings saved by earlier versions carry over. */
internal class DataStoreSettingsRepository(private val dataStore: KeyValueStore) : SettingsRepository {

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

    override val usageStatistics: Flow<Boolean> = dataStore.data.map { it[USAGE_STATISTICS] ?: true }.distinctUntilChanged()

    override suspend fun setUsageStatistics(enabled: Boolean) {
        dataStore.edit { it[USAGE_STATISTICS] = enabled }
    }

    override val identifyNoticeSeen: Flow<Boolean> = dataStore.data.map { it[IDENTIFY_NOTICE_SEEN] ?: false }.distinctUntilChanged()

    override suspend fun setIdentifyNoticeSeen() {
        dataStore.edit { it[IDENTIFY_NOTICE_SEEN] = true }
    }

    override val sharingTermsAccepted: Flow<Boolean> = dataStore.data.map { it[SHARING_TERMS_ACCEPTED] ?: false }.distinctUntilChanged()

    override suspend fun acceptSharingTerms() {
        dataStore.edit { it[SHARING_TERMS_ACCEPTED] = true }
    }

    override val surveyProgress: Flow<SurveyProgress> = dataStore.data
        .map { SurveyProgress(it[APP_OPENS] ?: 0, it[SURVEY_ASK_AT] ?: SurveyProgress.FIRST_ASK, it[SURVEY_FINISHED] ?: false) }
        .distinctUntilChanged()

    override suspend fun recordAppOpen() {
        dataStore.edit { it[APP_OPENS] = (it[APP_OPENS] ?: 0) + 1 }
    }

    override suspend fun postponeSurvey() {
        dataStore.edit {
            val opens = it[APP_OPENS] ?: 0
            if ((it[SURVEY_ASK_AT] ?: SurveyProgress.FIRST_ASK) > SurveyProgress.FIRST_ASK) {
                it[SURVEY_FINISHED] = true
            } else {
                it[SURVEY_ASK_AT] = opens + SurveyProgress.ASK_AGAIN_AFTER
            }
        }
    }

    override suspend fun finishSurveyInvite() {
        dataStore.edit { it[SURVEY_FINISHED] = true }
    }

    override val creditName: Flow<String?> = dataStore.data.map { it[CREDIT_NAME] }.distinctUntilChanged()

    override suspend fun setCreditName(name: String?) {
        dataStore.edit { if (name.isNullOrBlank()) it.remove(CREDIT_NAME) else it[CREDIT_NAME] = name.trim() }
    }

    override val seenBadges: Flow<Set<String>> = dataStore.data.map { it[SEEN_BADGES].orEmpty() }.distinctUntilChanged()

    override suspend fun markBadgesSeen(badges: Set<String>) {
        dataStore.edit { it[SEEN_BADGES] = it[SEEN_BADGES].orEmpty() + badges }
    }

    companion object {
        // Persisted key names. Renaming one silently resets the user's setting.
        val IS_OBJECTS_MODE_SINGLE_IMAGE = booleanKey("IS_OBJECTS_MODE")
        val CONFIDENCE_LEVEL = floatKey("CONFIDENCE_LEVEL")
        val USAGE_STATISTICS = booleanKey("usage_statistics")
        val IDENTIFY_NOTICE_SEEN = booleanKey("identify_notice_seen")
        val SHARING_TERMS_ACCEPTED = booleanKey("sharing_terms_accepted")
        val APP_OPENS = intKey("app_opens")
        val SURVEY_ASK_AT = intKey("survey_ask_at")
        val SURVEY_FINISHED = booleanKey("survey_finished")
        val CREDIT_NAME = stringKey("credit_name")
        val SEEN_BADGES = stringSetKey("seen_badges")
    }
}
