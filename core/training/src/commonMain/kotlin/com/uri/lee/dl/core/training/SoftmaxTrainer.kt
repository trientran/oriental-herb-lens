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
    /** Share of each class held out to decide when to stop (and to report accuracy). */
    val validationFraction: Float = 0.2f,
    val seed: Int = 42,
)

class TrainingResult(
    val classifier: SoftmaxClassifier,
    /** Epochs run, including those after the best one. */
    val epochs: Int,
    val bestEpoch: Int,
    val trainAccuracy: Float,
    /** On the held-out examples; NaN when there were too few to hold any out. */
    val validationAccuracy: Float,
    val validationLoss: Float,
)

/**
 * Trains a [SoftmaxClassifier] on embeddings: mini-batch gradient descent with Adam, L2
 * regularisation and early stopping on a stratified validation split. Deterministic for a seed.
 */
object SoftmaxTrainer {

    fun train(
        examples: List<Example>,
        classes: Int,
        options: TrainingOptions = TrainingOptions(),
        onEpoch: (epoch: Int, validationLoss: Float) -> Unit = { _, _ -> },
    ): TrainingResult {
        require(examples.isNotEmpty()) { "No examples" }
        val dimensions = examples.first().embedding.size
        require(examples.all { it.embedding.size == dimensions && it.label in 0 until classes }) { "Inconsistent examples" }

        val random = Random(options.seed)
        val inputs = examples.map { Example(normalized(it.embedding), it.label) }
        val (train, validation) = stratifiedSplit(inputs, options.validationFraction, random)
        val monitored = validation.ifEmpty { train }

        val model = SoftmaxClassifier(classes, dimensions)
        val adam = Adam(model.weights.size + model.bias.size, options.learningRate)
        var best = model.copy()
        var bestLoss = Float.MAX_VALUE
        var bestEpoch = 0
        var epoch = 0
        val order = train.indices.toMutableList()
        val gradient = FloatArray(model.weights.size + model.bias.size)

        while (epoch < options.maxEpochs && epoch - bestEpoch < options.patience) {
            epoch++
            order.shuffle(random)
            for (start in order.indices step options.batchSize) {
                val batch = order.subList(start, minOf(start + options.batchSize, order.size)).map { train[it] }
                gradient.fill(0f)
                accumulateGradient(model, batch, options.l2, gradient)
                adam.step(model, gradient)
            }
            val loss = loss(model, monitored)
            onEpoch(epoch, loss)
            if (loss < bestLoss - 1e-6f) {
                bestLoss = loss
                bestEpoch = epoch
                best = model.copy()
            }
        }
        return TrainingResult(
            classifier = best,
            epochs = epoch,
            bestEpoch = bestEpoch,
            trainAccuracy = accuracy(best, train),
            validationAccuracy = if (validation.isEmpty()) Float.NaN else accuracy(best, validation),
            validationLoss = bestLoss,
        )
    }

    /** Mean cross-entropy gradient of [batch] plus the L2 term, into [gradient] (weights, then biases). */
    private fun accumulateGradient(model: SoftmaxClassifier, batch: List<Example>, l2: Float, gradient: FloatArray) {
        val d = model.dimensions
        val biasOffset = model.weights.size
        val scale = 1f / batch.size
        for (example in batch) {
            val p = softmax(model.logits(example.embedding))
            for (c in 0 until model.classes) {
                val error = (p[c] - if (c == example.label) 1f else 0f) * scale
                val row = c * d
                for (i in 0 until d) gradient[row + i] += error * example.embedding[i]
                gradient[biasOffset + c] += error
            }
        }
        for (i in 0 until biasOffset) gradient[i] += l2 * model.weights[i]
    }

    private fun loss(model: SoftmaxClassifier, examples: List<Example>): Float {
        var total = 0.0
        for (example in examples) {
            val p = softmax(model.logits(example.embedding))[example.label]
            total -= ln(max(p, 1e-7f).toDouble())
        }
        return (total / examples.size).toFloat()
    }

    private fun accuracy(model: SoftmaxClassifier, examples: List<Example>): Float =
        if (examples.isEmpty()) Float.NaN
        else examples.count { softmax(model.logits(it.embedding)).indexOfMax() == it.label }.toFloat() / examples.size

    /** Holds out [fraction] of each class (at least one when a class has three or more examples). */
    internal fun stratifiedSplit(examples: List<Example>, fraction: Float, random: Random): Pair<List<Example>, List<Example>> {
        val train = mutableListOf<Example>()
        val validation = mutableListOf<Example>()
        examples.groupBy { it.label }.values.forEach { group ->
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

        fun step(model: SoftmaxClassifier, gradient: FloatArray) {
            t++
            val correction1 = 1 - BETA1.pow(t)
            val correction2 = 1 - BETA2.pow(t)
            val biasOffset = model.weights.size
            for (i in gradient.indices) {
                m[i] = BETA1 * m[i] + (1 - BETA1) * gradient[i]
                v[i] = BETA2 * v[i] + (1 - BETA2) * gradient[i] * gradient[i]
                val update = learningRate * (m[i] / correction1) / (sqrt(v[i] / correction2) + EPSILON)
                if (i < biasOffset) model.weights[i] -= update else model.bias[i - biasOffset] -= update
            }
        }

        private companion object {
            const val BETA1 = 0.9f
            const val BETA2 = 0.999f
            const val EPSILON = 1e-7f
        }
    }
}
