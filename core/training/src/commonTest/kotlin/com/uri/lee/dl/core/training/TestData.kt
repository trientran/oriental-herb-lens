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

/** A "backbone" whose output is its input: one [1, dimensions] float tensor, no operators. */
internal fun identityBackbone(dimensions: Int): ByteArray {
    val b = FlatBufferBuilder()
    b.startTable(); val emptyBuffer = b.endTable()
    val name = b.createString("embedding")
    val shape = b.createIntVector(listOf(1, dimensions))
    b.startTable()
    b.offsetField(TfliteExport.TENSOR_SHAPE, shape)
    b.offsetField(TfliteExport.TENSOR_NAME, name)
    b.byteField(TfliteExport.TENSOR_TYPE, 0)
    val tensor = b.endTable()
    val tensors = b.createOffsetVector(listOf(tensor))
    val io = b.createIntVector(listOf(0))
    val operators = b.createOffsetVector(emptyList())
    b.startTable()
    b.offsetField(TfliteExport.SUBGRAPH_TENSORS, tensors)
    b.offsetField(TfliteExport.SUBGRAPH_INPUTS, io)
    b.offsetField(TfliteExport.SUBGRAPH_OUTPUTS, io)
    b.offsetField(TfliteExport.SUBGRAPH_OPERATORS, operators)
    val graph = b.endTable()
    val subgraphs = b.createOffsetVector(listOf(graph))
    val buffers = b.createOffsetVector(listOf(emptyBuffer))
    val opcodes = b.createOffsetVector(emptyList())
    b.startTable()
    b.intField(0, 3)
    b.offsetField(1, opcodes)
    b.offsetField(2, subgraphs)
    b.offsetField(4, buffers)
    return b.finish(b.endTable(), "TFL3")
}
