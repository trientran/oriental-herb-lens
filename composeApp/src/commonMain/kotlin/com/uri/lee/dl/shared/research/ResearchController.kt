package com.uri.lee.dl.shared.research

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.training.EmbeddedDataset
import com.uri.lee.dl.core.training.Example
import com.uri.lee.dl.core.training.ResearchArchive
import com.uri.lee.dl.core.training.ResearchPlan
import com.uri.lee.dl.core.training.ResearchProgress
import com.uri.lee.dl.core.training.ResearchResults
import com.uri.lee.dl.core.training.ResearchSession
import com.uri.lee.dl.core.training.RunContext
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import com.uri.lee.dl.domain.ml.PhotoReader
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** Where a research run is up to, for the screen. */
data class ResearchState(
    val dataset: Dataset? = null,
    /** Datasets already in the app's folder, to choose without a picker. */
    val appDatasets: List<Dataset> = emptyList(),
    val backbones: List<String> = emptyList(),
    val running: Boolean = false,
    /** What's happening now, e.g. "Embedding with mobilenet_v3_large: 1,204 of 7,000". */
    val status: String = "",
    /** 0–1 of the current phase, or null when there's nothing to measure. */
    val progress: Float? = null,
    val log: List<String> = emptyList(),
    val results: ResearchResults? = null,
    val error: String? = null,
)

/**
 * Runs the study on this device (plan Phase 7, research mode): embeds the chosen dataset with each
 * backbone, runs every scenario × strategy × seed, then builds the archive for Zenodo. Everything
 * the paper needs is measured by the app and written to CSV; nothing is entered by hand.
 */
class ResearchController(
    private val platform: ResearchPlatform,
    private val reader: PhotoReader,
    private val embedders: ImageEmbedderLoader,
    private val appVersion: String,
) {
    private val _state = MutableStateFlow(ResearchState())
    val state: StateFlow<ResearchState> = _state.asStateFlow()

    val canPickZip: Boolean get() = platform.pickDatasetZip != null

    private var embeddingRows = mutableListOf<String>()
    private var backboneBytes = mapOf<String, ByteArray>()

    suspend fun loadBackbones() {
        val names = runCatching { platform.backbones().keys.toList() }.getOrElse { emptyList() }
        val datasets = runCatching { platform.appDatasets() }.getOrElse { emptyList() }.filter { it.classes.size >= 2 }
        _state.update { it.copy(backbones = names, appDatasets = datasets) }
    }

    fun choose(dataset: Dataset) = _state.update { it.copy(dataset = dataset, results = null, log = emptyList(), error = null) }

    suspend fun pick(zip: Boolean) {
        val picker = if (zip) platform.pickDatasetZip ?: return else platform.pickDatasetFolder
        _state.update { it.copy(status = "Reading the dataset…", error = null) }
        val dataset = runCatching { picker() }.getOrElse { e -> _state.update { it.copy(status = "", error = e.message ?: "Couldn't read the dataset") }; return }
        _state.update {
            when {
                dataset == null -> it.copy(status = "")
                dataset.classes.size < 2 -> it.copy(status = "", error = "Found ${dataset.images.size} photos in ${dataset.classes.size} class folders: a dataset needs at least two.")
                else -> it.copy(dataset = dataset, status = "", results = null, log = emptyList())
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun run(plan: ResearchPlan, backbones: List<String>) {
        val dataset = state.value.dataset ?: return
        val minSteps = plan.scenarios.maxOfOrNull { it.steps } ?: 1
        if (dataset.classes.size < minSteps) {
            _state.update { it.copy(error = "Class-incremental runs need at least $minSteps classes; this dataset has ${dataset.classes.size}.") }
            return
        }
        platform.keepAwake(true)
        _state.update { it.copy(running = true, error = null, results = null, log = emptyList()) }
        try {
            log("Device: ${platform.device}; app $appVersion")
            log("Dataset ${dataset.name}: ${dataset.images.size} photos, ${dataset.classes.size} classes")
            log("Plan: ${plan.describe()}")
            val locations = platform.backbones().filterKeys { it in backbones }
            backboneBytes = locations.mapValues { (_, location) -> platform.readModel(location) }
            embeddingRows = mutableListOf("device,platform,app_version,backbone,photos,unreadable,load_ms,median_ms,mean_ms,p90_ms,memory_after_bytes")
            val embedded = locations.map { (name, location) -> embed(dataset, name, location) }

            val session = ResearchSession(embedded, plan, { RunContext(platform.device, platform.platform, it, dataset.classes, appVersion) }, platform.monitor)
            val started = TimeSource.Monotonic.markNow()
            while (session.hasNext()) {
                val progress = withContext(Dispatchers.Default) { session.next() }
                val done = session.jobsDone
                val elapsed = started.elapsedNow().inWholeSeconds
                val remaining = if (done == 0) null else elapsed * (session.totalJobs - done) / done
                _state.update {
                    it.copy(
                        status = "Run $done of ${session.totalJobs}" + (remaining?.let { r -> "; about ${duration(r)} left" } ?: ""),
                        progress = done.toFloat() / session.totalJobs,
                    )
                }
                when (progress) {
                    is ResearchProgress.Run -> if (progress.number % plan.seeds.size == 0) {
                        val r = progress.result
                        log("${progress.backbone} ${r.scenario.id} ${r.strategy.id}: seed ${r.seed} final accuracy ${percent(r.steps.last().evaluation.accuracy)}")
                    }
                    is ResearchProgress.FinalModel -> log("${progress.backbone}: trained the model on every photo")
                }
                yield() // lets the screen update and Cancel through
            }
            val results = session.results()
            log("Finished in ${duration(results.elapsedMillis / 1000)}")
            _state.update { it.copy(results = results, status = "Done", progress = 1f) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(status = "Stopped", progress = null) }
                throw e
            }
            _state.update { it.copy(status = "", progress = null, error = e.message ?: e.toString()) }
        } finally {
            platform.keepAwake(false)
            _state.update { it.copy(running = false) }
        }
    }

    private suspend fun embed(dataset: Dataset, backbone: String, location: String): EmbeddedDataset {
        val loadStart = TimeSource.Monotonic.markNow()
        val embedder = embedders.load(location)
        val loadMs = loadStart.elapsedNow().inWholeMilliseconds
        val times = mutableListOf<Double>()
        val examples = mutableListOf<Example>()
        var unreadable = 0
        try {
            dataset.images.forEachIndexed { i, photo ->
                if (i % 25 == 0) _state.update { it.copy(status = "Embedding with $backbone: $i of ${dataset.images.size}", progress = i.toFloat() / dataset.images.size) }
                val image = reader.read(photo.image)?.image
                if (image == null) {
                    unreadable++
                } else {
                    val mark = TimeSource.Monotonic.markNow()
                    val embedding = embedder.embed(image)
                    // The first is a warm-up (the runtime compiles and allocates), not counted
                    if (i > 0) times += mark.elapsedNow().inWholeMicroseconds / 1000.0
                    examples += Example(embedding, dataset.classes.indexOf(photo.className))
                }
            }
        } finally {
            embedder.close()
        }
        val sorted = times.sorted()
        fun quantile(q: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * q).roundToInt()]
        val memory = platform.monitor.sample().memoryBytes
        embeddingRows += listOf(
            platform.device, platform.platform, appVersion, backbone, examples.size, unreadable, loadMs,
            quantile(0.5), times.average(), quantile(0.9), memory,
        ).joinToString(",") { v -> v?.toString()?.let { if (',' in it || '"' in it) "\"" + it.replace("\"", "\"\"") + "\"" else it } ?: "" }
        log("$backbone: embedded ${examples.size} photos, ${ms(quantile(0.5))} ms median" + if (unreadable > 0) "; $unreadable unreadable, skipped" else "")
        return EmbeddedDataset(backbone, dataset.classes, examples)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun save() {
        val results = state.value.results ?: return
        val dataset = state.value.dataset ?: return
        val stamp = Clock.System.now().toString().take(19).replace(":", "").replace("-", "")
        val archive = withContext(Dispatchers.Default) {
            val withEmbedding = ResearchResults(results.csv + ("embedding.csv" to embeddingRows.joinToString("\r\n", postfix = "\r\n")), results.models, results.elapsedMillis)
            ResearchArchive.build(withEmbedding, backboneBytes, dataset.classes, readme(dataset, results))
        }
        platform.saveArchive("herblens-research-${platform.platform}-$stamp.zip", archive)
    }

    private fun readme(dataset: Dataset, results: ResearchResults) = buildString {
        appendLine("Herb Lens: on-device continual learning results")
        appendLine()
        appendLine("Device: ${platform.device} (${platform.platform}); app $appVersion")
        appendLine("Dataset: ${dataset.name}, ${dataset.images.size} photos")
        dataset.classes.forEach { c -> appendLine("  $c: ${dataset.images.count { it.className == c }}") }
        appendLine("Run time: ${duration(results.elapsedMillis / 1000)}")
        appendLine()
        appendLine("Log:")
        state.value.log.forEach { appendLine("  $it") }
        appendLine()
        appendLine("results/: one row per observation; every row names the device, backbone, scenario, strategy and seed.")
        appendLine("  runs.csv           one row per run: final accuracy, average incremental accuracy, forgetting, backward transfer")
        appendLine("  steps.csv          one row per step: accuracy, top-3, balanced accuracy, macro F1, kappa, log loss, ECE, time, memory, CPU, battery")
        appendLine("  per_class.csv      precision, recall, F1 and support per class and step")
        appendLine("  confusion.csv      confusion matrices in long format (actual, predicted, count)")
        appendLine("  task_accuracy.csv  accuracy on each earlier step's classes after every step")
        appendLine("  epochs.csv         learning curves: losses and validation accuracy per epoch")
        appendLine("  embedding.csv      backbone load time and per-photo embedding time on this device")
        appendLine()
        appendLine("models/<backbone>/model.tflite: a standalone LiteRT image classifier trained on every photo.")
        appendLine("  Input: 224 x 224 RGB, float 0-1. Output: a probability per class in labels.txt order.")
        appendLine("  It carries TFLite metadata and labels.txt inside; labels.txt is also next to it.")
        appendLine("  head.json: the trained layers alone, for continuing training in the app.")
    }

    private fun log(line: String) {
        Logger.withTag("Research").i { line }
        _state.update { it.copy(log = it.log + line) }
    }

    private fun percent(v: Float) = "${(v * 1000).roundToInt() / 10.0} %"
    private fun ms(v: Double) = ((v * 10).roundToInt() / 10.0).toString()
    private fun duration(seconds: Long): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
        else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
    }
}
