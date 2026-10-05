package com.uri.lee.dl.feature.herbdetails

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface SuggestNameAction {
    data class DraftChanged(val text: String) : SuggestNameAction
    data object Submit : SuggestNameAction
}

data class SuggestNameState(
    val herbId: Long,
    val currentName: String,
    val draft: String,
    val isSignedIn: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val hasError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = isSignedIn && draft.isNotBlank() && draft.trim() != currentName.trim() &&
            draft.trim().length <= MAX_LENGTH && !isSubmitting && !isSubmitted

    companion object {
        /** Matches the security rules. */
        const val MAX_LENGTH = 100
    }
}

/** Suggesting a Vietnamese name is the only edit users can make to a herb's text; the admin reviews it. */
class SuggestNameViewModel(
    herbId: Long,
    currentName: String,
    private val contributions: ContributionRepository,
    auth: AuthRepository,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<SuggestNameState, SuggestNameAction>(SuggestNameState(herbId, currentName, draft = currentName)) {

    init {
        auth.observeUserId().onEach { uid -> setState { copy(isSignedIn = uid != null) } }.launchIn(viewModelScope)
    }

    override fun onAction(action: SuggestNameAction) {
        when (action) {
            is SuggestNameAction.DraftChanged -> setState { copy(draft = action.text, hasError = false) }
            SuggestNameAction.Submit -> submit()
        }
    }

    private fun submit() {
        if (!currentState.canSubmit) return
        setState { copy(isSubmitting = true, hasError = false) }
        viewModelScope.launch {
            try {
                contributions.suggestVietnameseName(currentState.herbId, currentState.draft)
                analytics.log(AnalyticsEvent.NameSuggested(currentState.herbId))
                setState { copy(isSubmitting = false, isSubmitted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Name suggestion failed" }
                setState { copy(isSubmitting = false, hasError = true) }
            }
        }
    }

    private companion object {
        val log = Logger.withTag("SuggestName")
    }
}
