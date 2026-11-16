package com.uri.lee.dl.feature.profile

import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.model.AppLinks
import com.uri.lee.dl.domain.model.Badge
import com.uri.lee.dl.domain.model.Citation
import com.uri.lee.dl.domain.model.Contribution
import com.uri.lee.dl.domain.model.ContributionKind
import com.uri.lee.dl.domain.model.ContributionSummary
import com.uri.lee.dl.core.common.Clock
import com.uri.lee.dl.domain.sharing.SharingProblem
import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAppLinksRepository
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeCitationRepository
import com.uri.lee.dl.testing.fakes.FakeContributionHistory
import com.uri.lee.dl.testing.fakes.FakeSettingsRepository
import com.uri.lee.dl.testing.testAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileViewModelTest : MainDispatcherTest() {

    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val citations = FakeCitationRepository()
    private val links = FakeAppLinksRepository()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun log(event: AnalyticsEvent) { events += event }
        override fun screen(name: String) = Unit
    }
    private val history = FakeContributionHistory()
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private fun viewModel() = ProfileViewModel(auth, settings, citations, links, testAppInfo, history, Clock { now }, analytics)

    private fun photo(id: String, species: Long, daysAgo: Int) =
        Contribution(id, ContributionKind.PHOTO, speciesKey = species, url = id, createdAtMillis = now - daysAgo * day)

    @Test
    fun `contributions show as totals and reviewed ones earn badges`() {
        history.contributions.value = listOf(photo("a", 1, 10), photo("b", 1, 1), Contribution("n", ContributionKind.NAME, speciesKey = 1, createdAtMillis = now))
        val viewModel = viewModel()

        assertEquals(ContributionSummary(photos = 2, species = 1, names = 1), viewModel.state.value.contributions)
        assertEquals(setOf(Badge.FIRST_PHOTO), viewModel.state.value.badges)
        assertEquals(setOf(Badge.FIRST_PHOTO), viewModel.state.value.newBadges)

        viewModel.onAction(ProfileAction.BadgesSeen)
        assertEquals(emptySet(), viewModel.state.value.newBadges)
    }

    @Test
    fun `a photo taken down no longer counts`() {
        history.contributions.value = listOf(photo("a", 1, 10))
        history.removed += "a"
        val viewModel = viewModel()

        assertEquals(0, viewModel.state.value.contributions?.photos)
        assertEquals(emptySet(), viewModel.state.value.badges)
    }

    @Test
    fun `signed out there are no contributions to show`() {
        history.contributions.value = listOf(photo("a", 1, 10))
        val viewModel = viewModel()

        viewModel.onAction(ProfileAction.SignOut)

        assertEquals(null, viewModel.state.value.contributions)
    }

    @Test
    fun `a credit name is checked before it is saved`() {
        val viewModel = viewModel()

        viewModel.onAction(ProfileAction.SaveCreditName("me@example.com"))
        assertEquals(SharingProblem.CONTACT_DETAILS, viewModel.state.value.creditProblem)
        assertEquals(null, settings.creditName.value)

        viewModel.onAction(ProfileAction.SaveCreditName("  Lan  "))
        assertEquals(null, viewModel.state.value.creditProblem)
        assertEquals("Lan", viewModel.state.value.creditName)

        viewModel.onAction(ProfileAction.SaveCreditName(""))
        assertEquals(null, viewModel.state.value.creditName)
    }

    @Test
    fun `copying a citation is counted with its link`() {
        citations.published = listOf(Citation("Paper one."), Citation("Paper two.", "https://doi.org/10.0/x"))
        val viewModel = viewModel()

        viewModel.onAction(ProfileAction.CitationCopied(1))

        assertEquals(mapOf("position" to 2, "url" to "https://doi.org/10.0/x"), events.single().parameters)
    }

    @Test
    fun `signing out updates the account state`() {
        val viewModel = viewModel()
        assertEquals(true, viewModel.state.value.isSignedIn)

        viewModel.onAction(ProfileAction.SignOut)

        assertEquals(false, viewModel.state.value.isSignedIn)
    }

    @Test
    fun `confidence is saved within the allowed range`() {
        val viewModel = viewModel()

        viewModel.onAction(ProfileAction.SetMinConfidence(0.99f))

        assertEquals(ProfileViewModel.MAX_CONFIDENCE, viewModel.state.value.scanSettings.minConfidence)
    }

    @Test
    fun `published citations are shown`() {
        citations.published = listOf(Citation("Tran, T. (2027). Training on the edge.", "https://doi.org/10.0/x"))

        assertEquals(citations.published, viewModel().state.value.citations)
    }

    @Test
    fun `no citations when they can't be fetched`() {
        citations.fail = true

        assertEquals(emptyList(), viewModel().state.value.citations)
    }

    @Test
    fun `published links replace the built-in ones`() {
        links.published = AppLinks(listOf("new@example.org"), "https://example.org/data")

        assertEquals(links.published, viewModel().state.value.links)
    }

    @Test
    fun `built-in links when they can't be fetched`() {
        links.fail = true

        assertEquals(AppLinks(), viewModel().state.value.links)
    }

    @Test
    fun `opening the dataset is counted`() {
        viewModel().onAction(ProfileAction.DatasetOpened)

        assertEquals("open_dataset", events.single().name)
    }
}
