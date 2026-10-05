package com.uri.lee.dl.domain.media

/**
 * Where a photo picker reports back. [onPreparing] comes as soon as the user confirms, with how
 * many photos were chosen, because getting them ready can take a moment (iOS copies each one
 * out of the photo library); [onPicked] follows with the photos, or none if cancelled.
 */
class PhotoPick(
    val onPreparing: (count: Int) -> Unit = {},
    val onPicked: (List<LocalImage>) -> Unit,
)

/** Opens the system photo picker. */
typealias PickPhotos = (PhotoPick) -> Unit
