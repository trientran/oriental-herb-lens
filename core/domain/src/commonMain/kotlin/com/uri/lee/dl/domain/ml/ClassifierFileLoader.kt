package com.uri.lee.dl.domain.ml

/**
 * Opens any image-classifier .tflite that carries TFLite metadata (input normalisation and a
 * packed labels file): a model someone trained and exported with this app, for example.
 */
fun interface ClassifierFileLoader {
    /** [model] is a platform location: a file path or URL. */
    suspend fun load(model: String): HerbClassifier
}
