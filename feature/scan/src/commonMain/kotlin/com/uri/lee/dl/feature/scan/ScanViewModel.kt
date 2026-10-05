package com.uri.lee.dl.feature.scan

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.ml.ReadPhoto
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.RecognizedHerb
import com.uri.lee.dl.domain.model.ScanSettings
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.usecase.IdentifyPlantsUseCase
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
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

/** A plant shown on the camera view or the photo as a dot, which the user can pick. */
data class ShownObject(val id: Int, val region: Region)

/** The plant the user picked (or held the camera on), cut out as [image], and what it may be. */
class PickedPlant(val id: Int, val image: ClassifierImage, val herbs: List<RecognizedHerb>)

sealed interface ScanAction {
    data class SetMode(val mode: ScanMode) : ScanAction

    /** A tap on a plant's dot. */
    data class SelectObject(val id: Int) : ScanAction

    /** A result was opened (for usage statistics). */
    data class ResultOpened(val speciesId: Long) : ScanAction

    /** Closes the picked plant; on the camera, looking for a plant starts again. */
    data object ClosePicked : ScanAction

    /** The camera view's width / height, so Whole view identifies what the user can see. */
    data class ViewAspect(val aspect: Float) : ScanAction

    /** The user confirmed [count] photos in the picker; they arrive shortly as [PhotosPicked]. */
    data class PhotosPreparing(val count: Int) : ScanAction
    data class PhotosPicked(val photos: List<LocalImage>) : ScanAction
    data object BackToCamera : ScanAction
}

data class ScanState(
    val mode: ScanMode = ScanMode.WHOLE_VIEW,
    val source: ScanSource = ScanSource.Camera,
    /** Pick a plant: a dot on each plant found. */
    val objects: List<ShownObject> = emptyList(),
    /** Pick a plant on the camera: the plant being held steady on, until it's picked. */
    val steadyId: Int? = null,
    /** Pick a plant: the plant shown with what it may be. The camera pauses while there is one. */
    val picked: PickedPlant? = null,
    /** Whole view: what the camera or photo may show; never below [minConfidence]. */
    val results: List<RecognizedHerb> = emptyList(),
    /** Width / height of the latest camera frame, to place [objects] over the preview. */
    val frameAspect: Float? = null,
    /** A photo is being read and identified. */
    val isWorking: Boolean = false,
    /** How many picked photos are still being handed over by the picker; 0 when none. */
    val preparingPhotos: Int = 0,
    val hasError: Boolean = false,
    val minConfidence: Float = ScanSettings.DEFAULT_MIN_CONFIDENCE,
)

/**
 * The Identify screen. Camera frames arrive through [analyzeFrame], one at a time; picked photos
 * through [ScanAction.PhotosPicked]. Everything is classified on the device.
 *
 * Pick a plant on the camera only looks for plants in each frame, which is quick. Once the camera
 * has stayed on one plant for [STEADY] (or the user taps its dot), that plant is cut out and
 * identified, and the camera pauses on it until the user closes it.
 */
class ScanViewModel(
    private val recognizeHerbs: RecognizeHerbsUseCase,
    private val identifyPlants: IdentifyPlantsUseCase,
    private val objectFinder: ObjectFinder,
    private val cropper: ImageCropper,
    private val photoReader: PhotoReader,
    private val settings: SettingsRepository,
    private val analytics: Analytics = NoAnalytics,
    private val time: TimeSource = TimeSource.Monotonic,
) : MviViewModel<ScanState, ScanAction>(ScanState()) {

    private var photo: ReadPhoto? = null
    private var photoPlants: Map<Int, PickedPlant> = emptyMap()
    private var photoJob: Job? = null
    private var viewAspect: Float? = null

    /** The plant the camera is on, and since when it hasn't moved. */
    private var steady: Steady? = null
    private var tappedId: Int? = null
    private var lastFrame: List<Pair<ShownObject, FoundObject>> = emptyList()
    private var lastFrameLog: TimeMark? = null
    /** Whole view on the camera reports a new identification only when the top species changes. */
    private var lastWholeViewTop: Long? = null

    private class Steady(val id: Int, val region: Region, val since: TimeMark)

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
                resetCamera()
                setState { copy(mode = action.mode, objects = emptyList(), steadyId = null, picked = null, results = emptyList()) }
                viewModelScope.launch { runCatching { settings.setDetectObjectsInSingleImage(action.mode == ScanMode.PICK_PLANT) } }
                if (currentState.source is ScanSource.Photo) identifyPhoto()
            }
            is ScanAction.SelectObject -> selectObject(action.id)
            is ScanAction.ResultOpened -> resultOpened(action.speciesId)
            ScanAction.ClosePicked -> {
                resetCamera()
                setState { copy(picked = null, steadyId = null) }
            }
            is ScanAction.ViewAspect -> viewAspect = action.aspect
            is ScanAction.PhotosPreparing -> setState { copy(preparingPhotos = action.count) }
            is ScanAction.PhotosPicked -> photosPicked(action.photos)
            ScanAction.BackToCamera -> {
                photoJob?.cancel()
                photo = null
                resetCamera()
                setState {
                    copy(
                        source = ScanSource.Camera, objects = emptyList(), steadyId = null, picked = null,
                        results = emptyList(), isWorking = false, hasError = false,
                    )
                }
            }
        }
    }

    private fun selectObject(id: Int) {
        val state = currentState
        when (state.source) {
            is ScanSource.Photo -> setState { copy(picked = photoPlants[id] ?: picked) }
            ScanSource.Camera -> {
                val paused = lastFrame.firstOrNull { it.first.id == id }
                if (state.picked != null && paused != null) {
                    // The camera is paused on a plant: identify another one from the same frame
                    viewModelScope.launch { pick(paused.first.id, paused.second.image) }
                } else {
                    tappedId = id // picked from the next frame
                }
            }
            is ScanSource.Photos -> Unit
        }
    }

    /**
     * Identifies one camera frame. The camera waits for this to return before sending the next,
     * so frames never pile up.
     */
    suspend fun analyzeFrame(frame: ClassifierImage, aspect: Float) {
        val state = currentState
        if (state.source != ScanSource.Camera) return
        val started = time.markNow()
        try {
            when (state.mode) {
                ScanMode.WHOLE_VIEW -> {
                    val visible = viewAspect?.let { cropper.crop(frame, visibleSquare(aspect, it)) } ?: frame
                    val results = recognizeHerbs(visible, state.minConfidence, MAX_RESULTS)
                    logFrame { "whole view in ${started.elapsedNow().inWholeMilliseconds} ms: ${results.describe()}" }
                    val top = results.firstOrNull()?.species?.id
                    if (top != null && top != lastWholeViewTop) analytics.log(results.identified(WHOLE_VIEW, CAMERA))
                    lastWholeViewTop = top ?: lastWholeViewTop
                    setState { if (mode == ScanMode.WHOLE_VIEW && source == ScanSource.Camera) copy(results = results, frameAspect = aspect) else this }
                }
                ScanMode.PICK_PLANT -> if (state.picked == null) findPlants(frame, aspect, started)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Frame not identified" } // the next frame tries again
        }
    }

    private suspend fun findPlants(frame: ClassifierImage, aspect: Float, started: TimeMark) {
        val found = objectFinder.find(frame, fromCamera = true)
        val shown = found.mapIndexed { i, it -> ShownObject(it.trackingId ?: -(i + 1), it.region) }
        lastFrame = shown.zip(found)
        val tapped = tappedId?.let { id -> lastFrame.firstOrNull { it.first.id == id } }
        // Stay with the plant already being held on while it's in view; otherwise the biggest
        val target = tapped
            ?: lastFrame.firstOrNull { it.first.id == steady?.id }
            ?: lastFrame.maxByOrNull { it.first.region.area }
        val current = steady
        val now = when {
            target == null -> null
            current == null || current.id != target.first.id || current.region.movedFrom(target.first.region) ->
                Steady(target.first.id, target.first.region, time.markNow())
            else -> current
        }
        steady = now
        val ready = target != null && now != null && (tapped != null || now.since.elapsedNow() >= STEADY)
        logFrame { "pick in ${started.elapsedNow().inWholeMilliseconds} ms: ${shown.size} plants, on ${target?.first?.id}" }
        setState {
            if (mode == ScanMode.PICK_PLANT && source == ScanSource.Camera) {
                copy(objects = shown, steadyId = if (ready) null else target?.first?.id, frameAspect = aspect)
            } else {
                this
            }
        }
        if (ready && target != null) pick(target.first.id, target.second.image)
    }

    /** Identifies one plant from the camera and pauses on it. */
    private suspend fun pick(id: Int, image: ClassifierImage) {
        tappedId = null
        steady = null
        val started = time.markNow()
        val herbs = recognizeHerbs(image, currentState.minConfidence, MAX_RESULTS)
        log.d { "picked $id in ${started.elapsedNow().inWholeMilliseconds} ms: ${herbs.describe()}" }
        analytics.log(herbs.identified(PICK_PLANT, CAMERA))
        setState { if (mode == ScanMode.PICK_PLANT && source == ScanSource.Camera) copy(picked = PickedPlant(id, image, herbs), steadyId = null) else this }
    }

    /** Which result was opened, and how sure the model was, for usage statistics. */
    private fun resultOpened(speciesId: Long) {
        val state = currentState
        val (herbs, source) = when (val source = state.source) {
            is ScanSource.Photos -> source.items.firstNotNullOfOrNull { item -> item.herbs?.takeIf { herbs -> herbs.any { it.species?.id == speciesId } } }.orEmpty() to PHOTOS
            else -> (if (state.mode == ScanMode.PICK_PLANT) state.picked?.herbs.orEmpty() else state.results) to (if (source == ScanSource.Camera) CAMERA else PHOTO)
        }
        val index = herbs.indexOfFirst { it.species?.id == speciesId }
        if (index < 0) return
        val mode = if (source == PHOTOS || state.mode == ScanMode.WHOLE_VIEW) WHOLE_VIEW else PICK_PLANT
        analytics.log(AnalyticsEvent.ResultOpened(speciesId, herbs[index].confidence, rank = index + 1, mode = mode, source = source))
    }

    private fun List<RecognizedHerb>.identified(mode: String, source: String) =
        AnalyticsEvent.Identified(mode, source, firstOrNull()?.species?.id, firstOrNull()?.confidence, size)

    private fun resetCamera() {
        steady = null
        tappedId = null
        lastFrame = emptyList()
    }

    /** At most once a second, so the log shows what the camera sees without a line per frame. */
    private fun logFrame(message: () -> String) {
        if (lastFrameLog?.let { it.elapsedNow() < 1.seconds } == true) return
        lastFrameLog = time.markNow()
        log.d(message = message)
    }

    private fun List<RecognizedHerb>.describe() =
        if (isEmpty()) "nothing above ${currentState.minConfidence}" else joinToString { "${it.label} ${it.confidence}" }

    private fun photosPicked(photos: List<LocalImage>) {
        setState { copy(preparingPhotos = 0) }
        if (photos.isEmpty()) return
        photoJob?.cancel()
        photo = null
        if (photos.size == 1) {
            setState { copy(source = ScanSource.Photo(photos.single().uri), objects = emptyList(), picked = null, results = emptyList()) }
            photoJob = viewModelScope.launch {
                photo = photoReader.read(photos.single())
                if (photo == null) setState { copy(hasError = true) } else identifyPhoto()
            }
        } else {
            setState { copy(source = ScanSource.Photos(photos.map { BatchItem(it.uri) }), results = emptyList(), objects = emptyList(), picked = null) }
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
                    isWorking = true, hasError = false, objects = emptyList(), picked = null, results = emptyList(),
                )
            }
            try {
                when (currentState.mode) {
                    ScanMode.WHOLE_VIEW -> {
                        val results = recognizeHerbs(read.image, minConfidence, MAX_RESULTS)
                        analytics.log(results.identified(WHOLE_VIEW, PHOTO))
                        setState { copy(results = results, isWorking = false) }
                    }
                    ScanMode.PICK_PLANT -> {
                        val plants = identifyPlants(read.image, minConfidence, MAX_RESULTS)
                        photoPlants = plants.withIndex().associate { (i, plant) -> i to PickedPlant(i, plant.image, plant.herbs) }
                        analytics.log(plants.firstOrNull()?.herbs.orEmpty().identified(PICK_PLANT, PHOTO))
                        setState {
                            copy(
                                objects = plants.mapIndexed { i, plant -> ShownObject(i, plant.region) },
                                // The most likely herb is shown straight away; the other dots can be tapped
                                picked = photoPlants[0]?.takeIf { it.herbs.isNotEmpty() },
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
                else BatchItem(picked.uri, herbs = recognizeHerbs(read.image, minConfidence, MAX_RESULTS).also { analytics.log(it.identified(WHOLE_VIEW, PHOTOS)) })
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

        // Usage statistics values
        private const val WHOLE_VIEW = "whole_view"
        private const val PICK_PLANT = "pick_plant"
        private const val CAMERA = "camera"
        private const val PHOTO = "photo"
        private const val PHOTOS = "photos"

        /** How long the camera stays on a plant before it's identified. */
        val STEADY: Duration = 2.seconds
        private val log = Logger.withTag("Scan")
    }
}

/**
 * Whether the camera moved off this plant: its centre shifted by more than 8 % of the frame, or
 * its size changed by more than a third (moving closer or away). Small jitter is ignored.
 */
internal fun Region.movedFrom(now: Region): Boolean {
    val shift = hypot((centerX - now.centerX).toDouble(), (centerY - now.centerY).toDouble())
    val resized = if (area > 0f) abs(now.area - area) / area else 1f
    return shift > 0.08 || resized > 0.33f
}

/**
 * The largest centred square of a camera frame ([frameAspect] = width / height) that is visible
 * in a view of [viewAspect] filled by the frame. The herb model takes a square image, so this is
 * what the user sees, without stretching it.
 */
internal fun visibleSquare(frameAspect: Float, viewAspect: Float): Region {
    // Visible part of the frame, as fractions of its width and height
    val visibleWidth = if (frameAspect > viewAspect) viewAspect / frameAspect else 1f
    val visibleHeight = if (frameAspect > viewAspect) 1f else frameAspect / viewAspect
    // The square's side in frame-height units, then as fractions of width and height
    val side = minOf(visibleWidth * frameAspect, visibleHeight)
    val width = side / frameAspect
    return Region((1 - width) / 2, (1 - side) / 2, (1 + width) / 2, (1 + side) / 2)
}
