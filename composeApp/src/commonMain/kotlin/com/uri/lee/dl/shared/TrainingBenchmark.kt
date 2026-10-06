package com.uri.lee.dl.shared

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.training.Example
import com.uri.lee.dl.core.training.HeadTrainer
import com.uri.lee.dl.core.training.TfliteExport
import com.uri.lee.dl.core.training.TrainingOptions
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.core.training.ClassifierHead
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import com.uri.lee.dl.domain.ml.PhotoReader
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** A labelled photo for the Phase 7 spike; [label] is the class index. */
class BenchmarkPhoto(val label: Int, val image: LocalImage)

/**
 * What the platform supplies for the spike: labelled photos, where each backbone is, how to read
 * a model file, and how to save one (returning a location the embedder loader can open).
 */
class BenchmarkSources(
    val photos: List<BenchmarkPhoto>,
    val backbones: Map<String, String>,
    val readModel: suspend (location: String) -> ByteArray,
    val saveModel: suspend (name: String, bytes: ByteArray) -> String,
)

/**
 * Phase 7 spike (debug builds): for each backbone, embeds the labelled photos, trains a final
 * layer on 16 photos per class (and on 5, for a "few photos" case) and tests on the rest. Reports
 * load, embedding and training times and accuracy; also written to the log.
 */
internal class TrainingBenchmark(private val reader: PhotoReader, private val loader: ImageEmbedderLoader) {

    suspend fun run(sources: BenchmarkSources, report: (String) -> Unit) {
        val classes = sources.photos.maxOf { it.label } + 1
        report("${sources.photos.size} photos, $classes classes")
        val decoded = sources.photos.mapNotNull { photo -> reader.read(photo.image)?.let { photo.label to it.image } }
        report("Decoded ${decoded.size}")
        for ((name, location) in sources.backbones) {
            val loadStart = TimeSource.Monotonic.markNow()
            val embedder = loader.load(location)
            val loadMs = loadStart.elapsedNow().inWholeMilliseconds
            embedder.embed(decoded.first().second) // warm-up, not timed
            val times = mutableListOf<Double>()
            val examples = decoded.map { (label, image) ->
                val mark = TimeSource.Monotonic.markNow()
                val embedding = embedder.embed(image)
                times += mark.elapsedNow().inWholeMicroseconds / 1000.0
                Example(embedding, label)
            }
            embedder.close()
            report("$name: loaded in $loadMs ms; ${examples.first().embedding.size}-d; embedding ${times.median().format()} ms median, ${times.average().format()} ms mean")
            // A softmax layer alone, and with a 100-unit hidden layer like Teachable Machine's
            for (hidden in listOf(0, 100)) for (perClass in listOf(16, 5)) {
                val byClass = examples.groupBy { it.label }
                val train = byClass.values.flatMap { it.take(perClass) }
                val test = byClass.values.flatMap { it.drop(16) }
                val trainStart = TimeSource.Monotonic.markNow()
                val result = HeadTrainer.train(train, classes, TrainingOptions(hiddenUnits = hidden))
                val trainMs = trainStart.elapsedNow().inWholeMilliseconds
                val correct = test.count { result.head.predict(it.embedding) == it.label }
                report(
                    "  ${if (hidden == 0) "softmax" else "hidden $hidden"}, $perClass per class: trained in $trainMs ms " +
                        "(${result.epochs} epochs, best ${result.bestEpoch}); test accuracy ${percent(correct, test.size)} ($correct/${test.size})",
                )
                if (perClass == 16) checkExport(sources, name, location, result.head, decoded.map { it.second }.take(8), report)
            }
        }
        report("Done")
    }

    /**
     * Exports backbone + head as one .tflite, runs it with LiteRT on some photos and compares its
     * probabilities with the Kotlin head's on the backbone's embeddings.
     */
    private suspend fun checkExport(
        sources: BenchmarkSources,
        name: String,
        location: String,
        head: ClassifierHead,
        images: List<ClassifierImage>,
        report: (String) -> Unit,
    ) {
        val exported = TfliteExport.export(sources.readModel(location), head)
        val merged = loader.load(sources.saveModel("${name}_hidden${head.hidden}.tflite", exported))
        val backbone = loader.load(location)
        var maxDifference = 0f
        var agree = 0
        for (image in images) {
            val fromFile = merged.embed(image)
            val fromKotlin = head.probabilities(backbone.embed(image))
            for (i in fromKotlin.indices) maxDifference = maxOf(maxDifference, abs(fromFile[i] - fromKotlin[i]))
            if (fromFile.indexOfMaxValue() == fromKotlin.indexOfMaxValue()) agree++
        }
        merged.close()
        backbone.close()
        report("    exported .tflite (${exported.size / 1024} KB): same answer on $agree/${images.size}, largest probability difference $maxDifference")
    }

    private fun FloatArray.indexOfMaxValue() = indices.maxBy { this[it] }

    private fun List<Double>.median() = sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
    private fun Double.format() = ((this * 10).roundToInt() / 10.0).toString()
    private fun percent(n: Int, of: Int) = if (of == 0) "-" else "${(100.0 * n / of).roundToInt()} %"
}

/** Runs the spike and shows its report (debug builds only, so the text isn't translated). */
@Composable
internal fun TrainingBenchmarkDialog(benchmark: TrainingBenchmark, sources: suspend () -> BenchmarkSources, onDismiss: () -> Unit) {
    var lines by remember { mutableStateOf(listOf("Starting…")) }
    LaunchedEffect(Unit) {
        val log = Logger.withTag("TrainingBenchmark")
        try {
            benchmark.run(sources()) { line ->
                log.i { line }
                lines = lines + line
            }
        } catch (e: Exception) {
            log.e(e) { "Benchmark failed" }
            lines = lines + "Failed: ${e.message}"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training benchmark") },
        text = {
            SelectionContainer {
                Text(
                    lines.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
