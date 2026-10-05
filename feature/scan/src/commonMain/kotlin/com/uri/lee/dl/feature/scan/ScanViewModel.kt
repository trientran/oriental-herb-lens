package com.uri.lee.dl.feature.scan

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.ml.ReadPhoto
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.RecognizedHerb
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.usecase.IdentifyPlantsUseCase
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Whole view identifies everything in sight; Pick a plant finds each plant and identifies one at a time. */
enum class ScanMode { WHOLE_VIEW, PICK_PLANT }

sealed interface ScanSource {
    data object Camera : ScanSource

    /** One photo; [aspect] (width / height) once it has been read. */
    data class Photo(val uri: String, val aspect: Float? = null) : ScanSource

    /** Several photos, each identified as a whole. */
    data class Photos(val items: List<BatchItem>) : ScanSource
}

/** [herbs] is null while the photo is being identified; [failed] when it couldn't be read. */
data class BatchItem(val uri: String, val herbs: List<RecognizedHerb>? = null, val failed: Boolean = false)

/** A plant shown on the camera view or the photo, which the user can pick. */
data class ShownObject(val id: Int, val region: Region)

sealed interface ScanAction {
    data class SetMode(val mode: ScanMode) : ScanAction
    data class SelectObject(val id: Int) : ScanAction
    data class PhotosPicked(val photos: List<LocalImage>) : ScanAction
    data object BackToCamera : ScanAction
}

data class ScanState(
    val mode: ScanMode = ScanMode.WHOLE_VIEW,
    val source: ScanSource = ScanSource.Camera,
    val objects: List<ShownObject> = emptyList(),
    val selectedId: Int? = null,
    /** For the whole view, or the selected plant; never below [minConfidence]. */
    val results: List<RecognizedHerb> = emptyList(),
    /** Width / height of the latest camera frame, to place [objects] over the preview. */
    val frameAspect: Float? = null,
    /** A photo is being read and identified. */
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
    val minConfidence: Float = ScanSettings.DEFAULT_MIN_CONFIDENCE,
)

/**
 * The Identify screen. Camera frames arrive through [analyzeFrame], one at a time; picked photos
 * through [ScanAction.PhotosPicked]. Everything is classified on the device.
 */
class ScanViewModel(
    private val recognizeHerbs: RecognizeHerbsUseCase,
    private val identifyPlants: IdentifyPlantsUseCase,
    private val objectFinder: ObjectFinder,
    private val photoReader: PhotoReader,
    private val settings: SettingsRepository,
) : MviViewModel<ScanState, ScanAction>(ScanState()) {

    private var photo: ReadPhoto? = null
    private var photoResults: Map<Int, List<RecognizedHerb>> = emptyMap()
    private var photoJob: Job? = null

    init {
        settings.scanSettings
            .onEach { setState { copy(minConfidence = it.minConfidence) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val pick = runCatching { settings.scanSettings.first().detectObjectsInSingleImage }.getOrDefault(false)
            setState { copy(mode = if (pick) ScanMode.PICK_PLANT else ScanMode.WHOLE_VIEW) }
        }
    }

    override fun onAction(action: ScanAction) {
        when (action) {
            is ScanAction.SetMode -> {
                if (action.mode == currentState.mode) return
                setState { copy(mode = action.mode, objects = emptyList(), selectedId = null, results = emptyList()) }
                viewModelScope.launch { runCatching { settings.setDetectObjectsInSingleImage(action.mode == ScanMode.PICK_PLANT) } }
                if (currentState.source is ScanSource.Photo) identifyPhoto()
            }
            is ScanAction.SelectObject -> setState {
                copy(selectedId = action.id, results = if (source is ScanSource.Photo) photoResults[action.id].orEmpty() else results)
            }
            is ScanAction.PhotosPicked -> photosPicked(action.photos)
            ScanAction.BackToCamera -> {
                photoJob?.cancel()
                photo = null
                setState { copy(source = ScanSource.Camera, objects = emptyList(), selectedId = null, results = emptyList(), isWorking = false, hasError = false) }
            }
        }
    }

    /**
     * Identifies one camera frame. The camera waits for this to return before sending the next,
     * so frames never pile up.
     */
    suspend fun analyzeFrame(frame: ClassifierImage, aspect: Float) {
        val state = currentState
        if (state.source != ScanSource.Camera) return
        try {
            when (state.mode) {
                ScanMode.WHOLE_VIEW -> {
                    val results = recognizeHerbs(frame, state.minConfidence, MAX_RESULTS)
                    setState { if (mode == ScanMode.WHOLE_VIEW && source == ScanSource.Camera) copy(results = results, frameAspect = aspect) else this }
                }
                ScanMode.PICK_PLANT -> {
                    val found = objectFinder.find(frame, fromCamera = true)
                    val shown = found.mapIndexed { i, it -> ShownObject(it.trackingId ?: -(i + 1), it.region) }
                    // Keep the user's choice while it's in view; otherwise the biggest plant
                    val selected = state.selectedId?.takeIf { id -> shown.any { it.id == id } } ?: shown.maxByOrNull { it.region.area }?.id
                    val results = found.getOrNull(shown.indexOfFirst { it.id == selected })
                        ?.let { recognizeHerbs(it.image, state.minConfidence, MAX_RESULTS) }
                        .orEmpty()
                    setState {
                        if (mode == ScanMode.PICK_PLANT && source == ScanSource.Camera) {
                            copy(objects = shown, selectedId = selected, results = results, frameAspect = aspect)
                        } else {
                            this
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Frame not identified" } // the next frame tries again
        }
    }

    private fun photosPicked(photos: List<LocalImage>) {
        if (photos.isEmpty()) return
        photoJob?.cancel()
        photo = null
        if (photos.size == 1) {
            setState { copy(source = ScanSource.Photo(photos.single().uri), objects = emptyList(), selectedId = null, results = emptyList()) }
            photoJob = viewModelScope.launch {
                photo = photoReader.read(photos.single())
                if (photo == null) setState { copy(hasError = true) } else identifyPhoto()
            }
        } else {
            setState { copy(source = ScanSource.Photos(photos.map { BatchItem(it.uri) }), results = emptyList(), objects = emptyList()) }
            photoJob = viewModelScope.launch { identifyBatch(photos) }
        }
    }

    private fun identifyPhoto() {
        val read = photo ?: return
        val minConfidence = currentState.minConfidence
        photoJob?.cancel()
        photoJob = viewModelScope.launch {
            setState {
                copy(
                    source = (source as? ScanSource.Photo)?.copy(aspect = read.width.toFloat() / read.height) ?: source,
                    isWorking = true, hasError = false, objects = emptyList(), selectedId = null, results = emptyList(),
                )
            }
            try {
                when (currentState.mode) {
                    ScanMode.WHOLE_VIEW -> {
                        val results = recognizeHerbs(read.image, minConfidence, MAX_RESULTS)
                        setState { copy(results = results, isWorking = false) }
                    }
                    ScanMode.PICK_PLANT -> {
                        val plants = identifyPlants(read.image, minConfidence, MAX_RESULTS)
                        photoResults = plants.withIndex().associate { (i, plant) -> i to plant.herbs }
                        setState {
                            copy(
                                objects = plants.mapIndexed { i, plant -> ShownObject(i, plant.region) },
                                selectedId = if (plants.isEmpty()) null else 0,
                                results = photoResults[0].orEmpty(),
                                isWorking = false,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Photo not identified" }
                setState { copy(isWorking = false, hasError = true) }
            }
        }
    }

    private suspend fun identifyBatch(photos: List<LocalImage>) {
        val minConfidence = currentState.minConfidence
        photos.forEachIndexed { index, picked ->
            val item = try {
                val read = photoReader.read(picked)
                if (read == null) BatchItem(picked.uri, failed = true)
                else BatchItem(picked.uri, herbs = recognizeHerbs(read.image, minConfidence, MAX_RESULTS))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Photo not identified" }
                BatchItem(picked.uri, failed = true)
            }
            setState {
                val batch = source as? ScanSource.Photos ?: return@setState this
                copy(source = batch.copy(items = batch.items.toMutableList().also { it[index] = item }))
            }
        }
    }

    companion object {
        const val MAX_RESULTS = 3
        private val log = Logger.withTag("Scan")
    }
}
