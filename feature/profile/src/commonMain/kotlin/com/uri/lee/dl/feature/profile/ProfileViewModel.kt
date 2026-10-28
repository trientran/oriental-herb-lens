package com.uri.lee.dl.feature.profile

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.common.AppInfo
import com.uri.lee.dl.core.common.Clock
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.model.AppLinks
import com.uri.lee.dl.domain.model.Badge
import com.uri.lee.dl.domain.model.Badges
import com.uri.lee.dl.domain.model.ContributionHistoryRepository
import com.uri.lee.dl.domain.model.ContributionKind
import com.uri.lee.dl.domain.model.ContributionSummary
import com.uri.lee.dl.domain.model.Citation
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.AppLinksRepository
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.CitationRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.sharing.SharingProblem
import com.uri.lee.dl.domain.sharing.SharingRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
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

    /** Credit the user by [name] on what they share from now on; blank goes back to "a Med Herb Lens user". */
    data class SaveCreditName(val name: String) : ProfileAction

    /** The badges on screen were seen, so they're no longer marked new. */
    data object BadgesSeen : ProfileAction
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
    /** What the signed-in user shared; null while signed out or not yet loaded. */
    val contributions: ContributionSummary? = null,
    /** Badges earned, private to the user. */
    val badges: Set<Badge> = emptySet(),
    /** Earned badges the user hasn't seen yet. */
    val newBadges: Set<Badge> = emptySet(),
    /** The name the user is credited by; null for "a Med Herb Lens user". */
    val creditName: String? = null,
    /** Why the credit name just typed couldn't be saved. */
    val creditProblem: SharingProblem? = null,
)

/** Account and settings. */
class ProfileViewModel(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val citations: CitationRepository,
    private val links: AppLinksRepository,
    app: AppInfo,
    private val history: ContributionHistoryRepository,
    private val clock: Clock = Clock.System,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<ProfileState, ProfileAction>(ProfileState(versionName = app.versionName)) {

    init {
        watchContributions()
        settings.creditName.onEach { setState { copy(creditName = it) } }.launchIn(viewModelScope)
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
            is ProfileAction.SaveCreditName -> {
                val name = action.name.trim()
                val problem = if (name.isEmpty()) null else SharingRules.creditProblem(name)
                setState { copy(creditProblem = problem) }
                if (problem == null) save { settings.setCreditName(name.ifEmpty { null }) }
            }
            ProfileAction.BadgesSeen -> currentState.newBadges.takeIf { it.isNotEmpty() }?.let { seen ->
                save { settings.markBadgesSeen(seen.mapTo(mutableSetOf()) { it.name }) }
            }
        }
    }

    /**
     * Totals and badges from the user's own record. What still counts is checked against what's
     * shown now (photos on their species page, models still listed), each time the record changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchContributions() {
        history.observe()
            .mapLatest { contributions ->
                try {
                    val shown = history.stillShown(contributions)
                    val adds = history.modelAdds(contributions.filter { it.kind == ContributionKind.MODEL }.mapNotNull { it.modelId })
                    Badges.summary(contributions, adds) { it.id in shown } to Badges.earned(contributions, adds, clock.nowMillis()) { it.id in shown }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.w(e) { "Contributions not checked" }
                    Badges.summary(contributions, 0) to emptySet()
                }
            }
            .combine(auth.observeUserId()) { totals, uid -> totals.takeIf { uid != null } }
            .combine(settings.seenBadges) { totals, seen -> totals to seen }
            .onEach { (totals, seen) ->
                setState {
                    copy(
                        contributions = totals?.first,
                        badges = totals?.second.orEmpty(),
                        newBadges = totals?.second.orEmpty().filterTo(mutableSetOf()) { it.name !in seen },
                    )
                }
            }
            .launchIn(viewModelScope)
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
