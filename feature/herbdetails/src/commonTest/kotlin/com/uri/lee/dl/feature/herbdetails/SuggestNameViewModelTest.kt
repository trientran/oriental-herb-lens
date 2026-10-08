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
    private val listed = mapOf("vi" to listOf("Đinh lăng"), "en" to listOf("Ming aralia"))
    private val viewModel by lazy { SuggestNameViewModel(5L, listed, "vi", contributions, auth) }

    @Test
    fun `starts empty in the given language and shows the names already listed`() {
        assertEquals("vi", viewModel.state.value.language)
        assertEquals("", viewModel.state.value.draft)
        assertEquals(listOf("Đinh lăng"), viewModel.state.value.listedInLanguage)
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `a name already listed in that language can't be suggested`() {
        viewModel.onAction(SuggestNameAction.DraftChanged(" đinh LĂNG "))
        assertFalse(viewModel.state.value.canSubmit)

        // Listed in Vietnamese, but new in English
        viewModel.onAction(SuggestNameAction.LanguageChanged("en"))
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `blank names and names longer than the rules allow can't be submitted`() {
        viewModel.onAction(SuggestNameAction.DraftChanged("   "))
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onAction(SuggestNameAction.DraftChanged("a".repeat(SuggestNameState.MAX_LENGTH + 1)))
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `only the offered languages can be chosen`() {
        viewModel.onAction(SuggestNameAction.LanguageChanged("zh"))
        assertEquals("vi", viewModel.state.value.language)

        val unoffered = SuggestNameViewModel(5L, listed, "zh", contributions, auth)
        assertEquals("vi", unoffered.state.value.language)
    }

    @Test
    fun `a new name is submitted once with its language`() {
        viewModel.onAction(SuggestNameAction.LanguageChanged("en"))
        viewModel.onAction(SuggestNameAction.DraftChanged("Ming aralia tree"))
        viewModel.onAction(SuggestNameAction.Submit)
        viewModel.onAction(SuggestNameAction.Submit)

        assertTrue(viewModel.state.value.isSubmitted)
        assertEquals(listOf(Triple(5L, "en", "Ming aralia tree")), contributions.names)
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
