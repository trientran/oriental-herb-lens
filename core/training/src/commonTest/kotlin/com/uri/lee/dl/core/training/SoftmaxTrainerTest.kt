package com.uri.lee.dl.core.training

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoftmaxTrainerTest {

    /** [classes] clusters around random directions in [dimensions]-d space, like embeddings of distinct things. */
    private fun clusters(classes: Int, perClass: Int, dimensions: Int, spread: Float, seed: Int = 1): List<Example> {
        val random = Random(seed)
        val centres = List(classes) { FloatArray(dimensions) { random.nextFloat() * 2 - 1 } }
        return (0 until classes).flatMap { c ->
            List(perClass) { Example(FloatArray(dimensions) { centres[c][it] + (random.nextFloat() * 2 - 1) * spread }, c) }
        }
    }

    @Test
    fun learnsSeparateClusters() {
        val result = SoftmaxTrainer.train(clusters(classes = 5, perClass = 20, dimensions = 64, spread = 0.5f), classes = 5)

        assertTrue(result.validationAccuracy >= 0.95f, "validation accuracy ${result.validationAccuracy}")
        assertTrue(result.trainAccuracy >= 0.95f, "train accuracy ${result.trainAccuracy}")
    }

    @Test
    fun stopsEarlyAndKeepsTheBestEpoch() {
        val options = TrainingOptions(maxEpochs = 1000, patience = 5)
        val result = SoftmaxTrainer.train(clusters(3, 15, 16, 0.3f), classes = 3, options = options)

        assertTrue(result.epochs < 1000, "ran all ${result.epochs} epochs")
        assertEquals(result.bestEpoch + options.patience, result.epochs)
    }

    @Test
    fun sameSeedSameModel() {
        val data = clusters(3, 10, 8, 0.4f)
        val a = SoftmaxTrainer.train(data, 3).classifier
        val b = SoftmaxTrainer.train(data, 3).classifier

        assertTrue(a.weights.contentEquals(b.weights) && a.bias.contentEquals(b.bias))
    }

    @Test
    fun probabilitiesSumToOne() {
        val classifier = SoftmaxTrainer.train(clusters(4, 8, 8, 0.4f), 4).classifier
        val p = classifier.probabilities(FloatArray(8) { it.toFloat() })

        assertTrue(abs(p.sum() - 1f) < 1e-5f)
    }

    @Test
    fun holdsOutPartOfEveryClass() {
        val data = clusters(3, 10, 4, 0.1f) + Example(FloatArray(4) { 1f }, 2).let { listOf(it) }
        val (train, validation) = SoftmaxTrainer.stratifiedSplit(data, 0.2f, Random(0))

        assertEquals(data.size, train.size + validation.size)
        assertEquals(setOf(0, 1, 2), validation.map { it.label }.toSet())
        assertEquals(2, validation.count { it.label == 0 })
    }

    @Test
    fun scaleOfEmbeddingsDoesNotMatter() {
        val data = clusters(3, 12, 8, 0.3f)
        val classifier = SoftmaxTrainer.train(data, 3).classifier
        val x = data.first().embedding

        assertEquals(classifier.predict(x), classifier.predict(FloatArray(x.size) { x[it] * 100 }))
    }
}
