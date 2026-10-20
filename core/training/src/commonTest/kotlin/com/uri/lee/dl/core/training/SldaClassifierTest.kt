package com.uri.lee.dl.core.training

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SldaClassifierTest {

    private val data = clusters(classes = 8, perClass = 40, dimensions = 24, spread = 0.9f, seed = 11)

    @Test
    fun theOrderOfTheDataDoesNotMatter() {
        val atOnce = SldaClassifier(24).apply { learn(data) }.head()
        val shuffled = data.shuffled(Random(5))
        val inParts = SldaClassifier(24).apply {
            learn(shuffled.subList(0, 100))
            learn(shuffled.subList(100, 250))
            learn(shuffled.subList(250, shuffled.size))
        }.head()

        assertEquals(atOnce.params.size, inParts.params.size)
        for (i in atOnce.params.indices) assertTrue(abs(atOnce.params[i] - inParts.params[i]) < 1e-3f, "param $i: ${atOnce.params[i]} vs ${inParts.params[i]}")
    }

    @Test
    fun usesHowDimensionsVaryTogether() {
        // Two classes 0.4 apart along the second dimension, with strong noise shared by the second and
        // third: the nearest mean is fooled by the noise, while the shared covariance sees through it
        val random = Random(3)
        fun sample(c: Int): Example {
            val noise = (random.nextFloat() * 2 - 1) * 3f
            val small = (random.nextFloat() * 2 - 1) * 0.1f
            return Example(floatArrayOf(20f, c * 0.4f + noise + small, noise), c)
        }
        val train = List(400) { sample(it % 2) }
        val test = List(400) { sample(it % 2) }
        fun accuracy(classifier: EmbeddingClassifier) = test.count { classifier.predict(it.embedding) == it.label }.toFloat() / test.size

        val slda = SldaClassifier(3).apply { learn(train) }
        val prototypes = PrototypeClassifier(3).apply { learn(train) }

        assertTrue(accuracy(slda) > 0.95f, "slda ${accuracy(slda)}")
        assertTrue(accuracy(prototypes) < 0.8f, "prototypes ${accuracy(prototypes)}")
    }

    @Test
    fun aClassWithNoExamplesIsNeverChosen() {
        val slda = SldaClassifier(24).apply { learn(data.filter { it.label != 2 }) }

        assertEquals(8, slda.classes)
        assertTrue(data.none { slda.predict(it.embedding) == 2 })
        assertEquals(1f, slda.probabilities(data.first().embedding).sum(), 1e-4f)
    }

    @Test
    fun learnsClassesOverTimeWithoutForgetting() {
        val scenario = Scenario(ScenarioKind.ClassIncremental, 4)
        val slda = ContinualRunner.run(data, 8, scenario, Strategy.Slda, seed = 3)
        val naive = ContinualRunner.run(data, 8, scenario, Strategy.Naive, seed = 3)

        assertTrue(slda.steps.last().evaluation.accuracy > naive.steps.last().evaluation.accuracy)
        assertTrue(slda.continual!!.forgetting < 0.1f, "forgetting ${slda.continual!!.forgetting}")
        assertEquals(8 + 24, slda.steps.last().storedVectors)
    }
}
