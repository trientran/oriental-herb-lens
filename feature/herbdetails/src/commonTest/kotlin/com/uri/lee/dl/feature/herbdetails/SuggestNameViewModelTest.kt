package com.uri.lee.dl.feature.herbdetails

import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeContributionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestNameViewModelTest : MainDispatcherTest() {

    private val contributions = FakeContributionRepository()
    private val auth = FakeAuthRepository()
    private val viewModel by lazy { SuggestNameViewModel(5L, "Đinh lăng", contributions, auth) }

    @Test
    fun `starts from the current name and can't submit it unchanged`() {
        assertEquals("Đinh lăng", viewModel.state.value.draft)
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `blank or whitespace-only changes can't be submitted`() {
        viewModel.onAction(SuggestNameAction.DraftChanged("   "))
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onAction(SuggestNameAction.DraftChanged(" Đinh lăng "))
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `names longer than the rules allow can't be submitted`() {
        viewModel.onAction(SuggestNameAction.DraftChanged("a".repeat(SuggestNameState.MAX_LENGTH + 1)))
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `a new name is submitted once`() {
        viewModel.onAction(SuggestNameAction.DraftChanged("Cây gỏi cá"))
        viewModel.onAction(SuggestNameAction.Submit)
        viewModel.onAction(SuggestNameAction.Submit)

        assertTrue(viewModel.state.value.isSubmitted)
        assertEquals(listOf(5L to "Cây gỏi cá"), contributions.names)
    }

    @Test
    fun `a failed submission is reported and can be retried`() {
        contributions.fail = true
        viewModel.onAction(SuggestNameAction.DraftChanged("Cây gỏi cá"))

        viewModel.onAction(SuggestNameAction.Submit)

        assertTrue(viewModel.state.value.hasError)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `signed out users are asked to sign in first`() {
        auth.userId.value = null
        viewModel.onAction(SuggestNameAction.DraftChanged("Cây gỏi cá"))

        assertFalse(viewModel.state.value.isSignedIn)
        assertFalse(viewModel.state.value.canSubmit)
    }
}
