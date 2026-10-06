package com.uri.lee.dl.core.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TfliteExportTest {

    /** A "backbone" whose output is its input: one [1, dimensions] float tensor, no operators. */
    private fun identityBackbone(dimensions: Int): ByteArray {
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

    private fun labels(n: Int) = List(n) { "Species $it" }

    private fun floats(reader: FlatBufferReader, buffer: Int): FloatArray {
        val data = reader.deref(reader.field(buffer, 0)!!)
        return FloatArray(reader.int(data) / 4) { Float.fromBits(reader.int(data + 4 + 4 * it)) }
    }

    @Test
    fun addsTheHeadAfterTheEmbedding() {
        val head = HeadTrainer.train(clusters(3, 10, 8, 0.3f), 3, TrainingOptions(hiddenUnits = 4, maxEpochs = 5)).head
        val model = FlatBufferReader(TfliteExport.export(identityBackbone(8), head, labels(head.classes)))

        assertEquals("TFL3", model.bytes.decodeToString(4, 8))
        val graph = model.tables(model.root, 2).single()
        val tensors = model.tables(graph, TfliteExport.SUBGRAPH_TENSORS)
        assertEquals("embedding", model.string(tensors[0], TfliteExport.TENSOR_NAME))
        assertEquals(listOf(0), model.ints(graph, TfliteExport.SUBGRAPH_INPUTS))
        val output = tensors[model.ints(graph, TfliteExport.SUBGRAPH_OUTPUTS).single()]
        assertEquals("probabilities", model.string(output, TfliteExport.TENSOR_NAME))
        assertEquals(listOf(1, 3), model.ints(output, TfliteExport.TENSOR_SHAPE))

        val codes = model.tables(model.root, 1).map { model.intField(it, TfliteExport.OPCODE_BUILTIN) }
        val operators = model.tables(graph, TfliteExport.SUBGRAPH_OPERATORS).map { codes[model.intField(it, 0)] }
        assertEquals(listOf(TfliteExport.L2_NORMALIZATION, TfliteExport.FULLY_CONNECTED, TfliteExport.FULLY_CONNECTED, TfliteExport.SOFTMAX), operators)

        // The weights, in order, are exactly the head's
        val buffers = model.tables(model.root, 4)
        // The last buffer is the metadata
        val weights = buffers.drop(1).dropLast(1).map { floats(model, it) }.reduce { a, b -> a + b }
        assertTrue(head.params.contentEquals(weights))
    }

    @Test
    fun weightDataIsAlignedFor16Bytes() {
        val head = HeadTrainer.train(clusters(3, 10, 8, 0.3f), 3, TrainingOptions(maxEpochs = 2)).head
        val model = FlatBufferReader(TfliteExport.export(identityBackbone(8), head, labels(head.classes)))

        model.tables(model.root, 4).drop(1).forEach { buffer -> assertEquals(0, (model.deref(model.field(buffer, 0)!!) + 4) % 16) }
    }

    @Test
    fun refusesAMismatchedBackbone() {
        val head = HeadTrainer.train(clusters(3, 10, 8, 0.3f), 3, TrainingOptions(maxEpochs = 2)).head

        assertFailsWith<IllegalArgumentException> { TfliteExport.export(identityBackbone(16), head, labels(head.classes)) }
    }

    @Test
    fun carriesItsLabelsAndMetadata() {
        val head = HeadTrainer.train(clusters(3, 10, 8, 0.3f), 3, TrainingOptions(maxEpochs = 2)).head
        val bytes = TfliteExport.export(identityBackbone(8), head, listOf("Lantana camara", "Mimosa pigra", "Cây mắc cỡ"))
        val model = FlatBufferReader(bytes)

        assertEquals(listOf("Lantana camara", "Mimosa pigra", "Cây mắc cỡ"), TfliteExport.labels(bytes))
        val entry = model.tables(model.root, 6).single()
        assertEquals("TFLITE_METADATA", model.string(entry, 0))
        val buffer = model.tables(model.root, 4)[model.intField(entry, 1)]
        val data = model.deref(model.field(buffer, 0)!!)
        val metadata = FlatBufferReader(bytes.copyOfRange(data + 4, data + 4 + model.int(data)))
        assertEquals("M001", metadata.bytes.decodeToString(4, 8))
        val output = metadata.tables(metadata.tables(metadata.root, 3).single(), 3).single()
        assertEquals("labels.txt", metadata.string(metadata.tables(output, 6).single(), 0))
    }

    @Test
    fun crcMatchesTheStandard() {
        assertEquals(0xCBF43926.toInt(), StoredZip.crc32("123456789".encodeToByteArray()))
    }
}
