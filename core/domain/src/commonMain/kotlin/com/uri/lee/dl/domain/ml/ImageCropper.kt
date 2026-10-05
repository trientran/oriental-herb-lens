package com.uri.lee.dl.domain.ml

/** Cuts part of an image out, e.g. the middle of a camera frame that the user is looking at. */
fun interface ImageCropper {
    /** The part of [image] inside [region]; null if the image can't be cropped. */
    suspend fun crop(image: ClassifierImage, region: Region): ClassifierImage?
}
