package com.uri.lee.dl.feature.profile

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.model.AppLinks
import com.uri.lee.dl.domain.model.Citation
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.AppLinksRepository
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.CitationRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface ProfileAction {
    data class SetMinConfidence(val value: Float) : ProfileAction
    data object SignOut : ProfileAction
    data class SetUsageStatistics(val enabled: Boolean) : ProfileAction

    /** The user copied the citation at [index] in How to cite (for usage statistics). */
    data class CitationCopied(val index: Int) : ProfileAction

    /** The user opened the dataset link (for usage statistics). */
    data object DatasetOpened : ProfileAction
}

data class ProfileState(
    /** Null until known. */
    val isSignedIn: Boolean? = null,
    val scanSettings: ScanSettings = ScanSettings(),
    val versionName: String = "",
    val usageStatistics: Boolean = true,
    /** Works to cite the app by; the section is hidden while empty. */
    val citations: List<Citation> = emptyList(),
    /** The built-in links until the published ones arrive. */
    val links: AppLinks = AppLinks(),
)

/** Account and settings. */
class ProfileViewModel(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val citations: CitationRepository,
    private val links: AppLinksRepository,
    app: AppInfo,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<ProfileState, ProfileAction>(ProfileState(versionName = app.versionName)) {

    init {
        auth.observeUserId().onEach { setState { copy(isSignedIn = it != null) } }.launchIn(viewModelScope)
        settings.scanSettings.onEach { setState { copy(scanSettings = it) } }.launchIn(viewModelScope)
        settings.usageStatistics.onEach { setState { copy(usageStatistics = it) } }.launchIn(viewModelScope)
        viewModelScope.launch {
            try {
                val published = citations.citations()
                setState { copy(citations = published) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Citations unavailable" }
            }
        }
        viewModelScope.launch {
            try {
                val published = links.links()
                setState { copy(links = published) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Links unavailable; keeping the built-in ones" }
            }
        }
    }

    override fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.SetMinConfidence -> save { settings.setMinConfidence(action.value.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)) }
            ProfileAction.SignOut -> save { auth.signOut() }
            is ProfileAction.SetUsageStatistics -> save { settings.setUsageStatistics(action.enabled) }
            is ProfileAction.CitationCopied -> currentState.citations.getOrNull(action.index)?.let {
                analytics.log(AnalyticsEvent.CitationCopied(action.index + 1, it.url))
            }
            ProfileAction.DatasetOpened -> analytics.log(AnalyticsEvent.DatasetOpened)
        }
    }

    private fun save(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Profile change failed" }
        }
    }

    companion object {
        const val MIN_CONFIDENCE = 0.3f
        const val MAX_CONFIDENCE = 0.95f
        private val log = Logger.withTag("Profile")
    }
}
