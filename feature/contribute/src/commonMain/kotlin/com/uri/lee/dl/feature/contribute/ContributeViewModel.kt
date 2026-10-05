package com.uri.lee.dl.feature.contribute

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.core.ui.MviViewModel
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.analytics.NoAnalytics
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.moderation.PlantCheck
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.usecase.SubmitImagesUseCase
import com.uri.lee.dl.domain.usecase.SubmitProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface ContributeAction {
    data class PhotosPicked(val photos: List<LocalImage>) : ContributeAction
    data class RemovePhoto(val photo: LocalImage) : ContributeAction

    /** From the device's position or a tap on the map; the address is looked up. */
    data class LocationPicked(val location: GeoLocation) : ContributeAction
    data object ClearLocation : ContributeAction
    data object Upload : ContributeAction
}

sealed interface UploadPhase {
    data object Editing : UploadPhase
    data class Uploading(val uploaded: Int, val total: Int) : UploadPhase
    data class Finished(val uploaded: Int, val failed: Int) : UploadPhase
    data object Failed : UploadPhase
}

data class PickedLocation(val location: GeoLocation, val address: String?)

/** What the on-device check found in a picked photo; only [PLANT] photos are uploaded. */
enum class PhotoCheck { CHECKING, PLANT, NOT_PLANT }

data class ContributeState(
    val herbId: Long,
    val speciesName: String? = null,
    val photos: List<LocalImage> = emptyList(),
    /** By photo URI; a photo without an entry hasn't been checked yet. */
    val checks: Map<String, PhotoCheck> = emptyMap(),
    val location: PickedLocation? = null,
    val isSignedIn: Boolean = true,
    val phase: UploadPhase = UploadPhase.Editing,
) {
    /** Every upload is geotagged: the photos are research records, so a place is required. */
    val canUpload: Boolean
        get() = isSignedIn && plantPhotos.isNotEmpty() && photos.none { checks[it.uri] == PhotoCheck.CHECKING } &&
            location != null && (phase == UploadPhase.Editing || phase == UploadPhase.Failed)

    /** The photos that will be uploaded: those the check found a plant in. */
    val plantPhotos: List<LocalImage> get() = photos.filter { checks[it.uri] == PhotoCheck.PLANT }
}

/**
 * Photos of a species, with the place they were taken, uploaded to R2 and listed on the species. The
 * upload runs in the app scope, so it finishes even if the user leaves the screen.
 */
class ContributeViewModel(
    herbId: Long,
    catalog: SpeciesRepository,
    auth: AuthRepository,
    private val submitImages: SubmitImagesUseCase,
    private val addresses: AddressLine,
    private val appScope: ApplicationScope,
    private val notifier: UploadNotifier,
    private val plantCheck: PlantCheck,
    private val analytics: Analytics = NoAnalytics,
) : MviViewModel<ContributeState, ContributeAction>(ContributeState(herbId)) {

    init {
        auth.observeUserId().onEach { setState { copy(isSignedIn = it != null) } }.launchIn(viewModelScope)
        viewModelScope.launch {
            val species = runCatching { catalog.get(herbId) }.getOrNull()
            setState { copy(speciesName = species?.let { it.preferredVietnameseName ?: it.scientificName }) }
        }
    }

    override fun onAction(action: ContributeAction) {
        when (action) {
            is ContributeAction.PhotosPicked -> {
                val added = action.photos.filter { picked -> currentState.photos.none { it.uri == picked.uri } }
                setState {
                    copy(
                        photos = (photos + added).take(MAX_PHOTOS),
                        checks = checks + added.associate { it.uri to PhotoCheck.CHECKING },
                    )
                }
                check(added)
            }
            is ContributeAction.RemovePhoto -> setState {
                copy(photos = photos.filterNot { it.uri == action.photo.uri }, checks = checks - action.photo.uri)
            }
            is ContributeAction.LocationPicked -> {
                setState { copy(location = PickedLocation(action.location, address = null)) }
                viewModelScope.launch {
                    val address = runCatching { addresses(action.location) }.getOrNull()
                    setState { if (location?.location == action.location) copy(location = location.copy(address = address)) else this }
                }
            }
            ContributeAction.ClearLocation -> setState { copy(location = null) }
            ContributeAction.Upload -> upload()
        }
    }

    /**
     * Looks for a plant in each new photo, on the device, so photos of anything else (people,
     * explicit images) are never uploaded. Unreadable photos count as having no plant.
     */
    private fun check(photos: List<LocalImage>) = viewModelScope.launch {
        for (photo in photos) {
            val result = try {
                if (plantCheck.showsPlant(photo) == true) PhotoCheck.PLANT else PhotoCheck.NOT_PLANT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Plant check failed" }
                PhotoCheck.NOT_PLANT
            }
            setState { if (photo.uri in checks) copy(checks = checks + (photo.uri to result)) else this }
        }
    }

    private fun upload() {
        if (!currentState.canUpload) return
        val state = currentState
        val location = state.location?.location ?: return
        setState { copy(phase = UploadPhase.Uploading(0, plantPhotos.size)) }
        notifier.uploadStarted()
        appScope.launch {
            try {
                submitImages(state.herbId, state.plantPhotos, location).collect { progress ->
                    if (progress is SubmitProgress.Finished) {
                        if (progress.uploaded > 0) analytics.log(AnalyticsEvent.PhotosShared(state.herbId, progress.uploaded))
                        notifier.uploadFinished(state.herbId, state.speciesName, progress.uploaded, progress.failed)
                    }
                    setState {
                        copy(
                            phase = when (progress) {
                                is SubmitProgress.Uploading -> UploadPhase.Uploading(progress.uploaded, progress.total)
                                is SubmitProgress.Finished -> UploadPhase.Finished(progress.uploaded, progress.failed)
                            },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Upload failed" }
                notifier.uploadFinished(state.herbId, state.speciesName, uploaded = 0, failed = state.plantPhotos.size)
                setState { copy(phase = UploadPhase.Failed) }
            }
        }
    }

    companion object {
        /** Per upload; more can be added afterwards. */
        const val MAX_PHOTOS = 20
        private val log = Logger.withTag("Contribute")
    }
}

/** Reverse geocoding, injected so the feature doesn't depend on core:location. */
fun interface AddressLine {
    suspend operator fun invoke(location: GeoLocation): String?
}
