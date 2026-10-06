package com.uri.lee.dl.core.training

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadTrainerTest {

    @Test
    fun learnsSeparateClusters() {
        val result = HeadTrainer.train(clusters(classes = 5, perClass = 20, dimensions = 64, spread = 0.5f), classes = 5)

        assertTrue(result.validationAccuracy >= 0.95f, "validation accuracy ${result.validationAccuracy}")
        assertTrue(result.trainAccuracy >= 0.95f, "train accuracy ${result.trainAccuracy}")
    }

    @Test
    fun stopsEarlyAndKeepsTheBestEpoch() {
        val options = TrainingOptions(maxEpochs = 1000, patience = 5)
        val result = HeadTrainer.train(clusters(3, 15, 16, 0.3f), classes = 3, options = options)

        assertTrue(result.epochs < 1000, "ran all ${result.epochs} epochs")
        assertEquals(result.bestEpoch + options.patience, result.epochs)
    }

    @Test
    fun sameSeedSameModel() {
        val data = clusters(3, 10, 8, 0.4f)
        val a = HeadTrainer.train(data, 3).head
        val b = HeadTrainer.train(data, 3).head

        assertTrue(a.params.contentEquals(b.params))
    }

    @Test
    fun probabilitiesSumToOne() {
        val classifier = HeadTrainer.train(clusters(4, 8, 8, 0.4f), 4).head
        val p = classifier.probabilities(FloatArray(8) { it.toFloat() })

        assertTrue(abs(p.sum() - 1f) < 1e-5f)
    }

    @Test
    fun holdsOutPartOfEveryClass() {
        val data = clusters(3, 10, 4, 0.1f) + Example(FloatArray(4) { 1f }, 2).let { listOf(it) }
        val (train, validation) = HeadTrainer.stratifiedSplit(data, 0.2f, Random(0))

        assertEquals(data.size, train.size + validation.size)
        assertEquals(setOf(0, 1, 2), validation.map { it.label }.toSet())
        assertEquals(2, validation.count { it.label == 0 })
    }

    @Test
    fun scaleOfEmbeddingsDoesNotMatter() {
        val data = clusters(3, 12, 8, 0.3f)
        val classifier = HeadTrainer.train(data, 3).head
        val x = data.first().embedding

        assertEquals(classifier.predict(x), classifier.predict(FloatArray(x.size) { x[it] * 100 }))
    }

    @Test
    fun hiddenLayerLearnsWhatOneLayerCant() {
        // Class 0 lies around v and -v, class 1 around u and -u: no single layer can split that
        val random = Random(3)
        val v = FloatArray(16) { random.nextFloat() * 2 - 1 }
        val u = FloatArray(16) { random.nextFloat() * 2 - 1 }
        fun around(centre: FloatArray, sign: Int, label: Int) =
            List(40) { Example(FloatArray(16) { sign * centre[it] + (random.nextFloat() - 0.5f) * 0.2f }, label) }
        val data = around(v, 1, 0) + around(v, -1, 0) + around(u, 1, 1) + around(u, -1, 1)

        assertTrue(HeadTrainer.train(data, 2).validationAccuracy < 0.8f)
        assertTrue(HeadTrainer.train(data, 2, TrainingOptions(hiddenUnits = 32)).validationAccuracy >= 0.95f)
    }

    @Test
    fun warmStartKeepsOldClassesAndAddsNewOnes() {
        val data = clusters(4, 20, 16, 0.2f)
        val first = HeadTrainer.train(data.filter { it.label < 2 }, 2).head
        val grown = first.expanded(4)
        val x = data.first { it.label == 0 }.embedding

        assertEquals(4, grown.classes)
        assertEquals(first.predict(x), grown.predict(x))
        val result = HeadTrainer.train(data, emptyList(), 4, TrainingOptions(), start = first)
        assertTrue(result.trainAccuracy >= 0.95f)
    }

    @Test
    fun imprintingRecognisesANewClassBeforeTraining() {
        val data = clusters(3, 20, 16, 0.2f)
        val head = HeadTrainer.train(data.filter { it.label < 2 }, 2).head.expanded(3)
        head.imprint(2, data.filter { it.label == 2 }.map { it.embedding }, listOf(0, 1))
        val newClass = data.filter { it.label == 2 }

        assertTrue(newClass.count { head.predict(it.embedding) == 2 } >= newClass.size * 0.9)
    }

    @Test
    fun classBalancingHelpsTheSmallClass() {
        val data = clusters(2, 200, 8, 1.2f, seed = 5).let { all -> all.filter { it.label == 0 } + all.filter { it.label == 1 }.take(10) }
        val test = clusters(2, 200, 8, 1.2f, seed = 5).filter { it.label == 1 }.drop(10)
        fun recall(balanced: Boolean) = HeadTrainer.train(data, 2, TrainingOptions(classBalanced = balanced)).head
            .let { head -> test.count { head.predict(it.embedding) == 1 } }

        assertTrue(recall(true) >= recall(false))
    }
}
