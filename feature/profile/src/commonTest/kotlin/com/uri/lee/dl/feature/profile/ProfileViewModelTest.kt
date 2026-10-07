package com.uri.lee.dl.feature.profile

import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.model.Citation
import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeCitationRepository
import com.uri.lee.dl.testing.fakes.FakeSettingsRepository
import com.uri.lee.dl.testing.testAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileViewModelTest : MainDispatcherTest() {

    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val citations = FakeCitationRepository()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun log(event: AnalyticsEvent) { events += event }
        override fun screen(name: String) = Unit
    }
    private fun viewModel() = ProfileViewModel(auth, settings, citations, testAppInfo, analytics)

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
}
