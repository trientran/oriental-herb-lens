package com.uri.lee.dl.feature.training

import com.uri.lee.dl.core.training.ModelPack
import com.uri.lee.dl.core.training.TfliteExport
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalysisExportTest {

    private fun photos(classes: Int, perClass: Int, from: Int = 0): List<StoredExample> {
        val random = Random(7)
        val centres = List(classes) { FloatArray(8) { random.nextFloat() * 2 - 1 } }
        var photo = from
        return (0 until classes).flatMap { c ->
            List(perClass) { StoredExample(c, photo++, true, FloatArray(8) { centres[c][it] + (random.nextFloat() - 0.5f) * 0.6f }) }
        }
    }

    private fun ByteArray.file(name: String) = assertNotNull(TfliteExport.packedFile(this, name), "$name is missing").decodeToString()

    @Test
    fun bundleHoldsTheModelItsPhotosAndScripts() {
        val labels = listOf("Lantana camara", "Mimosa pigra, giant sensitive plant", "Chromolaena odorata")
        val untrained = UserModel("m1", "Weeds", labels)
        val examples = photos(3, 20)
        val result = ModelTrainer.train(untrained, examples, previous = null)
        val model = untrained.copy(trainedThrough = result.trainedThrough, trainedClasses = labels, report = result.report)
        // Photos added after training haven't been learnt from: they stay out
        val later = photos(3, 2, from = 60)
        val scripts = AnalysisExport.SCRIPTS.associateWith { "# $it".encodeToByteArray() }

        val bundle = AnalysisExport.build(model, result.head, "TFL3 model".encodeToByteArray(), examples + later, scripts)

        for (name in AnalysisExport.SCRIPTS) assertEquals("# $name", bundle.file(name))
        assertEquals("TFL3 model", bundle.file("model.tflite"))
        assertEquals(labels, bundle.file("labels.txt").lines().filter { it.isNotEmpty() })
        assertContentEquals(result.head.params, ModelPack.fromJson(bundle.file("head.json")).head.params)

        val rows = bundle.file("examples.csv").lines().filter { it.isNotEmpty() }
        assertEquals("row,photo,label_index,label,share,original", rows.first())
        assertEquals(examples.size, rows.size - 1)
        val expert = model.settings.expert
        examples.forEachIndexed { i, e ->
            val share = ModelTrainer.share(e.photo, expert).name.lowercase()
            val label = labels[e.classIndex].let { if (',' in it) "\"$it\"" else it }
            assertEquals("$i,${e.photo},${e.classIndex},$label,$share,true", rows[i + 1])
        }

        val npy = assertNotNull(TfliteExport.packedFile(bundle, "embeddings.npy"))
        val header = npy.decodeToString(10, 10 + (npy[8].toInt() and 0xFF) + ((npy[9].toInt() and 0xFF) shl 8))
        assertTrue("'shape': (60, 8)" in header, header)
        assertEquals(examples.size * 8 * 4, npy.size - 10 - header.length)
        val first = Float.fromBits((0 until 4).sumOf { (npy[10 + header.length + it].toInt() and 0xFF) shl (8 * it) })
        assertEquals(examples.first().embedding.first(), first)

        val manifest = bundle.file("manifest.json")
        assertTrue("\"update\": \"calculate\"" in manifest, manifest)
        assertTrue("\"accuracy\": ${result.report.accuracy}" in manifest, manifest)
        assertTrue("\"test_photos\": ${result.report.testPhotos}" in manifest, manifest)
        assertTrue("\"Mimosa pigra, giant sensitive plant\"" in manifest, manifest)
    }
}
