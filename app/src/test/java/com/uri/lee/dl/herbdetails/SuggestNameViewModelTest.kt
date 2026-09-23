package com.uri.lee.dl.herbdetails

import androidx.lifecycle.SavedStateHandle
import com.uri.lee.dl.fakes.FakeContributionRepository
import com.uri.lee.dl.herbdetails.SuggestNameViewModel.Companion.ARG_CURRENT_NAME
import com.uri.lee.dl.herbdetails.SuggestNameViewModel.Companion.ARG_HERB_ID
import com.uri.lee.dl.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SuggestNameViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val contributions = FakeContributionRepository()
    private val viewModel = SuggestNameViewModel(
        SavedStateHandle(mapOf(ARG_HERB_ID to 5L, ARG_CURRENT_NAME to "Đinh lăng")),
        contributions,
    )

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

        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.canSubmit)
    }
}
