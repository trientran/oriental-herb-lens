package com.uri.lee.dl.feature.auth

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.SignInProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** How the user proved again who they are, which Firebase requires before deleting an account. */
sealed interface Proof {
    data class Google(val credential: GoogleCredential) : Proof

    /** [revoke] tells Apple the app no longer uses the account (Apple requires it); null where unavailable. */
    data class Apple(val credential: AppleCredential, val revoke: (suspend (authorizationCode: String) -> Unit)?) : Proof

    /** No way to sign in again here, e.g. an Apple account on Android: works if the sign-in is recent. */
    data object None : Proof
}

sealed interface DeleteAccountAction {
    /** The confirming sign-in started. */
    data object Started : DeleteAccountAction

    /** The confirming sign-in finished: null when the user backed out. */
    data class Confirmed(val proof: Proof?) : DeleteAccountAction

    /** The confirming sign-in itself failed. */
    data object Failed : DeleteAccountAction
}

data class DeleteAccountState(
    /** Null once signed out (or deleted). */
    val provider: SignInProvider? = null,
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
    val isDeleted: Boolean = false,
)

/**
 * Deletes the user's account: the Firebase account, the personal details old versions kept in
 * Firestore and, for Apple accounts, the app's Apple tokens. Photos already shared stay on the
 * species pages, no longer linked to an account.
 */
class DeleteAccountViewModel(private val auth: AuthRepository) :
    MviViewModel<DeleteAccountState, DeleteAccountAction>(DeleteAccountState(provider = auth.signInProvider)) {

    override fun onAction(action: DeleteAccountAction) {
        when (action) {
            DeleteAccountAction.Started -> setState { copy(isWorking = true, hasError = false) }
            DeleteAccountAction.Failed -> setState { copy(isWorking = false, hasError = true) }
            is DeleteAccountAction.Confirmed -> {
                val proof = action.proof ?: return setState { copy(isWorking = false) }
                delete(proof)
            }
        }
    }

    private fun delete(proof: Proof) {
        setState { copy(isWorking = true, hasError = false) }
        viewModelScope.launch {
            try {
                when (proof) {
                    is Proof.Google -> auth.reauthenticateWithGoogle(proof.credential.idToken, proof.credential.accessToken)
                    is Proof.Apple -> {
                        auth.reauthenticateWithApple(proof.credential.idToken, proof.credential.rawNonce)
                        val code = proof.credential.authorizationCode
                        if (code != null) proof.revoke?.invoke(code)
                    }
                    Proof.None -> Unit
                }
                auth.deleteAccount()
                setState { copy(isWorking = false, isDeleted = true, provider = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Account not deleted" }
                setState { copy(isWorking = false, hasError = true) }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("DeleteAccount")
    }
}
