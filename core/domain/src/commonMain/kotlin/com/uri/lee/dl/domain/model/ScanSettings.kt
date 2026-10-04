package com.uri.lee.dl.domain.model

data class ScanSettings(
    /** Minimum confidence (0..1) for a result to be shown. */
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    /** Single-image screen: detect individual objects first (true) or label the whole image. */
    val detectObjectsInSingleImage: Boolean = true,
) {
    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.7f
    }
}
