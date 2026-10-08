package com.uri.lee.dl.feature.auth

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface SignInAction {
    /** The platform's Google account picker opened. */
    data object GoogleStarted : SignInAction

    /** The picker closed: with a credential, or null when the user backed out. */
    data class GoogleFinished(val credential: GoogleCredential?) : SignInAction

    /** The picker itself failed (no Google account, no Play services, offline). */
    data object GoogleFailed : SignInAction

    /** The browser blocked the sign-in window (the web). */
    data object WindowBlocked : SignInAction

    /** Apple's sheet closed: with a credential, or null when the user backed out. */
    data class AppleFinished(val credential: AppleCredential?) : SignInAction
}

/** What Google's account picker returns; iOS also gives an access token, which Firebase needs there. */
data class GoogleCredential(val idToken: String, val accessToken: String? = null)

/**
 * What Sign in with Apple returns: the identity token, the unhashed nonce its request was made
 * with, and a one-off authorization code, which revokes the app's tokens when the account is deleted.
 */
data class AppleCredential(val idToken: String, val rawNonce: String, val authorizationCode: String? = null)

data class SignInState(
    val isSignedIn: Boolean = false,
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
    /** The error is a blocked sign-in window, which the user can allow. */
    val windowBlocked: Boolean = false,
)

class SignInViewModel(private val auth: AuthRepository) : MviViewModel<SignInState, SignInAction>(SignInState()) {

    init {
        auth.observeUserId().onEach { setState { copy(isSignedIn = it != null) } }.launchIn(viewModelScope)
    }

    override fun onAction(action: SignInAction) {
        when (action) {
            SignInAction.GoogleStarted -> setState { copy(isWorking = true, hasError = false, windowBlocked = false) }
            SignInAction.GoogleFailed -> setState { copy(isWorking = false, hasError = true) }
            SignInAction.WindowBlocked -> setState { copy(isWorking = false, hasError = true, windowBlocked = true) }
            is SignInAction.GoogleFinished -> {
                val credential = action.credential ?: return setState { copy(isWorking = false) }
                signIn { auth.signInWithGoogle(credential.idToken, credential.accessToken) }
            }
            is SignInAction.AppleFinished -> {
                val credential = action.credential ?: return setState { copy(isWorking = false) }
                signIn { auth.signInWithApple(credential.idToken, credential.rawNonce) }
            }
        }
    }

    private fun signIn(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                setState { copy(isWorking = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Firebase sign-in failed" }
                setState { copy(isWorking = false, hasError = true) }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("SignIn")
    }
}
