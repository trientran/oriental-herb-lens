package com.uri.lee.dl.domain.media

/** A photo picked on the device. Created and read by the platform layer only. */
interface LocalImage

/** Shrinks a picked photo to an upload-sized JPEG. */
fun interface ImageCompressor {
    /** JPEG bytes, or null if the image couldn't be read. */
    suspend fun compress(image: LocalImage): ByteArray?
}

/** Stores a photo of a species and returns its public URL. */
fun interface ImageHost {
    suspend fun upload(speciesId: Long, jpeg: ByteArray): String
}
