package com.uri.lee.dl.core.training

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exports a head on a real backbone, when one is in test-images/backbones (git-ignored; see
 * docs/user-trained-models.md), and leaves the result in build/ for inspecting with other tools.
 */
class RealBackboneExportTest {

    @Test
    fun exportsOnTheRealBackbones() {
        val folder = File("../../test-images/backbones")
        for (name in listOf("mobilenet_v3_small", "mobilenet_v3_large")) {
            val file = File(folder, "$name.tflite").takeIf { it.exists() } ?: continue
            val backbone = file.readBytes()
            val model = FlatBufferReader(backbone)
            val graph = model.tables(model.root, 2).single()
            val tensors = model.tables(graph, TfliteExport.SUBGRAPH_TENSORS)
            val dimensions = model.ints(tensors[model.ints(graph, TfliteExport.SUBGRAPH_OUTPUTS).single()], TfliteExport.TENSOR_SHAPE).last()
            val head = HeadTrainer.train(clusters(3, 10, dimensions, 0.3f), 3, TrainingOptions(maxEpochs = 2)).head
            val labels = listOf("Lantana camara", "Mimosa pigra", "Chromolaena odorata")
            val exported = TfliteExport.export(backbone, head, labels)

            assertEquals(labels, TfliteExport.labels(exported))
            File("build/exported-$name.tflite").writeBytes(exported)
        }
    }
}
