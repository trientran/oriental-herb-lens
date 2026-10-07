package com.uri.lee.dl.domain.model

data class ScanSettings(
    /** Minimum confidence (0..1) for a result to be shown. */
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    /**
     * Identify opens in Pick a plant mode (find each plant, identify one at a time) rather than
     * Whole view. The name and its stored key date from the old single-image screen.
     */
    val detectObjectsInSingleImage: Boolean = false,
) {
    companion object {
        // The lowest the slider allows: the herb model is still weak, so most right answers score low
        const val DEFAULT_MIN_CONFIDENCE = 0.3f
    }
}
