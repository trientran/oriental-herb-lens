package com.uri.lee.dl.shared

import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.model.AppLinks
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.model.SurveyProgress
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.domain.repository.AppStatusRepository
import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAppLinksRepository
import com.uri.lee.dl.testing.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppViewModelTest : MainDispatcherTest() {

    private val status = MutableStateFlow(AppStatus())
    private val settings = FakeSettingsRepository()
    private val links = FakeAppLinksRepository()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun log(event: AnalyticsEvent) { events += event }
        override fun screen(name: String) = Unit
    }

    private fun viewModel() = AppViewModel(object : AppStatusRepository { override fun observe() = status }, settings, links, analytics)

    /** Opens the app [times] times; each view model is one opening. */
    private fun openedBefore(times: Int) {
        settings.surveyProgress.value = settings.surveyProgress.value.copy(appOpens = times)
    }

    @Test
    fun `each opening is counted`() {
        viewModel()

        assertEquals(1, settings.surveyProgress.value.appOpens)
    }

    @Test
    fun `the invitation waits for the third opening`() {
        openedBefore(1)
        assertNull(viewModel().state.value.visibleSurveyInvite)

        openedBefore(SurveyProgress.FIRST_ASK - 1)
        assertEquals(SurveyInviteSource.INVITE, viewModel().state.value.visibleSurveyInvite)
    }

    @Test
    fun `not now asks again five openings later and then never`() {
        openedBefore(2)
        viewModel().onAction(AppAction.SurveyInviteClosed)
        assertEquals(3 + SurveyProgress.ASK_AGAIN_AFTER, settings.surveyProgress.value.askAtOpen)

        openedBefore(3 + SurveyProgress.ASK_AGAIN_AFTER - 1)
        val again = viewModel()
        assertEquals(SurveyInviteSource.INVITE, again.state.value.visibleSurveyInvite)
        again.onAction(AppAction.SurveyInviteClosed)

        assertTrue(settings.surveyProgress.value.finished)
    }

    @Test
    fun `taking the survey ends the invitations and is counted`() {
        openedBefore(2)
        val viewModel = viewModel()

        viewModel.onAction(AppAction.SurveyStarted)

        assertTrue(settings.surveyProgress.value.finished)
        assertNull(viewModel.state.value.visibleSurveyInvite)
        assertEquals(mapOf("source" to "invite"), events.single().parameters)
    }

    @Test
    fun `closing the invitation from Profile doesn't postpone anything`() {
        val viewModel = viewModel()
        viewModel.onAction(AppAction.ShowSurveyInvite)
        assertEquals(SurveyInviteSource.PROFILE, viewModel.state.value.visibleSurveyInvite)

        viewModel.onAction(AppAction.SurveyInviteClosed)

        assertNull(viewModel.state.value.visibleSurveyInvite)
        assertEquals(SurveyProgress(appOpens = 1), settings.surveyProgress.value)
    }

    @Test
    fun `nothing is offered once the study has closed`() {
        links.published = AppLinks(surveyUrl = null)
        openedBefore(10)
        val viewModel = viewModel()

        viewModel.onAction(AppAction.ShowSurveyInvite)

        assertFalse(viewModel.state.value.surveyAvailable)
        assertNull(viewModel.state.value.visibleSurveyInvite)
    }

    @Test
    fun `an update prompt comes first`() {
        status.value = AppStatus(update = UpdatePolicy.RECOMMENDED)
        openedBefore(5)

        assertNull(viewModel().state.value.visibleSurveyInvite)
    }

    @Test
    fun `the survey opens in the app's language`() {
        val links = AppLinks()

        assertEquals("${AppLinks.DEFAULT_SURVEY_URL}?Q_Language=VI", links.surveyLink("vi"))
        assertEquals("${AppLinks.DEFAULT_SURVEY_URL}?Q_Language=EN", links.surveyLink("en"))
    }
}
