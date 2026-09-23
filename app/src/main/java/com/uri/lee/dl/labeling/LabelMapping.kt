package com.uri.lee.dl.labeling

import com.google.mlkit.vision.label.ImageLabel

/** One classifier output: the model label, which is the herb id, and its confidence. */
data class RawLabel(val id: String, val confidence: Float)

fun ImageLabel.toRawLabel() = RawLabel(id = text, confidence = confidence)

/**
 * Turns classifier output into [Herb]s, keeping the model's order (highest confidence first).
 * Names are looked up by herb id; an id missing from a lookup yields a null name.
 */
fun List<RawLabel>.toHerbs(
    latinNameOf: (id: String) -> String?,
    viNameOf: (id: String) -> String?,
): List<Herb> = map { label ->
    Herb(
        id = label.id,
        latinName = latinNameOf(label.id),
        viName = viNameOf(label.id),
        confidence = label.confidence,
    )
}
