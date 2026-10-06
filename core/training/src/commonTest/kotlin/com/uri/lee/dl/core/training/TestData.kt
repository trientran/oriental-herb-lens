package com.uri.lee.dl.core.training

import kotlin.random.Random

/** [classes] clusters around random directions in [dimensions]-d space, like embeddings of distinct things. */
internal fun clusters(classes: Int, perClass: Int, dimensions: Int, spread: Float, seed: Int = 1): List<Example> {
    val random = Random(seed)
    val centres = List(classes) { FloatArray(dimensions) { random.nextFloat() * 2 - 1 } }
    return (0 until classes).flatMap { c ->
        List(perClass) { Example(FloatArray(dimensions) { centres[c][it] + (random.nextFloat() * 2 - 1) * spread }, c) }
    }
}
