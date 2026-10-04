package com.uri.lee.dl

import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.repository.AppStatusRepository
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

data class MainState(
    /** Null until known; false sends the user to sign in. */
    val isSignedIn: Boolean? = null,
    val status: AppStatus = AppStatus(),
)

/** The main screen reacts to state only; it sends no actions yet. */
sealed interface MainAction

class MainViewModel(
    auth: AuthRepository,
    appStatus: AppStatusRepository,
) : MviViewModel<MainState, MainAction>(MainState()) {

    init {
        auth.observeUserId()
            .onEach { uid -> setState { copy(isSignedIn = uid != null) } }
            .launchIn(viewModelScope)
        appStatus.observe()
            .onEach { status -> setState { copy(status = status) } }
            .catch { Timber.e(it, "App status unavailable") }
            .launchIn(viewModelScope)
    }

    override fun onAction(action: MainAction) = Unit
}
