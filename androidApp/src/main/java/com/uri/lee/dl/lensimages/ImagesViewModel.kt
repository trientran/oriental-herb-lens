package com.uri.lee.dl.lensimages

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.uri.lee.dl.MAX_IMAGE_DIMENSION_FOR_LABELING
import com.uri.lee.dl.core.ml.MlKitClassifierImage
import com.uri.lee.dl.data.platform.BitmapLoader
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.labeling.Herb
import com.uri.lee.dl.labeling.toHerbs
import com.uri.lee.dl.lensimages.ImagesState.Event
import com.uri.lee.dl.lensimages.ImagesState.Recognition
import com.uri.lee.dl.core.ui.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

sealed interface ImagesAction {
    data class ImagesPicked(val uris: List<Uri>) : ImagesAction
    data class ConfidenceChanged(val value: Float) : ImagesAction
    data object ClearAll : ImagesAction
}

class ImagesViewModel(
    private val recognizeHerbs: RecognizeHerbsUseCase,
    private val settings: SettingsRepository,
    private val bitmaps: BitmapLoader,
) : MviViewModel<ImagesState, ImagesAction>(ImagesState()) {

    private var processing: Job? = null

    init {
        viewModelScope.launch {
            try {
                val saved = settings.scanSettings.first()
                setState { copy(confidence = saved.minConfidence) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(event = Event.DataStoreError(e)) }
            }
        }
    }

    override fun onAction(action: ImagesAction) {
        when (action) {
            is ImagesAction.ImagesPicked -> {
                if (action.uris.isEmpty()) return
                setState { copy(imageUris = imageUris + action.uris) }
                label(action.uris)
            }
            is ImagesAction.ConfidenceChanged -> {
                setState { copy(confidence = action.value, recognitionList = emptyList()) }
                viewModelScope.launch { settings.setMinConfidence(action.value) }
                processing?.cancel()
                label(currentState.imageUris)
            }
            ImagesAction.ClearAll -> {
                processing?.cancel()
                setState { copy(imageUris = emptyList(), event = null, recognitionList = emptyList()) }
            }
        }
    }

    /** Labels [uris] one by one, appending a result row per image (empty when nothing matched). */
    private fun label(uris: List<Uri>) {
        val confidence = currentState.confidence ?: return
        val previous = processing
        processing = viewModelScope.launch {
            previous?.join() // keep result rows in the order images were added
            setState { copy(event = null) }
            for (uri in uris) {
                val herbs = try {
                    labelImage(uri, confidence) ?: continue
                } catch (e: CancellationException) {
                    throw e
                } catch (e: MlKitException) {
                    Timber.e(e)
                    setState { copy(event = Event.LabelingError(e)) }
                    continue
                } catch (e: Exception) {
                    Timber.e(e)
                    setState { copy(event = Event.Other(e)) }
                    continue
                }
                setState { copy(recognitionList = recognitionList + Recognition(fileUri = uri, herbs = herbs)) }
            }
        }
    }

    /** Null when the image couldn't be decoded (reported as an event). */
    private suspend fun labelImage(uri: Uri, confidence: Float): List<Herb>? {
        val bitmap = try {
            bitmaps.load(uri, MAX_IMAGE_DIMENSION_FOR_LABELING)
        } catch (e: IOException) {
            Timber.e(e)
            setState { copy(event = Event.BitmapError(e)) }
            null
        } ?: return null
        return recognizeHerbs(MlKitClassifierImage(InputImage.fromBitmap(bitmap, 0)), confidence).toHerbs()
    }
}

data class ImagesState(
    val imageUris: List<Uri> = emptyList(),
    val recognitionList: List<Recognition> = emptyList(),
    val confidence: Float? = null,
    val event: Event? = null,
) {
    data class Recognition(val fileUri: Uri, val herbs: List<Herb>)

    sealed interface Event {
        data class LabelingError(val exception: Exception) : Event
        data class BitmapError(val exception: Exception) : Event
        data class DataStoreError(val exception: Exception) : Event
        data class Other(val exception: Exception) : Event
    }
}
