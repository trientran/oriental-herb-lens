package com.uri.lee.dl.core.training

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** An image's embedding with its class (0 until the number of classes). */
class Example(val embedding: FloatArray, val label: Int)

data class TrainingOptions(
    val learningRate: Float = 0.01f,
    /** L2 regularisation of the weights: keeps a small dataset from being memorised. */
    val l2: Float = 1e-3f,
    val batchSize: Int = 16,
    val maxEpochs: Int = 300,
    /** Stop after this many epochs without a better validation loss; the best weights are kept. */
    val patience: Int = 15,
    /** Share of each class held out to decide when to stop, when no validation set is given. */
    val validationFraction: Float = 0.2f,
    /** Units in a ReLU layer before the softmax layer; 0 trains the softmax layer alone. */
    val hiddenUnits: Int = 0,
    /** Weighs each class's loss by the inverse of its share, so small classes count as much as big ones. */
    val classBalanced: Boolean = false,
    val seed: Int = 42,
)

/** One epoch's progress, for learning curves. */
data class EpochStats(val epoch: Int, val trainLoss: Float, val validationLoss: Float, val validationAccuracy: Float)

class TrainingResult(
    val head: ClassifierHead,
    /** Epochs run, including those after the best one. */
    val epochs: Int,
    val bestEpoch: Int,
    val trainAccuracy: Float,
    /** On the held-out examples; NaN when there were none. */
    val validationAccuracy: Float,
    val validationLoss: Float,
    val history: List<EpochStats>,
)

/**
 * Trains a [ClassifierHead] on embeddings: mini-batch gradient descent with Adam, L2
 * regularisation and early stopping on a validation set. Deterministic for a seed.
 */
object HeadTrainer {

    /** Trains a new head, holding out [TrainingOptions.validationFraction] of each class for validation. */
    fun train(
        examples: List<Example>,
        classes: Int,
        options: TrainingOptions = TrainingOptions(),
        onEpoch: (EpochStats) -> Unit = {},
    ): TrainingResult {
        val (train, validation) = stratifiedSplit(examples, options.validationFraction, Random(options.seed))
        return train(train, validation, classes, options, start = null, onEpoch)
    }

    /**
     * Trains on [train], stopping early on [validation] (on [train] itself when that's empty).
     * Starts from [start] when given (continual learning), expanded to [classes] if needed;
     * otherwise from a fresh head.
     */
    fun train(
        train: List<Example>,
        validation: List<Example>,
        classes: Int,
        options: TrainingOptions,
        start: ClassifierHead?,
        onEpoch: (EpochStats) -> Unit = {},
    ): TrainingResult {
        require(train.isNotEmpty()) { "No examples" }
        val dimensions = train.first().embedding.size
        require((train + validation).all { it.embedding.size == dimensions && it.label in 0 until classes }) { "Inconsistent examples" }
        require(start == null || (start.dimensions == dimensions && start.hidden == options.hiddenUnits)) { "The starting head doesn't fit" }

        val random = Random(options.seed)
        val trainSet = train.map { Example(normalized(it.embedding), it.label) }
        val validationSet = validation.map { Example(normalized(it.embedding), it.label) }
        val monitored = validationSet.ifEmpty { trainSet }
        val weights = classWeights(trainSet, classes, options.classBalanced)

        val model = start?.expanded(classes) ?: ClassifierHead.create(dimensions, options.hiddenUnits, classes, random)
        val adam = Adam(model.params.size, options.learningRate)
        var best = model.copy()
        var bestLoss = loss(model, monitored)
        var bestEpoch = 0
        var epoch = 0
        val order = trainSet.indices.toMutableList()
        val gradient = FloatArray(model.params.size)
        val history = mutableListOf<EpochStats>()

        while (epoch < options.maxEpochs && epoch - bestEpoch < options.patience) {
            epoch++
            order.shuffle(random)
            var trainLoss = 0.0
            for (start in order.indices step options.batchSize) {
                val batch = order.subList(start, minOf(start + options.batchSize, order.size)).map { trainSet[it] }
                gradient.fill(0f)
                trainLoss += accumulateGradient(model, batch, weights, options.l2, gradient) * batch.size
                adam.step(model.params, gradient)
            }
            val validationLoss = loss(model, monitored)
            val stats = EpochStats(epoch, (trainLoss / trainSet.size).toFloat(), validationLoss, accuracy(model, monitored))
            history += stats
            onEpoch(stats)
            if (validationLoss < bestLoss - 1e-6f) {
                bestLoss = validationLoss
                bestEpoch = epoch
                best = model.copy()
            }
        }
        return TrainingResult(
            head = best,
            epochs = epoch,
            bestEpoch = bestEpoch,
            trainAccuracy = accuracy(best, trainSet),
            validationAccuracy = if (validationSet.isEmpty()) Float.NaN else accuracy(best, validationSet),
            validationLoss = bestLoss,
            history = history,
        )
    }

    private fun classWeights(examples: List<Example>, classes: Int, balanced: Boolean): FloatArray {
        if (!balanced) return FloatArray(classes) { 1f }
        val counts = IntArray(classes)
        examples.forEach { counts[it.label]++ }
        val present = counts.count { it > 0 }
        return FloatArray(classes) { if (counts[it] == 0) 0f else examples.size.toFloat() / (present * counts[it]) }
    }

    /**
     * Adds the mean (class-weighted) cross-entropy gradient of [batch], plus the L2 term, to
     * [gradient]. Returns the batch's mean loss.
     */
    private fun accumulateGradient(model: ClassifierHead, batch: List<Example>, weights: FloatArray, l2: Float, gradient: FloatArray): Float {
        val features = model.features
        val scale = 1f / batch.size
        var loss = 0.0
        val outputError = FloatArray(model.classes)
        val hiddenError = FloatArray(model.hidden)
        for (example in batch) {
            val x = example.embedding
            val a = model.featuresOf(x)
            val p = softmax(model.logits(a))
            val w = weights[example.label]
            loss -= w * ln(max(p[example.label], 1e-7f).toDouble())
            for (c in 0 until model.classes) outputError[c] = (p[c] - if (c == example.label) 1f else 0f) * w * scale
            for (c in 0 until model.classes) {
                val error = outputError[c]
                val row = model.outputOffset + c * features
                for (i in 0 until features) gradient[row + i] += error * a[i]
                gradient[model.outputBiasOffset + c] += error
            }
            if (model.hidden > 0) {
                // Back through the ReLU: only active units pass the error on
                for (h in 0 until model.hidden) {
                    if (a[h] <= 0f) { hiddenError[h] = 0f; continue }
                    var sum = 0f
                    for (c in 0 until model.classes) sum += outputError[c] * model.params[model.outputOffset + c * features + h]
                    hiddenError[h] = sum
                }
                for (h in 0 until model.hidden) {
                    val error = hiddenError[h]
                    if (error == 0f) continue
                    val row = h * model.dimensions
                    for (d in 0 until model.dimensions) gradient[row + d] += error * x[d]
                    gradient[model.hiddenBiasOffset + h] += error
                }
            }
        }
        for (i in gradient.indices) if (model.isWeight(i)) gradient[i] += l2 * model.params[i]
        return (loss / batch.size).toFloat()
    }

    /** Mean cross-entropy over already-normalised [examples]. */
    internal fun loss(model: ClassifierHead, examples: List<Example>): Float {
        var total = 0.0
        for (example in examples) {
            val p = softmax(model.logits(model.featuresOf(example.embedding)))[example.label]
            total -= ln(max(p, 1e-7f).toDouble())
        }
        return (total / examples.size).toFloat()
    }

    private fun accuracy(model: ClassifierHead, examples: List<Example>): Float =
        if (examples.isEmpty()) Float.NaN
        else examples.count { model.logits(model.featuresOf(it.embedding)).indexOfMax() == it.label }.toFloat() / examples.size

    /** Holds out [fraction] of each class (at least one when a class has three or more examples). */
    internal fun stratifiedSplit(examples: List<Example>, fraction: Float, random: Random): Pair<List<Example>, List<Example>> {
        val train = mutableListOf<Example>()
        val validation = mutableListOf<Example>()
        examples.groupBy { it.label }.entries.sortedBy { it.key }.forEach { (_, group) ->
            val shuffled = group.shuffled(random)
            val held = if (group.size < 3) 0 else maxOf(1, (group.size * fraction).toInt())
            validation += shuffled.take(held)
            train += shuffled.drop(held)
        }
        return train to validation
    }

    private class Adam(size: Int, private val learningRate: Float) {
        private val m = FloatArray(size)
        private val v = FloatArray(size)
        private var t = 0

        fun step(params: FloatArray, gradient: FloatArray) {
            t++
            val correction1 = 1 - BETA1.pow(t)
            val correction2 = 1 - BETA2.pow(t)
            for (i in gradient.indices) {
                m[i] = BETA1 * m[i] + (1 - BETA1) * gradient[i]
                v[i] = BETA2 * v[i] + (1 - BETA2) * gradient[i] * gradient[i]
                params[i] -= learningRate * (m[i] / correction1) / (sqrt(v[i] / correction2) + EPSILON)
            }
        }

        private companion object {
            const val BETA1 = 0.9f
            const val BETA2 = 0.999f
            const val EPSILON = 1e-7f
        }
    }
}
