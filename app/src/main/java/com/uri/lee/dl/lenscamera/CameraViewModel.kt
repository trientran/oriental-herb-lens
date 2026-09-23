package com.uri.lee.dl.lenscamera

import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

data class CameraState(val confidence: Float? = null)

sealed interface CameraAction {
    data class ConfidenceChanged(val value: Float) : CameraAction
}

class CameraViewModel(
    private val settings: SettingsRepository,
) : MviViewModel<CameraState, CameraAction>(CameraState()) {

    init {
        viewModelScope.launch {
            try {
                val saved = settings.scanSettings.first()
                setState { copy(confidence = saved.minConfidence) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onAction(action: CameraAction) {
        when (action) {
            is CameraAction.ConfidenceChanged -> {
                setState { copy(confidence = action.value) }
                viewModelScope.launch { settings.setMinConfidence(action.value) }
            }
        }
    }
}
