package com.uri.lee.dl.feature.training

import com.uri.lee.dl.core.training.AnalysisBundle
import com.uri.lee.dl.core.training.ClassifierHead
import com.uri.lee.dl.core.training.ModelPack
import com.uri.lee.dl.core.training.SldaClassifier

/**
 * A trained model with what's needed to use and check it outside the app, as a zip: the
 * standalone model (model.tflite, labels.txt), its trained layers (head.json), its settings and
 * last report (manifest.json), the embedding of every photo it was trained and tested on
 * (embeddings.npy, one row per line of examples.csv, which says each photo's species and whether
 * it was for training, checking or testing), and Python scripts: predict.py and evaluate.py run
 * the model on folders of photos, reproduce.py recalculates the report from the embeddings.
 */
object AnalysisExport {

    /** The scripts and notes that go with every export, as files under files/analysis/ in the app's resources. */
    val SCRIPTS = listOf("README.md", "requirements.txt", "predict.py", "evaluate.py", "reproduce.py")

    fun build(
        model: UserModel,
        head: ClassifierHead,
        tflite: ByteArray,
        examples: List<StoredExample>,
        scripts: Map<String, ByteArray>,
    ): ByteArray {
        val labels = model.trainedClasses
        val expert = model.settings.expert
        // The photos the last training saw: later ones haven't been learnt from or tested on yet
        val trained = examples.filter { it.photo <= model.trainedThrough && it.classIndex < labels.size }
        val dimensions = trained.firstOrNull()?.embedding?.size ?: head.dimensions
        val csv = buildString {
            appendLine("row,photo,label_index,label,share,original")
            trained.forEachIndexed { row, e ->
                val share = ModelTrainer.share(e.photo, expert).name.lowercase()
                appendLine("$row,${e.photo},${e.classIndex},${csvField(labels[e.classIndex])},$share,${e.original}")
            }
        }
        val files = linkedMapOf<String, ByteArray>()
        for (name in SCRIPTS) scripts[name]?.let { files[name] = it }
        files["manifest.json"] = manifest(model, head).encodeToByteArray()
        files["model.tflite"] = tflite
        files["labels.txt"] = labels.joinToString("\n", postfix = "\n").encodeToByteArray()
        files["head.json"] = ModelPack(model.backbone, labels, head).toJson().encodeToByteArray()
        files["examples.csv"] = csv.encodeToByteArray()
        files["embeddings.npy"] = AnalysisBundle.npy(trained.map { it.embedding }, dimensions)
        return AnalysisBundle.zip(files)
    }

    private fun manifest(model: UserModel, head: ClassifierHead): String = buildString {
        val s = model.settings
        val e = s.expert
        val r = model.report
        append("{\n")
        append("  \"format\": \"herblens-analysis\",\n")
        append("  \"version\": 1,\n")
        append("  \"name\": ${quote(model.name)},\n")
        append("  \"backbone\": ${quote(model.backbone)},\n")
        append("  \"input\": {\"size\": 224, \"crop\": \"centre square\", \"resize\": \"bilinear\", \"colour\": \"RGB\", \"scale\": \"pixel / 255\"},\n")
        append("  \"head\": {\"dimensions\": ${head.dimensions}, \"hidden\": ${head.hidden}, \"classes\": ${head.classes}},\n")
        append("  \"labels\": [${model.trainedClasses.joinToString(", ") { quote(it) }}],\n")
        append("  \"update\": ${quote(s.update.name.lowercase())},\n")
        append("  \"slda_shrinkage\": ${SldaClassifier(1).shrinkage},\n")
        append("  \"settings\": {")
        append("\"learning_rate\": ${e.learningRate}, \"batch_size\": ${e.batchSize}, \"max_epochs\": ${e.maxEpochs}, ")
        append("\"patience\": ${e.patience}, \"l2\": ${e.l2}, \"validation_share\": ${e.validationShare}, ")
        append("\"test_share\": ${e.testShare}, \"seed\": ${e.seed}, \"class_balanced\": ${e.classBalanced}, ")
        append("\"hidden_units\": ${e.hiddenUnits}, \"replay_per_class\": ${e.replayPerClass}, \"augment\": ${s.quality.augment}},\n")
        append("  \"trained_through_photo\": ${model.trainedThrough},\n")
        if (r == null) {
            append("  \"report\": null\n")
        } else {
            append("  \"report\": {\n")
            append("    \"accuracy\": ${number(r.accuracy)},\n")
            append("    \"test_photos\": ${r.testPhotos},\n")
            append("    \"held_out\": ${r.heldOut},\n")
            append("    \"epochs\": ${r.epochs},\n")
            append("    \"train_millis\": ${r.trainMillis},\n")
            append("    \"per_class_recall\": {${r.perClass.joinToString(", ") { (name, v) -> "${quote(name)}: ${number(v)}" }}},\n")
            append("    \"confusion\": [${r.confusion.joinToString(", ") { row -> row.joinToString(", ", "[", "]") }}]\n")
            append("  }\n")
        }
        append("}\n")
    }

    /** JSON has no NaN: a class with no test photos has no recall. */
    private fun number(v: Float) = if (v.isNaN()) "null" else v.toString()

    private fun quote(text: String) = buildString {
        append('"')
        for (ch in text) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch < ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            else -> append(ch)
        }
        append('"')
    }

    private fun csvField(text: String) =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
