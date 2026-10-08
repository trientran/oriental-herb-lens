package com.uri.lee.dl.feature.herbdetails

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.model.NameLanguages
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface SuggestNameAction {
    data class LanguageChanged(val language: String) : SuggestNameAction
    data class DraftChanged(val text: String) : SuggestNameAction
    data object Submit : SuggestNameAction

    /** The sheet opened again: after a name was sent, start a new suggestion. */
    data object Opened : SuggestNameAction
}

data class SuggestNameState(
    val herbId: Long,
    /** The language of the name, an ISO 639-1 code from [NameLanguages.ENABLED]. */
    val language: String,
    /** The herb's names already in the catalog, by language, so they aren't suggested again. */
    val listed: Map<String, List<String>> = emptyMap(),
    val draft: String = "",
    val isSignedIn: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val hasError: Boolean = false,
) {
    val languages: List<String> get() = NameLanguages.ENABLED

    /** The catalog's names in the chosen language. */
    val listedInLanguage: List<String> get() = listed[language].orEmpty()

    val canSubmit: Boolean
        get() = isSignedIn && draft.isNotBlank() && draft.trim().length <= MAX_LENGTH &&
            listedInLanguage.none { it.equals(draft.trim(), ignoreCase = true) } &&
            language in languages && !isSubmitting && !isSubmitted

    companion object {
        /** Matches the security rules. */
        const val MAX_LENGTH = 100
    }
}

/**
 * Suggesting a common (vernacular) name, in one of the app's languages, is the only edit users can
 * make to a herb's text; the admin reviews it before it reaches the catalog.
 */
class SuggestNameViewModel(
    herbId: Long,
    listed: Map<String, List<String>>,
    language: String,
    private val contributions: ContributionRepository,
    auth: AuthRepository,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<SuggestNameState, SuggestNameAction>(
    SuggestNameState(herbId, language.takeIf { it in NameLanguages.ENABLED } ?: NameLanguages.ENABLED.first(), listed),
) {

    init {
        auth.observeUserId().onEach { uid -> setState { copy(isSignedIn = uid != null) } }.launchIn(viewModelScope)
    }

    override fun onAction(action: SuggestNameAction) {
        when (action) {
            is SuggestNameAction.LanguageChanged ->
                if (action.language in NameLanguages.ENABLED) setState { copy(language = action.language, hasError = false) }
            is SuggestNameAction.DraftChanged -> setState { copy(draft = action.text, hasError = false) }
            SuggestNameAction.Submit -> submit()
            SuggestNameAction.Opened -> if (currentState.isSubmitted) setState { copy(draft = "", isSubmitted = false, hasError = false) }
        }
    }

    private fun submit() {
        if (!currentState.canSubmit) return
        val (herbId, language) = currentState.herbId to currentState.language
        setState { copy(isSubmitting = true, hasError = false) }
        viewModelScope.launch {
            try {
                contributions.suggestName(herbId, language, currentState.draft)
                analytics.log(AnalyticsEvent.NameSuggested(herbId, language))
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
