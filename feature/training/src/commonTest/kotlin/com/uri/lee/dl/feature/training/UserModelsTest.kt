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
    fun storeKeepsReadingAndTrainingTimes() = runTest {
        val store = UserModelStore(MemoryFiles())
        val report = ModelReport(0.9f, listOf("Lantana" to 1f, "Mimosa" to 0.8f), null, testPhotos = 6, epochs = 0, heldOut = true, trainMillis = 1_234)
        store.save(UserModel("m1", "Weeds", listOf("Lantana", "Mimosa"), report = report))
        store.addReadTime("m1", 9_000, 10)
        store.addReadTime("m1", 1_000, 2)
        store.addReadTime("m1", 500, 0)

        val loaded = assertNotNull(store.load("m1"))
        assertEquals(10_000, loaded.readMillis)
        assertEquals(12, loaded.readPhotos)
        assertEquals(1_234, loaded.report?.trainMillis)
    }

    @Test
    fun modelsSavedBeforeTimingLoadWithoutTimes() = runTest {
        val files = MemoryFiles()
        files.files["models/old/meta.txt"] = "name=Old\nimported=false\n".encodeToByteArray()
        files.files["models/old/classes.txt"] = "a\nb\n".encodeToByteArray()
        files.files["models/old/report.txt"] = "accuracy=0.5\ntestPhotos=2\nepochs=3\nheldOut=true\n".encodeToByteArray()

        val loaded = assertNotNull(UserModelStore(files).load("old"))
        assertEquals(0, loaded.readPhotos)
        assertEquals(0, loaded.report?.trainMillis)
    }

    @Test
    fun durationsShowMillisecondsSecondsOrMinutes() {
        assertEquals(ShownDuration.Millis(129), ShownDuration.of(129))
        assertEquals(ShownDuration.Seconds(1), ShownDuration.of(1_499))
        assertEquals(ShownDuration.Seconds(47), ShownDuration.of(46_750))
        assertEquals(ShownDuration.Minutes(1, 0), ShownDuration.of(59_600))
        assertEquals(ShownDuration.Minutes(3, 7), ShownDuration.of(187_000))
    }

    @Test
    fun newModelsBuildOnDinov2AndEarlierModelsKeepTheirBackbone() = runTest {
        assertEquals("dinov2_vits14", TrainingSettings().quality.backbone)
        assertEquals(listOf(Quality.MOST_ACCURATE, Quality.FAST), Quality.entries.filter { it.offered })

        val files = MemoryFiles()
        val store = UserModelStore(files)
        store.save(UserModel("old", "Made before DINOv2", listOf("Lantana", "Mimosa"), settings = TrainingSettings(Quality.BEST)))
        // Its photos were read with the large MobileNet: it must stay on it
        assertEquals("mobilenet_v3_large", assertNotNull(store.load("old")).settings.quality.backbone)
        assertTrue(assertNotNull(store.load("old")).settings.quality.augment)

        // Settings saved before there was a quality: also the large MobileNet
        val meta = files.files.getValue("models/old/meta.txt").decodeToString().lines().filterNot { it.startsWith("quality=") }
        files.files["models/old/meta.txt"] = meta.joinToString("\n").encodeToByteArray()
        assertEquals(Quality.BALANCED, assertNotNull(store.load("old")).settings.quality)
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
    fun newModelsAreCalculatedAndEarlierOnesKeepTheirWay() = runTest {
        assertEquals(UpdateMode.CALCULATE, TrainingSettings().update)

        val files = MemoryFiles()
        val store = UserModelStore(files)
        store.save(UserModel("old", "Trained with replay", listOf("Lantana", "Mimosa")))
        // Settings saved before there was a way to calculate: those models were trained with replay
        val meta = files.files.getValue("models/old/meta.txt").decodeToString().lines().filterNot { it.startsWith("update=") }
        files.files["models/old/meta.txt"] = meta.joinToString("\n").encodeToByteArray()
        assertEquals(UpdateMode.REPLAY, assertNotNull(store.load("old")).settings.update)
    }

    @Test
    fun calculatingNeedsNoRoundsAndAddsSpeciesWithoutForgetting() {
        val all = photos(4, 30)
        val model = UserModel("m", "Test", listOf("a", "b", "c"))
        val first = ModelTrainer.train(model, all.filter { it.classIndex < 3 }, previous = null)
        val grown = model.copy(classes = listOf("a", "b", "c", "d"), trainedThrough = first.trainedThrough, trainedClasses = model.classes)
        val second = ModelTrainer.train(grown, all, previous = first.head)

        assertEquals(0, first.report.epochs)
        assertTrue(first.report.accuracy >= 0.9f, "accuracy ${first.report.accuracy}")
        assertEquals(4, second.head.classes)
        assertTrue(second.report.perClass.all { (_, accuracy) -> accuracy.isNaN() || accuracy >= 0.8f }, "per class ${second.report.perClass}")
    }

    @Test
    fun addingASpeciesWithReplayKeepsTheOldOnes() {
        val all = photos(4, 30)
        val firstModel = UserModel("m", "Test", listOf("a", "b", "c"), settings = TrainingSettings(update = UpdateMode.REPLAY))
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
