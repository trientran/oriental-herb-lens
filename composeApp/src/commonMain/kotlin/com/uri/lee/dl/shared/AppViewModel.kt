package com.uri.lee.dl.shared

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.model.AppLinks
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.model.SurveyProgress
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.domain.repository.AppLinksRepository
import com.uri.lee.dl.domain.repository.AppStatusRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface AppAction {
    /** The user put off a recommended update. */
    data object DismissUpdate : AppAction

    /** Profile → Research survey: shows the invitation. */
    data object ShowSurveyInvite : AppAction

    /** The user chose to take the survey; the app opens it. */
    data object SurveyStarted : AppAction

    /** "Not now" on the invitation shown by itself; closing the one opened from Profile changes nothing. */
    data object SurveyInviteClosed : AppAction
}

/** Where the survey invitation came from: shown by itself after a few opens, or from Profile. */
enum class SurveyInviteSource(val analyticsName: String) { INVITE("invite"), PROFILE("profile") }

data class AppState(
    val status: AppStatus = AppStatus(),
    val updateDismissed: Boolean = false,
    /** Null until the links are known; the survey is offered only with a link. */
    val links: AppLinks? = null,
    val surveyProgress: SurveyProgress = SurveyProgress(finished = true),
    /** The invitation on screen, if any. */
    val surveyInvite: SurveyInviteSource? = null,
    /** The invitation shown by itself was answered in this session. */
    val surveyAnswered: Boolean = false,
) {
    val showUpdate: Boolean
        get() = status.update == UpdatePolicy.REQUIRED || (status.update == UpdatePolicy.RECOMMENDED && !updateDismissed)

    val surveyAvailable: Boolean get() = links?.surveyUrl != null

    /** The invitation to show: Profile's, or the one due after a few opens, unless something more important is up. */
    val visibleSurveyInvite: SurveyInviteSource?
        get() = when {
            !surveyAvailable || status.isSuspended -> null
            surveyInvite != null -> surveyInvite
            surveyProgress.due && !surveyAnswered && !showUpdate -> SurveyInviteSource.INVITE
            else -> null
        }
}

/** App-wide switches from Remote Config (update prompts, the suspension notice) and the research survey invitation. */
class AppViewModel(
    appStatus: AppStatusRepository,
    private val settings: SettingsRepository,
    links: AppLinksRepository,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<AppState, AppAction>(AppState()) {

    init {
        appStatus.observe()
            .onEach { setState { copy(status = it) } }
            .catch { log.e(it) { "App status unavailable" } }
            .launchIn(viewModelScope)
        settings.surveyProgress
            .onEach { setState { copy(surveyProgress = it) } }
            .catch { log.e(it) { "Survey progress unavailable" } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            safely { settings.recordAppOpen() }
            // Links fall back to the built-in ones, so the survey still works offline
            val published = try {
                links.links()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Links unavailable" }
                AppLinks()
            }
            setState { copy(links = published) }
        }
    }

    override fun onAction(action: AppAction) {
        when (action) {
            AppAction.DismissUpdate -> setState { copy(updateDismissed = true) }
            AppAction.ShowSurveyInvite -> setState { copy(surveyInvite = SurveyInviteSource.PROFILE) }
            AppAction.SurveyStarted -> {
                currentState.visibleSurveyInvite?.let { analytics.log(AnalyticsEvent.SurveyOpened(it.analyticsName)) }
                setState { copy(surveyInvite = null, surveyAnswered = true) }
                viewModelScope.launch { safely { settings.finishSurveyInvite() } }
            }
            AppAction.SurveyInviteClosed -> {
                val fromProfile = currentState.visibleSurveyInvite == SurveyInviteSource.PROFILE
                // Either way, not asked again in this session
                setState { copy(surveyInvite = null, surveyAnswered = true) }
                if (!fromProfile) viewModelScope.launch { safely { settings.postponeSurvey() } }
            }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "Saving survey progress failed" }
    }

    private companion object {
        val log = Logger.withTag("App")
    }
}
