package com.uri.lee.dl.core.training

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A classifier's final layer: one weight per class and embedding dimension, plus a bias per
 * class. Embeddings are L2-normalised before use, so their scale doesn't matter.
 */
class SoftmaxClassifier(
    val classes: Int,
    val dimensions: Int,
    /** Row-major: class c's weights are [c * dimensions, (c + 1) * dimensions). */
    val weights: FloatArray = FloatArray(classes * dimensions),
    val bias: FloatArray = FloatArray(classes),
) {
    init {
        require(classes >= 2) { "Needs at least two classes" }
        require(weights.size == classes * dimensions && bias.size == classes) { "Weights don't match the shape" }
    }

    /** The probability of each class for [embedding], summing to 1. */
    fun probabilities(embedding: FloatArray): FloatArray = softmax(logits(normalized(embedding)))

    /** The most likely class. */
    fun predict(embedding: FloatArray): Int = probabilities(embedding).indexOfMax()

    internal fun logits(x: FloatArray): FloatArray = FloatArray(classes) { c ->
        var sum = bias[c]
        val row = c * dimensions
        for (d in 0 until dimensions) sum += weights[row + d] * x[d]
        sum
    }

    fun copy() = SoftmaxClassifier(classes, dimensions, weights.copyOf(), bias.copyOf())
}

/** [embedding] scaled to length 1 (unchanged if it's all zeros). */
fun normalized(embedding: FloatArray): FloatArray {
    var sum = 0f
    for (v in embedding) sum += v * v
    val length = sqrt(sum)
    return if (length == 0f) embedding.copyOf() else FloatArray(embedding.size) { embedding[it] / length }
}

internal fun softmax(logits: FloatArray): FloatArray {
    val max = logits.max()
    val exps = FloatArray(logits.size) { exp(logits[it] - max) }
    val total = exps.sum()
    return FloatArray(exps.size) { exps[it] / total }
}

internal fun FloatArray.indexOfMax(): Int {
    var best = 0
    for (i in 1 until size) if (this[i] > this[best]) best = i
    return best
}
