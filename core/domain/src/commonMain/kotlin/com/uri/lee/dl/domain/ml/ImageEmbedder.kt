package com.uri.lee.dl.domain.ml

/**
 * Turns an image into an embedding: a general-purpose "backbone" model's summary of it, the
 * input that user-trained models learn from (plan Phase 7). Not tied to herbs.
 */
interface ImageEmbedder {
    suspend fun embed(image: ClassifierImage): FloatArray

    /** Frees the model. */
    fun close() {}
}

/** Loads a backbone (a .tflite with an embedding output) from a platform location: a file path or URL. */
fun interface ImageEmbedderLoader {
    suspend fun load(model: String): ImageEmbedder
}
