package com.uri.lee.dl.domain.ml

import com.uri.lee.dl.domain.media.LocalImage

/** A box within an image, as fractions (0..1) of the image's width and height. */
data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
    val area: Float get() = (right - left) * (bottom - top)
}

/** One separate object found in an image, with its own cropped image to classify. */
class FoundObject(
    val region: Region,
    val image: ClassifierImage,
    /** Stable across camera frames while the same object stays in view; null for still photos. */
    val trackingId: Int?,
)

/** Finds the separate objects (plants, leaves, flowers) in an image. */
interface ObjectFinder {
    /** [fromCamera] tracks objects from frame to frame; otherwise the image is treated as a single photo. */
    suspend fun find(image: ClassifierImage, fromCamera: Boolean): List<FoundObject>
}

/** A picked photo, decoded upright for classification. */
class ReadPhoto(val image: ClassifierImage, val width: Int, val height: Int)

/** Decodes photos picked on the device. */
fun interface PhotoReader {
    /** Null when the photo can't be read. */
    suspend fun read(photo: LocalImage): ReadPhoto?
}
