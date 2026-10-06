package com.uri.lee.dl.feature.profile

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
    private fun viewModel() = ProfileViewModel(auth, settings, citations, testAppInfo)

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
