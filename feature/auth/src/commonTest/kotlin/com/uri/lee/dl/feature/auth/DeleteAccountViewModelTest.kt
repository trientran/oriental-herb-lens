package com.uri.lee.dl.feature.auth

import com.uri.lee.dl.domain.repository.SignInProvider
import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeleteAccountViewModelTest : MainDispatcherTest() {

    @Test
    fun `a Google user confirms with Google and the account is deleted`() {
        val auth = FakeAuthRepository(uid = "google-alice")
        val viewModel = DeleteAccountViewModel(auth)
        assertEquals(SignInProvider.GOOGLE, viewModel.state.value.provider)

        viewModel.onAction(DeleteAccountAction.Started)
        viewModel.onAction(DeleteAccountAction.Confirmed(Proof.Google(GoogleCredential("alice"))))

        assertEquals(listOf("google-alice"), auth.deleted)
        assertTrue(viewModel.state.value.isDeleted)
        assertNull(auth.currentUserId)
    }

    @Test
    fun `an Apple user's tokens are revoked before the account is deleted`() {
        val auth = FakeAuthRepository(uid = "apple-bob")
        val revoked = mutableListOf<String>()
        val viewModel = DeleteAccountViewModel(auth)

        viewModel.onAction(DeleteAccountAction.Confirmed(Proof.Apple(AppleCredential("bob", "nonce", "code-1")) { revoked += it }))

        assertEquals(listOf("code-1"), revoked)
        assertEquals("apple-bob", auth.reauthenticatedAs)
        assertTrue(viewModel.state.value.isDeleted)
    }

    @Test
    fun `backing out of the confirming sign-in deletes nothing`() {
        val auth = FakeAuthRepository(uid = "google-alice")
        val viewModel = DeleteAccountViewModel(auth)

        viewModel.onAction(DeleteAccountAction.Started)
        viewModel.onAction(DeleteAccountAction.Confirmed(null))

        assertTrue(auth.deleted.isEmpty())
        assertFalse(viewModel.state.value.isWorking)
        assertFalse(viewModel.state.value.hasError)
    }

    @Test
    fun `a failed deletion is reported and the user stays signed in`() {
        val auth = FakeAuthRepository(uid = "google-alice").apply { failDelete = true }
        val viewModel = DeleteAccountViewModel(auth)

        viewModel.onAction(DeleteAccountAction.Confirmed(Proof.Google(GoogleCredential("alice"))))

        assertTrue(viewModel.state.value.hasError)
        assertFalse(viewModel.state.value.isDeleted)
        assertEquals("google-alice", auth.currentUserId)
    }
}
