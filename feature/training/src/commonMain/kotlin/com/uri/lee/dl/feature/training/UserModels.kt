package com.uri.lee.dl.feature.training

import com.uri.lee.dl.core.training.ClassifierHead
import com.uri.lee.dl.core.training.EpochStats
import com.uri.lee.dl.core.training.Evaluation
import com.uri.lee.dl.core.training.Example
import com.uri.lee.dl.core.training.HeadTrainer
import com.uri.lee.dl.core.training.ModelPack
import com.uri.lee.dl.core.training.TrainingOptions
import com.uri.lee.dl.domain.training.AppFiles
import kotlin.random.Random

/** Simple choices for people who don't want to think about training settings. */
enum class Quality(val backbone: String, val augment: Boolean) {
    /** The small backbone: quickest to train and run. */
    FAST("mobilenet_v3_small", augment = false),

    /** The large backbone. */
    BALANCED("mobilenet_v3_large", augment = false),

    /** The large backbone, plus two cropped copies of every photo to learn from. */
    BEST("mobilenet_v3_large", augment = true),
}

/** How a trained model learns species or photos added later. */
enum class UpdateMode {
    /** Carries on from the last model, replaying a sample of earlier photos so they aren't forgotten. */
    REPLAY,

    /** Trains a new model on every photo. */
    RETRAIN_ALL,
}

/** Settings for experts; the defaults suit most models. */
data class ExpertSettings(
    val learningRate: Float = 0.01f,
    val batchSize: Int = 16,
    val maxEpochs: Int = 300,
    val patience: Int = 15,
    val l2: Float = 1e-3f,
    /** Share of photos set aside to decide when to stop training. */
    val validationShare: Float = 0.15f,
    /** Share of photos set aside to measure the model (never trained on). */
    val testShare: Float = 0.15f,
    val seed: Int = 42,
    val classBalanced: Boolean = true,
    /** Units in a hidden layer; 0 for none. */
    val hiddenUnits: Int = 0,
    /** Earlier photos per species replayed when adding to a trained model. */
    val replayPerClass: Int = 20,
)

data class TrainingSettings(
    val quality: Quality = Quality.BALANCED,
    val update: UpdateMode = UpdateMode.REPLAY,
    val expert: ExpertSettings = ExpertSettings(),
) {
    fun options(seed: Int = expert.seed) = TrainingOptions(
        learningRate = expert.learningRate,
        l2 = expert.l2,
        batchSize = expert.batchSize,
        maxEpochs = expert.maxEpochs,
        patience = expert.patience,
        hiddenUnits = expert.hiddenUnits,
        classBalanced = expert.classBalanced,
        seed = seed,
    )
}

/** A photo's embedding. [photo] numbers photos in the order they were added; crops share their photo's number. */
class StoredExample(val classIndex: Int, val photo: Int, val original: Boolean, val embedding: FloatArray)

/** How well the last training went, on photos set aside from training. */
data class ModelReport(
    val accuracy: Float,
    /** Each species and the share of its set-aside photos the model got right. */
    val perClass: List<Pair<String, Float>>,
    /** The two species most often mistaken for each other, if any were. */
    val confused: Pair<String, String>?,
    val testPhotos: Int,
    val epochs: Int,
    /** False when there were too few photos to set any aside for testing: then it's on the validation photos. */
    val heldOut: Boolean,
    val history: List<EpochStats> = emptyList(),
    /** [actual][predicted] counts on the test photos. */
    val confusion: List<List<Int>> = emptyList(),
)

/** A model on the device: one the user trains here, or an imported .tflite. */
data class UserModel(
    val id: String,
    val name: String,
    val classes: List<String>,
    val photoCounts: Map<String, Int> = emptyMap(),
    val settings: TrainingSettings = TrainingSettings(),
    val imported: Boolean = false,
    /** Photos up to this number were in the last training; later ones are new. */
    val trainedThrough: Int = -1,
    val trainedClasses: List<String> = emptyList(),
    val report: ModelReport? = null,
    /** Imported from a model shared by Herb Lens, which can go on learning here. */
    val importedTrainable: Boolean = false,
) {
    val backbone: String get() = settings.quality.backbone
    val isTrained: Boolean get() = imported || trainedClasses.isNotEmpty()
    val photos: Int get() = photoCounts.values.sum()

    /** Whether photos or species were added since the last training. */
    fun hasChanges(lastPhoto: Int): Boolean = !imported && (lastPhoto > trainedThrough || classes != trainedClasses)
}

/**
 * Models in the app's files, one folder each under models/: settings (meta.txt), species
 * (classes.txt), every photo's embedding (embeddings.bin), the trained layers (head.json), the
 * standalone classifier (model.tflite) and the last report (report.txt).
 */
class UserModelStore(private val files: AppFiles) {

    suspend fun list(): List<UserModel> = files.list(ROOT).mapNotNull { load(it) }.sortedBy { it.name.lowercase() }

    suspend fun load(id: String): UserModel? {
        val meta = files.read("$ROOT/$id/meta.txt")?.decodeToString()?.let(::values) ?: return null
        val classes = files.read("$ROOT/$id/classes.txt")?.decodeToString()?.lines()?.filter { it.isNotEmpty() }.orEmpty()
        val counts = if (meta["imported"] == "true") emptyMap() else examples(id).filter { it.original }.groupingBy { classes[it.classIndex] }.eachCount()
        return UserModel(
            id = id,
            name = meta["name"] ?: id,
            classes = classes,
            photoCounts = counts,
            settings = decodeSettings(meta),
            imported = meta["imported"] == "true",
            trainedThrough = meta["trainedThrough"]?.toIntOrNull() ?: -1,
            trainedClasses = meta["trainedClasses"]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty(),
            report = files.read("$ROOT/$id/report.txt")?.decodeToString()?.let(::decodeReport),
            importedTrainable = meta["importedTrainable"] == "true",
        )
    }

    suspend fun save(model: UserModel) {
        files.write("$ROOT/${model.id}/meta.txt", encodeMeta(model).encodeToByteArray())
        files.write("$ROOT/${model.id}/classes.txt", model.classes.joinToString("\n", postfix = "\n").encodeToByteArray())
        model.report?.let { files.write("$ROOT/${model.id}/report.txt", encodeReport(it).encodeToByteArray()) }
    }

    suspend fun delete(id: String) = files.delete("$ROOT/$id")

    suspend fun examples(id: String): List<StoredExample> = files.read("$ROOT/$id/embeddings.bin")?.let(::decodeExamples).orEmpty()

    suspend fun addExamples(id: String, examples: List<StoredExample>) {
        if (examples.isEmpty()) return
        files.write("$ROOT/$id/embeddings.bin", encodeExamples(examples(id) + examples))
    }

    suspend fun head(id: String): ClassifierHead? = files.read("$ROOT/$id/head.json")?.let { ModelPack.fromJson(it.decodeToString()).head }

    suspend fun saveTrained(model: UserModel, head: ClassifierHead, tflite: ByteArray) {
        files.write("$ROOT/${model.id}/head.json", ModelPack(model.backbone, model.trainedClasses, head).toJson().encodeToByteArray())
        files.write("$ROOT/${model.id}/model.tflite", tflite)
        save(model)
    }

    suspend fun saveImported(model: UserModel, tflite: ByteArray) {
        files.write("$ROOT/${model.id}/model.tflite", tflite)
        save(model)
    }

    suspend fun tflite(id: String): ByteArray? = files.read("$ROOT/$id/model.tflite")

    /** Where the embedder loader can open the model's .tflite. */
    suspend fun tfliteLocation(id: String): String = files.location("$ROOT/$id/model.tflite")

    private fun values(text: String) = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun encodeMeta(model: UserModel) = buildString {
        appendLine("name=${model.name.replace('\n', ' ')}")
        appendLine("imported=${model.imported}")
        appendLine("importedTrainable=${model.importedTrainable}")
        appendLine("trainedThrough=${model.trainedThrough}")
        appendLine("trainedClasses=${model.trainedClasses.joinToString(SEPARATOR)}")
        val s = model.settings
        val e = s.expert
        appendLine("quality=${s.quality.name}")
        appendLine("update=${s.update.name}")
        appendLine("learningRate=${e.learningRate}")
        appendLine("batchSize=${e.batchSize}")
        appendLine("maxEpochs=${e.maxEpochs}")
        appendLine("patience=${e.patience}")
        appendLine("l2=${e.l2}")
        appendLine("validationShare=${e.validationShare}")
        appendLine("testShare=${e.testShare}")
        appendLine("seed=${e.seed}")
        appendLine("classBalanced=${e.classBalanced}")
        appendLine("hiddenUnits=${e.hiddenUnits}")
        appendLine("replayPerClass=${e.replayPerClass}")
    }

    private fun decodeSettings(v: Map<String, String>): TrainingSettings {
        val d = ExpertSettings()
        return TrainingSettings(
            quality = v["quality"]?.let { q -> Quality.entries.firstOrNull { it.name == q } } ?: Quality.BALANCED,
            update = v["update"]?.let { u -> UpdateMode.entries.firstOrNull { it.name == u } } ?: UpdateMode.REPLAY,
            expert = ExpertSettings(
                learningRate = v["learningRate"]?.toFloatOrNull() ?: d.learningRate,
                batchSize = v["batchSize"]?.toIntOrNull() ?: d.batchSize,
                maxEpochs = v["maxEpochs"]?.toIntOrNull() ?: d.maxEpochs,
                patience = v["patience"]?.toIntOrNull() ?: d.patience,
                l2 = v["l2"]?.toFloatOrNull() ?: d.l2,
                validationShare = v["validationShare"]?.toFloatOrNull() ?: d.validationShare,
                testShare = v["testShare"]?.toFloatOrNull() ?: d.testShare,
                seed = v["seed"]?.toIntOrNull() ?: d.seed,
                classBalanced = v["classBalanced"]?.toBooleanStrictOrNull() ?: d.classBalanced,
                hiddenUnits = v["hiddenUnits"]?.toIntOrNull() ?: d.hiddenUnits,
                replayPerClass = v["replayPerClass"]?.toIntOrNull() ?: d.replayPerClass,
            ),
        )
    }

    private fun encodeReport(r: ModelReport) = buildString {
        appendLine("accuracy=${r.accuracy}")
        appendLine("testPhotos=${r.testPhotos}")
        appendLine("epochs=${r.epochs}")
        appendLine("heldOut=${r.heldOut}")
        r.confused?.let { appendLine("confused=${it.first}$SEPARATOR${it.second}") }
        r.perClass.forEach { (name, accuracy) -> appendLine("class=$accuracy$SEPARATOR$name") }
        r.confusion.forEach { row -> appendLine("confusion=${row.joinToString(",")}") }
        r.history.forEach { h -> appendLine("epoch=${h.epoch},${h.trainLoss},${h.validationLoss},${h.validationAccuracy}") }
    }

    private fun decodeReport(text: String): ModelReport? {
        val lines = text.lines()
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
        return ModelReport(
            accuracy = value("accuracy")?.toFloatOrNull() ?: return null,
            perClass = lines.filter { it.startsWith("class=") }.map { it.substringAfter('=') }
                .map { it.substringAfter(SEPARATOR) to (it.substringBefore(SEPARATOR).toFloatOrNull() ?: Float.NaN) },
            confused = value("confused")?.split(SEPARATOR)?.takeIf { it.size == 2 }?.let { it[0] to it[1] },
            testPhotos = value("testPhotos")?.toIntOrNull() ?: 0,
            epochs = value("epochs")?.toIntOrNull() ?: 0,
            heldOut = value("heldOut") != "false",
            history = lines.filter { it.startsWith("epoch=") }.mapNotNull { line ->
                val p = line.substringAfter('=').split(',')
                if (p.size < 4) null else EpochStats(p[0].toInt(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
            },
            confusion = lines.filter { it.startsWith("confusion=") }.map { l -> l.substringAfter('=').split(',').mapNotNull { it.toIntOrNull() } },
        )
    }

    companion object {
        private const val ROOT = "models"
        private const val SEPARATOR = "\t"

        /** Little-endian: "HLU1", count, dimensions, then per example its class, photo, original flag and values. */
        fun encodeExamples(examples: List<StoredExample>): ByteArray {
            val dimensions = examples.firstOrNull()?.embedding?.size ?: 0
            val out = ByteArray(12 + examples.size * (12 + 4 * dimensions))
            var at = 0
            fun int(v: Int) { for (i in 0 until 4) out[at++] = (v shr (8 * i)).toByte() }
            "HLU1".encodeToByteArray().copyInto(out); at = 4
            int(examples.size); int(dimensions)
            for (e in examples) {
                int(e.classIndex); int(e.photo); int(if (e.original) 1 else 0)
                for (v in e.embedding) int(v.toRawBits())
            }
            return out
        }

        fun decodeExamples(bytes: ByteArray): List<StoredExample> {
            if (bytes.size < 12 || bytes.decodeToString(0, 4) != "HLU1") return emptyList()
            fun int(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
                ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)
            val count = int(4)
            val dimensions = int(8)
            var at = 12
            return List(count) {
                val classIndex = int(at); val photo = int(at + 4); val original = int(at + 8) == 1
                at += 12
                val embedding = FloatArray(dimensions) { Float.fromBits(int(at + 4 * it)) }
                at += 4 * dimensions
                StoredExample(classIndex, photo, original, embedding)
            }
        }
    }
}

/**
 * What a model shared from Herb Lens carries inside its .tflite besides labels.txt, so whoever
 * imports it can go on training it: the trained layers and backbone (model.json) and a sample of
 * each species' embeddings to replay (replay.bin), about 100 KB per species. TFLite tools ignore them.
 */
object SharedModel {
    const val MODEL = "herblens/model.json"
    const val REPLAY = "herblens/replay.bin"

    class Unpacked(val pack: ModelPack, val replay: List<StoredExample>)

    fun pack(model: UserModel, head: ClassifierHead, examples: List<StoredExample>): Map<String, ByteArray> {
        val expert = model.settings.expert
        val random = Random(expert.seed)
        val replay = examples.filter { it.original && ModelTrainer.share(it.photo, expert) == ModelTrainer.Share.TRAIN }
            .groupBy { it.classIndex }.toList().sortedBy { it.first }
            .flatMap { (_, list) -> list.shuffled(random).take(expert.replayPerClass) }
        return mapOf(
            MODEL to ModelPack(model.backbone, model.classes, head).toJson().encodeToByteArray(),
            REPLAY to UserModelStore.encodeExamples(replay),
        )
    }

    /** What a shared .tflite carries for further training, or null for any other model. */
    fun unpack(tflite: ByteArray): Unpacked? {
        val json = com.uri.lee.dl.core.training.TfliteExport.packedFile(tflite, MODEL) ?: return null
        val pack = runCatching { ModelPack.fromJson(json.decodeToString()) }.getOrNull() ?: return null
        val replay = com.uri.lee.dl.core.training.TfliteExport.packedFile(tflite, REPLAY)?.let(UserModelStore::decodeExamples).orEmpty()
        return Unpacked(pack, replay)
    }

    /**
     * The replay sample renumbered as this device's photos, all in the training share (so none
     * is ever used to test the model here).
     */
    fun renumbered(replay: List<StoredExample>, expert: ExpertSettings): List<StoredExample> {
        var next = 0
        return replay.map { e ->
            while (ModelTrainer.share(next, expert) != ModelTrainer.Share.TRAIN) next++
            StoredExample(e.classIndex, next++, true, e.embedding)
        }
    }
}

/**
 * Trains a model's head from its stored embeddings. Each photo is put in the training, validation
 * or test share by its number and the seed, so a photo stays in the same share as more are added:
 * test photos are never trained on, however many times the model is updated.
 */
object ModelTrainer {

    enum class Share { TRAIN, VALIDATION, TEST }

    class Result(val head: ClassifierHead, val report: ModelReport, val trainedThrough: Int)

    fun share(photo: Int, expert: ExpertSettings): Share {
        val bucket = Random(expert.seed * 1_000_003 + photo).nextFloat()
        return when {
            bucket < expert.testShare -> Share.TEST
            bucket < expert.testShare + expert.validationShare -> Share.VALIDATION
            else -> Share.TRAIN
        }
    }

    /**
     * Trains [model] on [examples]: from scratch, or (replay) on from [previous] with the photos
     * added since plus a sample of earlier ones. [onEpoch] reports progress.
     */
    fun train(model: UserModel, examples: List<StoredExample>, previous: ClassifierHead?, onEpoch: (EpochStats) -> Unit = {}): Result {
        val classes = model.classes.size
        require(classes >= 2) { "Needs at least two species" }
        val expert = model.settings.expert
        val byShare = examples.groupBy { share(it.photo, expert) }
        fun Iterable<StoredExample>.toExamples() = map { Example(it.embedding, it.classIndex) }
        val trainAll = byShare[Share.TRAIN].orEmpty()
        val validation = byShare[Share.VALIDATION].orEmpty().filter { it.original }.toExamples()
        val replay = previous != null && model.settings.update == UpdateMode.REPLAY &&
            model.trainedClasses.isNotEmpty() && model.classes.take(model.trainedClasses.size) == model.trainedClasses
        val lastPhoto = examples.maxOfOrNull { it.photo } ?: -1

        val train: List<Example>
        val start: ClassifierHead?
        if (replay) {
            val random = Random(expert.seed + lastPhoto)
            fun isNew(e: StoredExample) = e.photo > model.trainedThrough || e.classIndex >= model.trainedClasses.size
            val fresh = trainAll.filter(::isNew)
            val earlier = trainAll.filter { !isNew(it) && it.original }.groupBy { it.classIndex }
                .flatMap { (_, list) -> list.shuffled(random).take(expert.replayPerClass) }
            train = (fresh + earlier).toExamples()
            start = previous!!.expanded(classes).also { grown ->
                val known = model.trainedClasses.indices.toList()
                for (c in model.trainedClasses.size until classes) {
                    grown.imprint(c, fresh.filter { it.classIndex == c }.map { it.embedding }, known)
                }
            }
        } else {
            train = trainAll.toExamples()
            start = null
        }
        require(train.isNotEmpty()) { "No photos to train on" }

        val result = HeadTrainer.train(train, validation, classes, model.settings.options(), start, onEpoch)
        val test = byShare[Share.TEST].orEmpty().filter { it.original }.toExamples()
        val heldOut = test.isNotEmpty()
        val evaluation = Evaluation.of(result.head, test.ifEmpty { validation.ifEmpty { train } }, classes)
        return Result(result.head, report(model.classes, evaluation, result.epochs, heldOut, result.history), lastPhoto)
    }

    private fun report(classes: List<String>, e: Evaluation, epochs: Int, heldOut: Boolean, history: List<EpochStats>): ModelReport {
        var confused: Pair<String, String>? = null
        var most = 0
        for (a in classes.indices) for (b in a + 1 until classes.size) {
            val n = e.confusion[a][b] + e.confusion[b][a]
            if (n > most) {
                most = n
                confused = classes[a] to classes[b]
            }
        }
        return ModelReport(
            accuracy = e.accuracy,
            perClass = classes.mapIndexed { i, name -> name to e.perClass[i].recall },
            confused = confused,
            testPhotos = e.examples,
            epochs = epochs,
            heldOut = heldOut,
            history = history,
            confusion = e.confusion.map { it.toList() },
        )
    }
}
