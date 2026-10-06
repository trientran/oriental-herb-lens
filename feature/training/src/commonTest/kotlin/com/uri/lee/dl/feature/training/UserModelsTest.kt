package com.uri.lee.dl.feature.training

import com.uri.lee.dl.domain.training.AppFiles
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class UserModelsTest {

    private class MemoryFiles : AppFiles {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun read(name: String) = files[name]
        override suspend fun write(name: String, bytes: ByteArray) { files[name] = bytes }
        override suspend fun append(name: String, text: String) { files[name] = (files[name] ?: ByteArray(0)) + text.encodeToByteArray() }
        override suspend fun delete(name: String) { files.keys.removeAll { it == name || it.startsWith("$name/") } }
        override suspend fun list(folder: String) = files.keys.filter { it.startsWith("$folder/") }.map { it.removePrefix("$folder/").substringBefore('/') }.distinct()
        override suspend fun location(name: String) = name
    }

    /** [perClass] photos of each class, clustered around a random direction per class. */
    private fun photos(classes: Int, perClass: Int, from: Int = 0, seed: Int = 1): List<StoredExample> {
        val random = Random(seed)
        val centres = List(classes) { FloatArray(16) { random.nextFloat() * 2 - 1 } }
        var photo = from
        return (0 until classes).flatMap { c ->
            List(perClass) { StoredExample(c, photo++, true, FloatArray(16) { centres[c][it] + (random.nextFloat() - 0.5f) * 0.6f }) }
        }
    }

    @Test
    fun storeKeepsModelsTheirSettingsAndPhotos() = runTest {
        val store = UserModelStore(MemoryFiles())
        val settings = TrainingSettings(Quality.BEST, UpdateMode.RETRAIN_ALL, ExpertSettings(learningRate = 0.02f, hiddenUnits = 32))
        val model = UserModel("m1", "Garden weeds", listOf("Lantana", "Mimosa"), settings = settings)
        store.save(model)
        store.addExamples("m1", photos(2, 3))
        store.addExamples("m1", photos(2, 2, from = 6))

        val loaded = assertNotNull(store.load("m1"))
        assertEquals("Garden weeds", loaded.name)
        assertEquals(settings, loaded.settings)
        assertEquals(mapOf("Lantana" to 5, "Mimosa" to 5), loaded.photoCounts)
        assertEquals(listOf("m1"), store.list().map { it.id })
        store.delete("m1")
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun aPhotoStaysInItsShareAsMoreAreAdded() {
        val expert = ExpertSettings()
        val shares = (0 until 200).map { ModelTrainer.share(it, expert) }

        assertEquals(shares, (0 until 200).map { ModelTrainer.share(it, expert) })
        assertTrue(shares.count { it == ModelTrainer.Share.TEST } in 15..45, "test share ${shares.count { it == ModelTrainer.Share.TEST }}")
    }

    @Test
    fun trainsAndReportsOnPhotosItDidNotLearnFrom() {
        val model = UserModel("m", "Test", listOf("a", "b", "c"))
        val result = ModelTrainer.train(model, photos(3, 30), previous = null)

        assertTrue(result.report.accuracy >= 0.9f, "accuracy ${result.report.accuracy}")
        assertTrue(result.report.heldOut)
        assertEquals(89, result.trainedThrough)
        assertEquals(listOf("a", "b", "c"), result.report.perClass.map { it.first })
    }

    @Test
    fun addingASpeciesWithReplayKeepsTheOldOnes() {
        val all = photos(4, 30)
        val firstModel = UserModel("m", "Test", listOf("a", "b", "c"))
        val first = ModelTrainer.train(firstModel, all.filter { it.classIndex < 3 }, previous = null)
        val grown = firstModel.copy(classes = listOf("a", "b", "c", "d"), trainedThrough = first.trainedThrough, trainedClasses = firstModel.classes)
        val second = ModelTrainer.train(grown, all, previous = first.head)

        assertEquals(4, second.head.classes)
        assertTrue(second.report.perClass.all { (_, accuracy) -> accuracy.isNaN() || accuracy >= 0.8f }, "per class ${second.report.perClass}")
    }

    @Test
    fun aSharedModelGoesOnLearningWhereItIsImported() {
        val all = photos(4, 40)
        val source = UserModel("m", "Shared", listOf("a", "b", "c"))
        val trained = ModelTrainer.train(source, all.filter { it.classIndex < 3 }, previous = null)
        val packed = SharedModel.pack(source, trained.head, all.filter { it.classIndex < 3 })

        // What the importer gets: the layers and 20 photos' embeddings per species, all for training
        val replay = SharedModel.renumbered(UserModelStore.decodeExamples(packed.getValue(SharedModel.REPLAY)), source.settings.expert)
        assertEquals(60, replay.size)
        assertTrue(replay.all { ModelTrainer.share(it.photo, source.settings.expert) == ModelTrainer.Share.TRAIN })

        // The importer adds a fourth species with photos of their own
        val next = replay.maxOf { it.photo } + 1
        val newPhotos = all.filter { it.classIndex == 3 }.mapIndexed { i, e -> StoredExample(3, next + i, true, e.embedding) }
        val imported = UserModel(
            "i", "Shared", listOf("a", "b", "c", "d"),
            trainedThrough = replay.maxOf { it.photo }, trainedClasses = listOf("a", "b", "c"), importedTrainable = true,
        )
        val result = ModelTrainer.train(imported, replay + newPhotos, previous = trained.head)
        val oldTest = all.filter { it.classIndex < 3 }.map { com.uri.lee.dl.core.training.Example(it.embedding, it.classIndex) }
        val keptOld = oldTest.count { result.head.predict(it.embedding) == it.label }.toFloat() / oldTest.size

        assertEquals(4, result.head.classes)
        assertTrue(keptOld >= 0.85f, "old species right: $keptOld")
    }
}
