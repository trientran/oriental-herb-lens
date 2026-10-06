package com.uri.lee.dl.shared.research

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.training.EmbeddedDataset
import com.uri.lee.dl.core.training.Example
import com.uri.lee.dl.core.training.ModelPack
import com.uri.lee.dl.core.training.ResearchArchive
import com.uri.lee.dl.core.training.ResearchFormats
import com.uri.lee.dl.core.training.ResearchPlan
import com.uri.lee.dl.core.training.ResearchProgress
import com.uri.lee.dl.core.training.ResearchResults
import com.uri.lee.dl.core.training.ResearchSession
import com.uri.lee.dl.core.training.RunContext
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.training.Backbones
import com.uri.lee.dl.domain.training.Dataset
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** A run that was stopped (app closed, system ended it) and can carry on, or a finished one not yet discarded. */
data class PendingRun(val dataset: String, val done: Int, val total: Int, val finished: Boolean)

/** Where a research run is up to, for the screen. */
data class ResearchState(
    val dataset: Dataset? = null,
    /** Datasets already in the app's folder, to choose without a picker. */
    val appDatasets: List<Dataset> = emptyList(),
    val backbones: List<String> = emptyList(),
    /** The last run's progress, kept on the device: resumable, or finished and ready to save. */
    val pending: PendingRun? = null,
    val running: Boolean = false,
    /** What's happening now, e.g. "Embedding with mobilenet_v3_large: 1,204 of 7,000". */
    val status: String = "",
    /** 0–1 of the current phase, or null when there's nothing to measure. */
    val progress: Float? = null,
    val log: List<String> = emptyList(),
    val error: String? = null,
    /** The saved run's results so far, by backbone and scenario, for the charts. */
    val summary: List<ResultGroup> = emptyList(),
)

/**
 * Runs the study on this device (plan Phase 7, research mode): embeds the chosen dataset with each
 * backbone, runs every scenario × strategy × seed, then builds the archive for Zenodo. Everything
 * the paper needs is measured by the app and written to CSV; nothing is entered by hand.
 *
 * Progress is saved after every job (result rows, embeddings, models), so a run stopped by the
 * system, a crash or a flat battery resumes where it stopped. It lives as long as the app, not
 * the screen: leaving the screen doesn't stop a run.
 */
class ResearchController(
    private var platform: ResearchPlatform,
    private val reader: PhotoReader,
    private val embedders: ImageEmbedderLoader,
    private val backbones: Backbones,
    private val appVersion: String,
) {
    private val _state = MutableStateFlow(ResearchState())
    val state: StateFlow<ResearchState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val files get() = platform.files

    val canPickZip: Boolean get() = platform.pickDatasetZip != null
    val backgroundNote: String get() = platform.backgroundNote

    /** The screen was opened again, perhaps by a new activity: use its pickers from now on. */
    fun attach(platform: ResearchPlatform) {
        this.platform = platform
    }

    suspend fun refresh() {
        val datasets = runCatching { platform.appDatasets() }.getOrElse { emptyList() }.filter { it.classes.size >= 2 }
        val pending = runCatching { pending() }.getOrNull()
        _state.update { it.copy(backbones = backbones.available.sorted(), appDatasets = datasets, pending = pending, summary = summary()) }
    }

    private suspend fun pending(): PendingRun? {
        val meta = files.read(META)?.decodeToString()?.let(::parseMeta) ?: return null
        val plan = files.read(PLAN)?.decodeToString()?.let(ResearchFormats::decodePlan) ?: return null
        val total = (plan.runsPerBackbone + 1) * meta.backbones.size
        return PendingRun(meta.dataset, doneKeys().size, total, finished = files.read(FINISHED) != null)
    }

    suspend fun pick(zip: Boolean) {
        val picker = if (zip) platform.pickDatasetZip ?: return else platform.pickDatasetFolder
        _state.update { it.copy(status = "Reading the dataset…", error = null) }
        val dataset = runCatching { picker() }.getOrElse { e -> _state.update { it.copy(status = "", error = e.message ?: "Couldn't read the dataset") }; return }
        _state.update {
            when {
                dataset == null -> it.copy(status = "")
                dataset.classes.size < 2 -> it.copy(status = "", error = "Found ${dataset.images.size} photos in ${dataset.classes.size} class folders: a dataset needs at least two.")
                else -> it.copy(dataset = dataset, status = "", error = null)
            }
        }
    }

    fun choose(dataset: Dataset) = _state.update { it.copy(dataset = dataset, error = null) }

    /** Starts a new run, replacing any saved one. */
    @OptIn(ExperimentalTime::class)
    fun start(plan: ResearchPlan, backbones: List<String>) {
        val dataset = state.value.dataset ?: return
        val minSteps = plan.scenarios.maxOfOrNull { it.steps } ?: 1
        if (dataset.classes.size < minSteps) {
            _state.update { it.copy(error = "Class-incremental runs need at least $minSteps classes; this dataset has ${dataset.classes.size}.") }
            return
        }
        launchRun {
            files.delete(WORK)
            _state.update { it.copy(log = emptyList()) }
            val meta = SessionMeta(dataset.name, backbones, dataset.classes.map { c -> c to dataset.images.count { it.className == c } }, Clock.System.now().toString())
            files.write(META, meta.encode().encodeToByteArray())
            files.write(PLAN, ResearchFormats.encodePlan(plan).encodeToByteArray())
            files.write(HEADERS, headerText().encodeToByteArray())
            log("Device: ${platform.device}; app $appVersion")
            log("Dataset ${dataset.name}: ${dataset.images.size} photos, ${dataset.classes.size} classes")
            log("Plan: ${plan.describe()}")
            execute(meta, plan, dataset)
        }
    }

    /** Carries on with the saved run; the dataset is only needed if a backbone hadn't finished embedding it. */
    fun resume() = launchRun {
        val meta = files.read(META)?.decodeToString()?.let(::parseMeta) ?: error("No saved run")
        val plan = ResearchFormats.decodePlan(files.read(PLAN)?.decodeToString() ?: error("No saved plan"))
        check(files.read(HEADERS)?.decodeToString() == headerText()) {
            "This run was started by an earlier version of the app, which saved different columns. Save its results so far, then start a new run."
        }
        _state.update { it.copy(log = files.read(LOG)?.decodeToString()?.lines()?.filter { l -> l.isNotEmpty() }.orEmpty()) }
        log("Resumed on ${platform.device}")
        execute(meta, plan, state.value.dataset?.takeIf { it.classes == meta.classes.map { c -> c.first } })
    }

    fun stop() {
        job?.cancel()
    }

    /** Deletes the saved run. */
    fun discard() {
        scope.launch {
            files.delete(WORK)
            _state.update { it.copy(pending = null, log = emptyList(), status = "", progress = null, summary = emptyList()) }
        }
    }

    private fun launchRun(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            platform.keepAwake(true)
            platform.background.start("Herb Lens research run") { job?.cancel() }
            _state.update { it.copy(running = true, error = null, status = "Starting…", progress = null) }
            try {
                block()
            } catch (e: CancellationException) {
                _state.update { it.copy(status = "Stopped. Progress is saved: you can resume.", progress = null) }
                throw e
            } catch (e: Exception) {
                Logger.withTag("Research").e(e) { "Run failed" }
                _state.update { it.copy(status = "", progress = null, error = e.message ?: e.toString()) }
            } finally {
                platform.keepAwake(false)
                platform.background.stop()
                val pending = runCatching { pending() }.getOrNull()
                val summary = runCatching { summary() }.getOrDefault(emptyList())
                _state.update { it.copy(running = false, pending = pending, summary = summary) }
            }
        }
    }

    private suspend fun execute(meta: SessionMeta, plan: ResearchPlan, dataset: Dataset?) {
        val classes = meta.classes.map { it.first }
        val embedded = meta.backbones.map { backbone -> loadOrEmbed(backbone, classes, dataset) }
        val context = { backbone: String -> RunContext(platform.device, platform.platform, backbone, classes, appVersion) }
        val session = ResearchSession(embedded, plan, context, platform.monitor, doneKeys())
        val sizes = restoreResults()
        val started = TimeSource.Monotonic.markNow()
        val startedAt = session.jobsDone
        while (session.hasNext()) {
            val progress = withContext(Dispatchers.Default) { session.next() }
            when (progress) {
                is ResearchProgress.Run -> {
                    progress.rows.forEach { (table, rows) ->
                        files.append("$RESULTS/$table", rows)
                        sizes[table] = (sizes[table] ?: 0L) + rows.encodeToByteArray().size
                    }
                    if (progress.number % plan.seeds.size == 0) {
                        val r = progress.result
                        log("${progress.backbone} ${r.scenario.id} ${r.strategy.id}: seed ${r.seed} final accuracy ${percent(r.steps.last().evaluation.accuracy)}")
                    }
                }
                is ResearchProgress.FinalModel -> {
                    files.write("$MODELS/${progress.backbone}.json", ModelPack(progress.backbone, classes, progress.head).toJson().encodeToByteArray())
                    log("${progress.backbone}: trained the model on every photo")
                }
            }
            // Written last: a job only counts as done once everything it produced is saved. With it go
            // the results files' lengths, so a resume can cut off rows of a job that didn't finish
            files.append(DONE, progress.key + "\t" + sizes.entries.joinToString(",") { "${it.key}=${it.value}" } + "\n")
            val done = session.jobsDone
            val thisSession = done - startedAt
            val elapsed = started.elapsedNow().inWholeSeconds
            val remaining = if (thisSession == 0) null else elapsed * (session.totalJobs - done) / thisSession
            val status = "Run $done of ${session.totalJobs}" + (remaining?.let { "; about ${duration(it)} left" } ?: "")
            _state.update { it.copy(status = status, progress = done.toFloat() / session.totalJobs) }
            platform.background.progress(done, session.totalJobs, status)
            if (done % SUMMARY_EVERY == 0) _state.update { it.copy(summary = summary()) }
            yield() // lets the screen update and Stop through
        }
        files.write(FINISHED, byteArrayOf())
        log("Finished; this session took ${duration(started.elapsedNow().inWholeSeconds)}")
        _state.update { it.copy(status = "Done", progress = 1f) }
    }

    private suspend fun loadOrEmbed(backbone: String, classes: List<String>, dataset: Dataset?): EmbeddedDataset {
        files.read("$EMBEDDINGS/$backbone.bin")?.let { return EmbeddedDataset(backbone, classes, ResearchFormats.decodeEmbeddings(it)) }
        checkNotNull(dataset) { "$backbone hadn't finished embedding the photos: choose the same dataset again, then Resume." }
        val location = backbones.location(backbone) {
            _state.update { it.copy(status = "Downloading $backbone…", progress = null) }
        }
        val loadStart = TimeSource.Monotonic.markNow()
        val embedder = embedders.load(location)
        val loadMs = loadStart.elapsedNow().inWholeMilliseconds
        val times = mutableListOf<Double>()
        val examples = mutableListOf<Example>()
        var unreadable = 0
        var firstMs: Double? = null
        try {
            dataset.images.forEachIndexed { i, photo ->
                if (i % 25 == 0) {
                    val status = "Embedding with $backbone: $i of ${dataset.images.size}"
                    _state.update { it.copy(status = status, progress = i.toFloat() / dataset.images.size) }
                    platform.background.progress(i, dataset.images.size, status)
                }
                val image = reader.read(photo.image)?.image
                if (image == null) {
                    unreadable++
                } else {
                    val mark = TimeSource.Monotonic.markNow()
                    val embedding = embedder.embed(image)
                    // The first is a warm-up (some runtimes load the model only now), reported on its own
                    val ms = mark.elapsedNow().inWholeMicroseconds / 1000.0
                    if (firstMs == null) firstMs = ms else times += ms
                    examples += Example(embedding, classes.indexOf(photo.className))
                }
            }
        } finally {
            embedder.close()
        }
        val sorted = times.sorted()
        fun quantile(q: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * q).roundToInt()]
        val row = listOf(
            platform.device, platform.platform, appVersion, backbone, examples.size, unreadable, loadMs, firstMs,
            quantile(0.5), times.average(), quantile(0.9), platform.monitor.sample().memoryBytes,
        ).joinToString(",") { v -> v?.toString()?.let { if (',' in it || '"' in it) "\"" + it.replace("\"", "\"\"") + "\"" else it } ?: "" }
        // Its row first, then the embeddings: once these exist, the backbone is done
        files.write("$EMBEDDINGS/$backbone.csv", (row + "\r\n").encodeToByteArray())
        files.write("$EMBEDDINGS/$backbone.bin", ResearchFormats.encodeEmbeddings(examples))
        log("$backbone: embedded ${examples.size} photos, ${ms(quantile(0.5))} ms median" + if (unreadable > 0) "; $unreadable unreadable, skipped" else "")
        return EmbeddedDataset(backbone, classes, examples)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun save() {
        val meta = files.read(META)?.decodeToString()?.let(::parseMeta) ?: return
        _state.update { it.copy(status = "Preparing the results…", error = null) }
        val archive = runCatching {
            withContext(Dispatchers.Default) {
                // The headers the run was started with, matching its rows
                val saved = files.read(HEADERS)?.decodeToString()?.split(HEADER_SEPARATOR)?.filter { it.isNotEmpty() }
                    ?.associate { it.substringBefore('\n') to it.substringAfter('\n') }
                val headers = saved ?: ResearchSession.headers()
                val csv = headers.mapValues { (table, header) -> header + (files.read("$RESULTS/$table")?.decodeToString() ?: "") } +
                    ("embedding.csv" to (headers["embedding.csv"] ?: (EMBEDDING_HEADER + "\r\n")) + meta.backbones.map { b -> files.read("$EMBEDDINGS/$b.csv")?.decodeToString().orEmpty() }.joinToString(""))
                val models = meta.backbones.mapNotNull { b -> files.read("$MODELS/$b.json")?.let { b to ModelPack.fromJson(it.decodeToString()).head } }.toMap()
                val backboneFiles = models.keys.associateWith { backbones.read(it) }
                ResearchArchive.build(ResearchResults(csv, models, 0), backboneFiles, meta.classes.map { it.first }, readme(meta))
            }
        }.getOrElse { e -> _state.update { it.copy(status = "", error = e.message ?: e.toString()) }; return }
        val stamp = Clock.System.now().toString().take(19).replace(":", "").replace("-", "")
        platform.saveArchive("herblens-research-${platform.platform}-$stamp.zip", archive)
        _state.update { it.copy(status = "Saved") }
    }

    private suspend fun readme(meta: SessionMeta) = buildString {
        appendLine("Herb Lens: on-device continual learning results")
        appendLine()
        appendLine("Device: ${platform.device} (${platform.platform}); app $appVersion")
        appendLine("Dataset: ${meta.dataset}, ${meta.classes.sumOf { it.second }} photos; run started ${meta.started}")
        meta.classes.forEach { (c, n) -> appendLine("  $c: $n") }
        appendLine()
        appendLine("Log:")
        files.read(LOG)?.decodeToString()?.lines()?.filter { it.isNotEmpty() }?.forEach { appendLine("  $it") }
        appendLine()
        appendLine("results/: one row per observation; every row names the device, backbone, scenario, strategy and seed.")
        appendLine("  runs.csv           one row per run: final accuracy, average incremental accuracy, forgetting, backward transfer")
        appendLine("  steps.csv          one row per step: accuracy, top-3, balanced accuracy, macro F1, kappa, log loss, ECE, time, memory, CPU, battery")
        appendLine("  per_class.csv      precision, recall, F1 and support per class and step")
        appendLine("  confusion.csv      confusion matrices in long format (actual, predicted, count)")
        appendLine("  task_accuracy.csv  accuracy on each earlier step's classes after every step")
        appendLine("  epochs.csv         learning curves: losses and validation accuracy per epoch")
        appendLine("  embedding.csv      backbone load time, first photo (warm-up) and per-photo embedding time on this device")
        appendLine()
        appendLine("models/<backbone>/model.tflite: a standalone LiteRT image classifier trained on every photo.")
        appendLine("  Input: 224 x 224 RGB, float 0-1. Output: a probability per class in labels.txt order.")
        appendLine("  It carries TFLite metadata and labels.txt inside; labels.txt is also next to it.")
        appendLine("  head.json: the trained layers alone, for continuing training in the app.")
    }

    /** Each table's name and header line, to save with a run. */
    private fun headerText(): String =
        (ResearchSession.headers() + ("embedding.csv" to EMBEDDING_HEADER + "\r\n")).entries
            .joinToString(HEADER_SEPARATOR) { (table, header) -> "$table\n$header" }

    /** The saved results so far, summarised for the charts. */
    private suspend fun summary(): List<ResultGroup> {
        val headers = ResearchSession.headers()
        val runs = files.read("$RESULTS/runs.csv")?.decodeToString() ?: return emptyList()
        val steps = files.read("$RESULTS/steps.csv")?.decodeToString().orEmpty()
        return runCatching { ResearchSummary.of(headers.getValue("runs.csv") + runs, headers.getValue("steps.csv") + steps) }.getOrDefault(emptyList())
    }

    private suspend fun doneLines(): List<String> = files.read(DONE)?.decodeToString()?.lines()?.filter { it.isNotBlank() }.orEmpty()

    private suspend fun doneKeys(): Set<String> = doneLines().map { it.substringBefore('\t') }.toSet()

    /**
     * Cuts each results file back to its length when the last job was done (a job stopped part-way
     * may have written some rows), and returns those lengths.
     */
    private suspend fun restoreResults(): MutableMap<String, Long> {
        val lines = doneLines()
        val last = lines.lastOrNull()
        if (last != null && '\t' !in last) {
            // Saved by an earlier version, without lengths: leave the files as they are
            return ResearchSession.headers().keys.associateWith { (files.read("$RESULTS/$it")?.size ?: 0).toLong() }.toMutableMap()
        }
        val recorded = last?.substringAfter('\t')?.split(',')?.filter { '=' in it }
            ?.associate { it.substringBefore('=') to it.substringAfter('=').toLong() }.orEmpty()
        for (table in ResearchSession.headers().keys) {
            val bytes = files.read("$RESULTS/$table") ?: continue
            val keep = recorded[table] ?: 0L
            if (bytes.size > keep) files.write("$RESULTS/$table", bytes.copyOf(keep.toInt()))
        }
        return recorded.toMutableMap()
    }

    private suspend fun log(line: String) {
        Logger.withTag("Research").i { line }
        files.append(LOG, line + "\n")
        _state.update { it.copy(log = it.log + line) }
    }

    private fun percent(v: Float) = "${(v * 1000).roundToInt() / 10.0} %"
    private fun ms(v: Double) = ((v * 10).roundToInt() / 10.0).toString()
    private fun duration(seconds: Long): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
        else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
    }

    /** What a saved run was started with. */
    private class SessionMeta(val dataset: String, val backbones: List<String>, val classes: List<Pair<String, Int>>, val started: String) {
        fun encode() = buildString {
            appendLine("dataset=$dataset")
            appendLine("backbones=${backbones.joinToString(",")}")
            appendLine("started=$started")
            classes.forEach { (name, count) -> appendLine("class=$count\t$name") }
        }
    }

    private fun parseMeta(text: String): SessionMeta? {
        val lines = text.lines()
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
        return SessionMeta(
            dataset = value("dataset") ?: return null,
            backbones = value("backbones")?.split(',')?.filter { it.isNotEmpty() } ?: return null,
            classes = lines.filter { it.startsWith("class=") }.map { it.substringAfter('=') }.map { it.substringAfter('\t') to it.substringBefore('\t').toInt() },
            started = value("started").orEmpty(),
        )
    }

    companion object {
        private const val WORK = "work"
        private const val META = "$WORK/session.txt"
        private const val PLAN = "$WORK/plan.txt"
        private const val DONE = "$WORK/done.txt"
        private const val LOG = "$WORK/log.txt"
        private const val FINISHED = "$WORK/finished"
        private const val RESULTS = "$WORK/results"
        private const val MODELS = "$WORK/models"
        private const val EMBEDDINGS = "$WORK/embeddings"
        private const val HEADERS = "$WORK/headers.txt"
        private const val SUMMARY_EVERY = 10
        private const val HEADER_SEPARATOR = "\u0000"
        private const val EMBEDDING_HEADER = "device,platform,app_version,backbone,photos,unreadable,load_ms,first_ms,median_ms,mean_ms,p90_ms,memory_after_bytes"

        private var shared: ResearchController? = null

        /** The app's one controller, so a run outlives the screen (and, on Android, the activity). */
        fun shared(platform: ResearchPlatform, reader: PhotoReader, embedders: ImageEmbedderLoader, backbones: Backbones, appVersion: String): ResearchController =
            shared?.also { it.attach(platform) } ?: ResearchController(platform, reader, embedders, backbones, appVersion).also { shared = it }
    }
}
