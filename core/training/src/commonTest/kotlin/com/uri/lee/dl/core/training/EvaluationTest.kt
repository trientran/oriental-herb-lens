package com.uri.lee.dl.core.training

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluationTest {

    /** Always gives the same probabilities, whatever the image: [answers] by the example's first value. */
    private class Fixed(private val answers: Map<Float, FloatArray>) : EmbeddingClassifier {
        override val classes = answers.values.first().size
        override val dimensions = 1
        override fun probabilities(embedding: FloatArray) = answers.getValue(embedding[0])
    }

    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-4f, "expected $expected, was $actual")

    @Test
    fun scoresAKnownConfusion() {
        // Two of class 0 right, one of class 0 called 1, one of class 1 right
        val classifier = Fixed(mapOf(1f to floatArrayOf(0.9f, 0.1f), 2f to floatArrayOf(0.4f, 0.6f)))
        val test = listOf(Example(floatArrayOf(1f), 0), Example(floatArrayOf(1f), 0), Example(floatArrayOf(2f), 0), Example(floatArrayOf(2f), 1))
        val e = Evaluation.of(classifier, test)

        assertEquals(listOf(2, 1), e.confusion[0].toList())
        assertEquals(listOf(0, 1), e.confusion[1].toList())
        near(0.75f, e.accuracy)
        near((2f / 3 + 1f) / 2, e.balancedAccuracy)
        near(1f, e.perClass[0].precision)
        near(0.5f, e.perClass[1].precision)
        near(0.5f, e.cohensKappa) // p_o 0.75, p_e (3·2 + 1·2) / 16 = 0.5
        near(1f, e.topKAccuracy)
        // ECE: confidence 0.9 bin all right (|1 - 0.9|·0.5), 0.6 bin half right (|0.5 - 0.6|·0.5)
        near(0.1f, e.calibrationError)
    }

    @Test
    fun aClassNeverPredictedHasNoPrecision() {
        val e = Evaluation.of(Fixed(mapOf(1f to floatArrayOf(1f, 0f))), listOf(Example(floatArrayOf(1f), 0), Example(floatArrayOf(1f), 1)))

        assertTrue(e.perClass[1].precision.isNaN())
        near(0f, e.perClass[1].f1)
    }

    @Test
    fun continualMetricsFromAnAccuracyMatrix() {
        val m = ContinualMetrics(listOf(listOf(0.9f), listOf(0.6f, 0.8f), listOf(0.5f, 0.7f, 0.9f)))

        near(0.7f, m.finalAverageAccuracy)
        near(((0.9 + 0.7 + 0.7) / 3).toFloat(), m.averageIncrementalAccuracy)
        near(((0.5f - 0.9f) + (0.7f - 0.8f)) / 2, m.backwardTransfer)
        near(((0.9f - 0.5f) + (0.8f - 0.7f)) / 2, m.forgetting)
    }

    @Test
    fun prototypesLearnClassesOneAtATimeWithoutForgetting() {
        val data = clusters(4, 20, 16, 0.3f)
        val prototypes = PrototypeClassifier(16)
        prototypes.learn(data.filter { it.label < 2 })
        val before = data.filter { it.label < 2 }.count { prototypes.predict(it.embedding) == it.label }
        prototypes.learn(data.filter { it.label >= 2 })

        assertEquals(4, prototypes.classes)
        assertEquals(40, before)
        assertTrue(data.count { prototypes.predict(it.embedding) == it.label } >= 76)
    }

    @Test
    fun csvNumbersAreTheSameTextOnEveryPlatform() {
        assertEquals("0.1", csvField(0.1f))
        assertEquals("0.001", csvField(1e-3f))
        assertEquals("0.6666667", csvField(2f / 3))
        assertEquals("0.000123456", csvField(0.000123456f))
        assertEquals("123.4568", csvField(123.45678f))
        assertEquals("-2.5", csvField(-2.5))
        assertEquals("1", csvField(1.0f))
        assertEquals("0", csvField(0f))
        assertEquals("", csvField(Float.NaN))
    }
}
