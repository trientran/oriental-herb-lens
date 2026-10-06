package com.uri.lee.dl.core.training

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** A class's precision, recall and F1, and how many test examples it had. */
data class ClassScores(val precision: Float, val recall: Float, val f1: Float, val support: Int)

/**
 * How well a classifier does on a test set. Undefined values (a class nobody predicted, say) are
 * NaN rather than 0, so they can be told apart in the exported CSV.
 */
class Evaluation(
    val classes: Int,
    /** [actual][predicted] counts. */
    val confusion: Array<IntArray>,
    val accuracy: Float,
    val topKAccuracy: Float,
    val topK: Int,
    /** Mean recall over the classes present in the test set. */
    val balancedAccuracy: Float,
    val macroF1: Float,
    val cohensKappa: Float,
    /** Mean cross-entropy of the true class. */
    val logLoss: Float,
    /** Expected calibration error over [CALIBRATION_BINS] equal-width confidence bins. */
    val calibrationError: Float,
    val perClass: List<ClassScores>,
    val examples: Int,
) {
    companion object {
        const val CALIBRATION_BINS = 15

        fun of(classifier: EmbeddingClassifier, test: List<Example>, classes: Int = classifier.classes, topK: Int = 3): Evaluation {
            require(test.isNotEmpty()) { "No test examples" }
            val confusion = Array(classes) { IntArray(classes) }
            var correctTopK = 0
            var logLoss = 0.0
            val binCount = IntArray(CALIBRATION_BINS)
            val binConfidence = DoubleArray(CALIBRATION_BINS)
            val binCorrect = IntArray(CALIBRATION_BINS)
            for (example in test) {
                // A classifier that hasn't met every class yet gives fewer probabilities: the rest are 0
                val p = classifier.probabilities(example.embedding).copyOf(classes)
                val predicted = p.indexOfMax()
                confusion[example.label][predicted]++
                val rankOfTrue = p.count { it > p[example.label] }
                if (rankOfTrue < topK) correctTopK++
                logLoss -= ln(max(p[example.label], 1e-7f).toDouble())
                val confidence = p[predicted]
                val bin = minOf((confidence * CALIBRATION_BINS).toInt(), CALIBRATION_BINS - 1)
                binCount[bin]++
                binConfidence[bin] += confidence.toDouble()
                if (predicted == example.label) binCorrect[bin]++
            }
            val n = test.size
            val correct = (0 until classes).sumOf { confusion[it][it] }
            val actual = IntArray(classes) { confusion[it].sum() }
            val predictedTotals = IntArray(classes) { c -> confusion.sumOf { it[c] } }
            val perClass = (0 until classes).map { c ->
                val tp = confusion[c][c]
                val precision = if (predictedTotals[c] == 0) Float.NaN else tp.toFloat() / predictedTotals[c]
                val recall = if (actual[c] == 0) Float.NaN else tp.toFloat() / actual[c]
                val f1 = when {
                    precision.isNaN() || recall.isNaN() -> if (actual[c] > 0) 0f else Float.NaN
                    precision + recall == 0f -> 0f
                    else -> 2 * precision * recall / (precision + recall)
                }
                ClassScores(precision, recall, f1, actual[c])
            }
            val present = perClass.filter { it.support > 0 }
            val accuracy = correct.toFloat() / n
            val chance = (0 until classes).sumOf { actual[it].toDouble() * predictedTotals[it] } / (n.toDouble() * n)
            var calibration = 0.0
            for (b in 0 until CALIBRATION_BINS) {
                if (binCount[b] == 0) continue
                calibration += binCount[b].toDouble() / n * abs(binCorrect[b].toDouble() / binCount[b] - binConfidence[b] / binCount[b])
            }
            return Evaluation(
                classes = classes,
                confusion = confusion,
                accuracy = accuracy,
                topKAccuracy = correctTopK.toFloat() / n,
                topK = topK,
                balancedAccuracy = present.map { it.recall }.average().toFloat(),
                macroF1 = present.map { it.f1 }.average().toFloat(),
                cohensKappa = if (chance >= 1.0) Float.NaN else ((accuracy - chance) / (1 - chance)).toFloat(),
                logLoss = (logLoss / n).toFloat(),
                calibrationError = calibration.toFloat(),
                perClass = perClass,
                examples = n,
            )
        }
    }
}

/**
 * Continual-learning measures from an accuracy matrix: [accuracy] (step i)(task j) is the
 * accuracy on task j's test examples after training step i, for j ≤ i (Lopez-Paz & Ranzato,
 * 2017; Chaudhry et al., 2018).
 */
class ContinualMetrics(val accuracy: List<List<Float>>) {
    private val steps = accuracy.size

    init {
        require(accuracy.withIndex().all { (i, row) -> row.size == i + 1 }) { "Row i needs i + 1 tasks" }
    }

    /** Mean accuracy over all tasks after the last step. */
    val finalAverageAccuracy: Float get() = accuracy.last().average().toFloat()

    /** Mean over the steps of each step's mean accuracy on the tasks seen so far. */
    val averageIncrementalAccuracy: Float get() = accuracy.map { it.average() }.average().toFloat()

    /** Backward transfer: how much later steps changed earlier tasks' accuracy (negative = forgetting). */
    val backwardTransfer: Float
        get() = if (steps < 2) Float.NaN
        else (0 until steps - 1).map { j -> accuracy.last()[j] - accuracy[j][j] }.average().toFloat()

    /** Mean over earlier tasks of the drop from their best accuracy to their final accuracy. */
    val forgetting: Float
        get() = if (steps < 2) Float.NaN
        else (0 until steps - 1).map { j -> (j until steps - 1).maxOf { accuracy[it][j] } - accuracy.last()[j] }.average().toFloat()
}
