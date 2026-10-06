package com.uri.lee.dl.core.training

/**
 * Nearest class mean (Mensink et al., 2013): each class is the mean of its normalised embeddings,
 * and an image goes to the class whose mean is most similar (cosine). Learning a class is just
 * adding to its sums, so new classes and new photos never disturb old ones: no training, no
 * forgetting, and the only memory is one sum per class.
 */
class PrototypeClassifier(
    override val dimensions: Int,
    /** Softmax temperature on cosine similarities, for probabilities (it doesn't change the ranking). */
    val temperature: Float = 0.05f,
) : EmbeddingClassifier {

    private val sums = mutableListOf<FloatArray>()
    private val counts = mutableListOf<Int>()

    override val classes: Int get() = sums.size

    /** Adds [examples] to their classes' means, making room for any new class. */
    fun learn(examples: List<Example>) {
        for (example in examples) {
            require(example.embedding.size == dimensions) { "Wrong embedding size" }
            while (sums.size <= example.label) {
                sums += FloatArray(dimensions)
                counts += 0
            }
            val x = normalized(example.embedding)
            val sum = sums[example.label]
            for (i in 0 until dimensions) sum[i] += x[i]
            counts[example.label]++
        }
    }

    /** The normalised mean of each class (all zeros for a class with no examples yet). */
    fun prototypes(): List<FloatArray> = sums.map { normalized(it) }

    override fun probabilities(embedding: FloatArray): FloatArray {
        val x = normalized(embedding)
        val prototypes = prototypes()
        return softmax(FloatArray(prototypes.size) { c ->
            var dot = 0f
            for (i in 0 until dimensions) dot += prototypes[c][i] * x[i]
            if (counts[c] == 0) -1e9f else dot / temperature
        })
    }
}
