package com.uri.lee.dl.core.training

import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/** Anything that turns an image embedding into class probabilities. */
interface EmbeddingClassifier {
    val classes: Int
    val dimensions: Int

    /** The probability of each class for [embedding], summing to 1. */
    fun probabilities(embedding: FloatArray): FloatArray

    /** The most likely class. */
    fun predict(embedding: FloatArray): Int = probabilities(embedding).indexOfMax()
}

/**
 * The trainable part of a classifier, on top of a frozen backbone's embeddings: either a single
 * softmax layer, or (when [hidden] > 0) a ReLU layer of that many units followed by the softmax
 * layer, like Teachable Machine's head. Embeddings are L2-normalised first, so their scale
 * doesn't matter.
 *
 * All weights live in one array, [params]: the hidden layer's weights and biases (when there is
 * one), then the output layer's weights (row-major, one row per class) and biases.
 */
class ClassifierHead(
    override val dimensions: Int,
    val hidden: Int,
    override val classes: Int,
    val params: FloatArray = FloatArray(size(dimensions, hidden, classes)),
) : EmbeddingClassifier {

    init {
        require(classes >= 1) { "Needs at least one class" }
        require(hidden >= 0) { "Hidden units can't be negative" }
        require(params.size == size(dimensions, hidden, classes)) { "Weights don't match the shape" }
    }

    /** Inputs to the output layer: the hidden units, or the embedding itself. */
    val features: Int get() = if (hidden > 0) hidden else dimensions
    internal val hiddenBiasOffset: Int get() = hidden * dimensions
    internal val outputOffset: Int get() = hiddenBiasOffset + hidden
    internal val outputBiasOffset: Int get() = outputOffset + classes * features

    override fun probabilities(embedding: FloatArray): FloatArray = softmax(logits(featuresOf(normalized(embedding))))

    /** The output layer's inputs for a normalised embedding [x]. */
    internal fun featuresOf(x: FloatArray): FloatArray {
        if (hidden == 0) return x
        return FloatArray(hidden) { h ->
            var sum = params[hiddenBiasOffset + h]
            val row = h * dimensions
            for (d in 0 until dimensions) sum += params[row + d] * x[d]
            if (sum > 0f) sum else 0f
        }
    }

    internal fun logits(features: FloatArray): FloatArray = FloatArray(classes) { c ->
        var sum = params[outputBiasOffset + c]
        val row = outputOffset + c * this.features
        for (i in 0 until this.features) sum += params[row + i] * features[i]
        sum
    }

    /** Whether [index] in [params] is a weight (regularised) rather than a bias. */
    internal fun isWeight(index: Int): Boolean =
        index < hiddenBiasOffset || (index in outputOffset until outputBiasOffset)

    fun copy() = ClassifierHead(dimensions, hidden, classes, params.copyOf())

    /**
     * This head with room for [newClasses] classes: the existing classes keep their weights, and
     * new ones start at zero (see [imprint] to give them a head start).
     */
    fun expanded(newClasses: Int): ClassifierHead {
        require(newClasses >= classes) { "Can't drop classes" }
        if (newClasses == classes) return copy()
        val grown = ClassifierHead(dimensions, hidden, newClasses)
        params.copyInto(grown.params, 0, 0, outputOffset)
        params.copyInto(grown.params, grown.outputOffset, outputOffset, outputBiasOffset)
        params.copyInto(grown.params, grown.outputBiasOffset, outputBiasOffset, params.size)
        return grown
    }

    /**
     * Weight imprinting (Qi et al., 2018): sets class [c]'s output weights to the mean direction
     * of its [embeddings]' features, scaled like the other classes' weights, so a new class is
     * recognised before any training.
     */
    fun imprint(c: Int, embeddings: List<FloatArray>, otherClasses: Collection<Int>) {
        if (embeddings.isEmpty()) return
        val mean = FloatArray(features)
        for (e in embeddings) {
            val f = featuresOf(normalized(e))
            for (i in mean.indices) mean[i] += f[i]
        }
        val direction = normalized(mean)
        val others = otherClasses.filter { it != c }
        val scale = if (others.isEmpty()) 1f else others.map { rowNorm(it) }.average().toFloat().takeIf { it > 0f } ?: 1f
        val bias = if (others.isEmpty()) 0f else others.map { params[outputBiasOffset + it] }.average().toFloat()
        val row = outputOffset + c * features
        for (i in 0 until features) params[row + i] = direction[i] * scale
        params[outputBiasOffset + c] = bias
    }

    private fun rowNorm(c: Int): Float {
        val row = outputOffset + c * features
        var sum = 0f
        for (i in 0 until features) sum += params[row + i] * params[row + i]
        return sqrt(sum)
    }

    companion object {
        fun size(dimensions: Int, hidden: Int, classes: Int): Int =
            hidden * dimensions + hidden + classes * (if (hidden > 0) hidden else dimensions) + classes

        /** A fresh head: He-initialised hidden weights (seeded), zero output weights. */
        fun create(dimensions: Int, hidden: Int, classes: Int, random: Random): ClassifierHead {
            val head = ClassifierHead(dimensions, hidden, classes)
            if (hidden > 0) {
                val std = sqrt(2f / dimensions)
                for (i in 0 until head.hiddenBiasOffset) head.params[i] = random.nextGaussian() * std
            }
            return head
        }
    }
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

/** Box–Muller: a standard normal sample, deterministic for the generator's seed. */
internal fun Random.nextGaussian(): Float {
    var u = nextDouble()
    while (u == 0.0) u = nextDouble()
    val v = nextDouble()
    return (sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * kotlin.math.PI * v)).toFloat()
}
