package com.uri.lee.dl.feature.auth

import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInViewModelTest : MainDispatcherTest() {

    private val auth = FakeAuthRepository(uid = null)

    @Test
    fun `a Google token signs the user in`() {
        val viewModel = SignInViewModel(auth)

        viewModel.onAction(SignInAction.GoogleStarted)
        viewModel.onAction(SignInAction.GoogleFinished(GoogleCredential("token")))

        assertTrue(viewModel.state.value.isSignedIn)
        assertFalse(viewModel.state.value.isWorking)
    }

    @Test
    fun `an Apple credential signs the user in`() {
        val viewModel = SignInViewModel(auth)

        viewModel.onAction(SignInAction.AppleFinished(AppleCredential("token", "nonce")))

        assertTrue(viewModel.state.value.isSignedIn)
    }

    @Test
    fun `backing out of the picker is not an error`() {
        val viewModel = SignInViewModel(auth)

        viewModel.onAction(SignInAction.GoogleStarted)
        viewModel.onAction(SignInAction.GoogleFinished(null))

        assertFalse(viewModel.state.value.isSignedIn)
        assertFalse(viewModel.state.value.hasError)
        assertFalse(viewModel.state.value.isWorking)
    }

    @Test
    fun `a blocked sign-in window is reported as such`() {
        val viewModel = SignInViewModel(auth)

        viewModel.onAction(SignInAction.GoogleStarted)
        viewModel.onAction(SignInAction.WindowBlocked)

        assertFalse(viewModel.state.value.isWorking)
        assertTrue(viewModel.state.value.windowBlocked)

        // Trying again clears it
        viewModel.onAction(SignInAction.GoogleStarted)
        assertFalse(viewModel.state.value.windowBlocked)
    }

    @Test
    fun `a rejected token is reported`() {
        auth.failSignIn = true
        val viewModel = SignInViewModel(auth)

        viewModel.onAction(SignInAction.GoogleFinished(GoogleCredential("token")))

        assertTrue(viewModel.state.value.hasError)
    }
}
