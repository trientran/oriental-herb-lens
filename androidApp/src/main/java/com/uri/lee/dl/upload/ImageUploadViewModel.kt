package com.uri.lee.dl.upload

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.uri.lee.dl.DeviceLocation
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.core.location.AddressLookup
import com.uri.lee.dl.data.platform.UriImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.usecase.SubmitImagesUseCase
import com.uri.lee.dl.domain.usecase.SubmitProgress
import com.uri.lee.dl.ui.common.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface ImageUploadAction {
    data class ImagesPicked(val uris: List<Uri>) : ImageUploadAction
    data object ClearAll : ImageUploadAction

    /** From the device's location or a place search; already has an address. */
    data class LocationFound(val location: DeviceLocation) : ImageUploadAction

    /** A point picked on the map; its address is looked up. */
    data class MapPointPicked(val latitude: Double, val longitude: Double) : ImageUploadAction
    data object Upload : ImageUploadAction
}

class ImageUploadViewModel(
    savedState: SavedStateHandle,
    private val submitImages: SubmitImagesUseCase,
    private val addresses: AddressLookup,
    private val appScope: ApplicationScope,
) : MviViewModel<ImageUploadState, ImageUploadAction>(
    ImageUploadState(herbId = savedState.get<Long>(HERB_ID))
) {

    override fun onAction(action: ImageUploadAction) {
        when (action) {
            is ImageUploadAction.ImagesPicked -> if (action.uris.isNotEmpty()) {
                setState { copy(imageUris = imageUris + action.uris) }
            }
            ImageUploadAction.ClearAll -> setState { copy(imageUris = emptyList(), error = null) }
            is ImageUploadAction.LocationFound -> with(action.location) {
                setState { copy(location = ImageUploadState.HerbLocation(lat, long, addressLine)) }
            }
            is ImageUploadAction.MapPointPicked -> viewModelScope.launch {
                val address = addresses.addressLine(action.latitude, action.longitude)
                setState { copy(location = ImageUploadState.HerbLocation(action.latitude, action.longitude, address)) }
            }
            ImageUploadAction.Upload -> upload()
        }
    }

    /** Runs in the app scope: an upload keeps going if the user leaves the screen. */
    private fun upload() {
        val herbId = currentState.herbId ?: return
        val images = currentState.imageUris.map(::UriImage)
        if (images.isEmpty()) return
        val location = currentState.location?.let { GeoLocation(it.lat, it.long) }
        setState { copy(isUploadComplete = false, error = null) }
        appScope.launch {
            try {
                submitImages(herbId, images, location).collect { progress ->
                    when (progress) {
                        is SubmitProgress.Uploading -> setState { copy(uploadedImagesCount = progress.uploaded) }
                        is SubmitProgress.Finished -> setState {
                            copy(uploadedImagesCount = progress.uploaded, failedImagesCount = progress.failed, isUploadComplete = true)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                setState { copy(error = ImageUploadState.Error(e)) }
            }
        }
    }
}

data class ImageUploadState(
    val herbId: Long? = null,
    val imageUris: List<Uri> = emptyList(),
    val uploadedImagesCount: Int? = null,
    val failedImagesCount: Int = 0,
    val isUploadComplete: Boolean = false,
    val error: Error? = null,
    val location: HerbLocation? = null,
) {
    data class Error(val exception: Exception)

    data class HerbLocation(
        val lat: Double,
        val long: Double,
        val addressLine: String?,
    )
}
