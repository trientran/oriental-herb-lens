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

    /** The picker closed: with an ID token, or null when the user backed out. */
    data class GoogleFinished(val idToken: String?) : SignInAction

    /** The picker itself failed (no Google account, no Play services, offline). */
    data object GoogleFailed : SignInAction
}

data class SignInState(
    val isSignedIn: Boolean = false,
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
)

class SignInViewModel(private val auth: AuthRepository) : MviViewModel<SignInState, SignInAction>(SignInState()) {

    init {
        auth.observeUserId().onEach { setState { copy(isSignedIn = it != null) } }.launchIn(viewModelScope)
    }

    override fun onAction(action: SignInAction) {
        when (action) {
            SignInAction.GoogleStarted -> setState { copy(isWorking = true, hasError = false) }
            SignInAction.GoogleFailed -> setState { copy(isWorking = false, hasError = true) }
            is SignInAction.GoogleFinished -> {
                val token = action.idToken ?: return setState { copy(isWorking = false) }
                viewModelScope.launch {
                    try {
                        auth.signInWithGoogle(token)
                        setState { copy(isWorking = false) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.e(e) { "Firebase sign-in failed" }
                        setState { copy(isWorking = false, hasError = true) }
                    }
                }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("SignIn")
    }
}
