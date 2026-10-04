package com.uri.lee.dl.herbdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface SuggestNameAction {
    data class DraftChanged(val text: String) : SuggestNameAction
    data object Submit : SuggestNameAction
}

data class SuggestNameState(
    val herbId: Long,
    val currentName: String,
    val draft: String,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val error: Error? = null,
) {
    val canSubmit: Boolean
        get() = draft.isNotBlank() && draft.trim() != currentName.trim() && !isSubmitting && !isSubmitted

    data class Error(val exception: Exception)
}

/** Suggesting a Vietnamese name is the only edit users can make to a herb's text. */
class SuggestNameViewModel(
    savedState: SavedStateHandle,
    private val contributions: ContributionRepository,
) : MviViewModel<SuggestNameState, SuggestNameAction>(
    savedState.get<String>(ARG_CURRENT_NAME).orEmpty().let { current ->
        SuggestNameState(herbId = requireNotNull(savedState.get<Long>(ARG_HERB_ID)), currentName = current, draft = current)
    }
) {

    override fun onAction(action: SuggestNameAction) {
        when (action) {
            is SuggestNameAction.DraftChanged -> setState { copy(draft = action.text, error = null) }
            SuggestNameAction.Submit -> submit()
        }
    }

    private fun submit() {
        if (!currentState.canSubmit) return
        setState { copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                contributions.suggestVietnameseName(currentState.herbId, currentState.draft)
                setState { copy(isSubmitting = false, isSubmitted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(isSubmitting = false, error = SuggestNameState.Error(e)) }
            }
        }
    }

    companion object {
        // Navigation argument names (edit_herb_details.xml)
        const val ARG_HERB_ID = "herbId"
        const val ARG_CURRENT_NAME = "oldValue"
    }
}
