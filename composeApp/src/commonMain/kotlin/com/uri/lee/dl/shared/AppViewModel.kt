package com.uri.lee.dl.shared

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.model.AppStatus
import com.uri.lee.dl.domain.model.UpdatePolicy
import com.uri.lee.dl.domain.repository.AppStatusRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed interface AppAction {
    /** The user put off a recommended update. */
    data object DismissUpdate : AppAction
}

data class AppState(val status: AppStatus = AppStatus(), val updateDismissed: Boolean = false) {
    val showUpdate: Boolean
        get() = status.update == UpdatePolicy.REQUIRED || (status.update == UpdatePolicy.RECOMMENDED && !updateDismissed)
}

/** App-wide switches from Remote Config: update prompts and the suspension notice. */
class AppViewModel(appStatus: AppStatusRepository) : MviViewModel<AppState, AppAction>(AppState()) {

    init {
        appStatus.observe()
            .onEach { setState { copy(status = it) } }
            .catch { Logger.withTag("AppStatus").e(it) { "App status unavailable" } }
            .launchIn(viewModelScope)
    }

    override fun onAction(action: AppAction) {
        when (action) {
            AppAction.DismissUpdate -> setState { copy(updateDismissed = true) }
        }
    }
}
