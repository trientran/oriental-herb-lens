package com.uri.lee.dl.lenscamera.livecamera

import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.ui.common.MviViewModel

data class LiveCameraState(val herbs: List<Herb> = emptyList())

/** The live camera has no user input to report; frames arrive through [analyzer]. */
sealed interface LiveCameraAction

class LiveCameraViewModel(
    private val recognizeHerbs: RecognizeHerbsUseCase,
) : MviViewModel<LiveCameraState, LiveCameraAction>(LiveCameraState()) {

    override fun onAction(action: LiveCameraAction) = Unit

    fun analyzer(confidence: Float): ImageAnalysis.Analyzer =
        ImageAnalyzer(viewModelScope, recognizeHerbs, confidence) { herbs -> setState { copy(herbs = herbs) } }
}
