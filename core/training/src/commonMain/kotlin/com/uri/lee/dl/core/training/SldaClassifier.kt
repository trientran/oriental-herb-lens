package com.uri.lee.dl.core.training

import kotlin.math.sqrt

/**
 * Streaming linear discriminant analysis (Hayes & Kanan, 2020): each class is the mean of its
 * normalised embeddings, and all classes share one covariance matrix of how the embedding's
 * dimensions vary within a class. Learning only adds to running sums, so new classes and new
 * photos never disturb old ones and the order they arrive in doesn't matter: no gradient
 * training, no forgetting. The classifier itself is computed from the sums when needed, as a
 * single softmax layer ([head]) like the trained heads.
 *
 * Memory: one sum per class plus the [dimensions] × [dimensions] scatter matrix.
 */
class SldaClassifier(
    override val dimensions: Int,
    /** Pulls the shared covariance towards the identity, so it can be inverted with few photos. */
    val shrinkage: Double = 1e-4,
) : EmbeddingClassifier {

    private val sums = mutableListOf<DoubleArray>()
    private val counts = mutableListOf<Int>()

    /** Sum of x xᵀ over every normalised embedding learnt; only the upper triangle is kept up to date. */
    private val scatter = DoubleArray(dimensions * dimensions)
    private var total = 0
    private var fitted: ClassifierHead? = null

    override val classes: Int get() = sums.size

    /** Adds [examples] to the running sums, making room for any new class. */
    fun learn(examples: List<Example>) {
        if (examples.isEmpty()) return
        val d = dimensions
        for (example in examples) {
            require(example.embedding.size == d) { "Wrong embedding size" }
            while (sums.size <= example.label) {
                sums += DoubleArray(d)
                counts += 0
            }
            val x = normalized(example.embedding)
            val sum = sums[example.label]
            for (i in 0 until d) {
                val xi = x[i].toDouble()
                sum[i] += xi
                val row = i * d
                for (j in i until d) scatter[row + j] += xi * x[j]
            }
            counts[example.label]++
            total++
        }
        fitted = null
    }

    /**
     * The classifier as a softmax layer on the normalised embedding: for class c with mean μ_c and
     * precision Λ = ((1 − shrinkage)·Σ + shrinkage·I)⁻¹, weights Λμ_c and bias −½ μ_cᵀΛμ_c.
     * A class with no examples yet gets a bias that keeps it from ever being chosen.
     */
    fun head(): ClassifierHead {
        fitted?.let { return it }
        val d = dimensions
        val c = classes
        require(c >= 1 && total > 0) { "Nothing learnt yet" }
        // Shared within-class covariance: (Σ x xᵀ − Σ_c sum_c sum_cᵀ / n_c) / n
        val a = DoubleArray(d * d)
        for (i in 0 until d) for (j in i until d) {
            var between = 0.0
            for (k in 0 until c) if (counts[k] > 0) between += sums[k][i] * sums[k][j] / counts[k]
            val v = (1 - shrinkage) * (scatter[i * d + j] - between) / total + if (i == j) shrinkage else 0.0
            a[i * d + j] = v
            a[j * d + i] = v
        }
        choleskyInPlace(a, d)
        val head = ClassifierHead(d, 0, c)
        for (k in 0 until c) {
            if (counts[k] == 0) {
                head.params[head.outputBiasOffset + k] = -1e9f
                continue
            }
            val mean = DoubleArray(d) { sums[k][it] / counts[k] }
            val w = choleskySolve(a, d, mean)
            var quadratic = 0.0
            for (i in 0 until d) {
                head.params[head.outputOffset + k * d + i] = w[i].toFloat()
                quadratic += mean[i] * w[i]
            }
            head.params[head.outputBiasOffset + k] = (-0.5 * quadratic).toFloat()
        }
        fitted = head
        return head
    }

    override fun probabilities(embedding: FloatArray): FloatArray = head().probabilities(embedding)

    private companion object {
        /** Replaces the symmetric positive definite [a] (n × n) with its lower Cholesky factor L, where a = L Lᵀ. */
        fun choleskyInPlace(a: DoubleArray, n: Int) {
            for (j in 0 until n) {
                val rowJ = j * n
                var diagonal = a[rowJ + j]
                for (k in 0 until j) diagonal -= a[rowJ + k] * a[rowJ + k]
                require(diagonal > 0) { "Covariance isn't positive definite" }
                val l = sqrt(diagonal)
                a[rowJ + j] = l
                for (i in j + 1 until n) {
                    val rowI = i * n
                    var s = a[rowI + j]
                    for (k in 0 until j) s -= a[rowI + k] * a[rowJ + k]
                    a[rowI + j] = s / l
                }
            }
        }

        /** Solves L Lᵀ x = [b] with the factor from [choleskyInPlace]. */
        fun choleskySolve(l: DoubleArray, n: Int, b: DoubleArray): DoubleArray {
            val y = DoubleArray(n)
            for (i in 0 until n) {
                var s = b[i]
                val row = i * n
                for (k in 0 until i) s -= l[row + k] * y[k]
                y[i] = s / l[row + i]
            }
            val x = DoubleArray(n)
            for (i in n - 1 downTo 0) {
                var s = y[i]
                for (k in i + 1 until n) s -= l[k * n + i] * x[k]
                x[i] = s / l[i * n + i]
            }
            return x
        }
    }
}
