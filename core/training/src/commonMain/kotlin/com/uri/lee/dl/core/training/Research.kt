package com.uri.lee.dl.core.training

import kotlin.time.TimeSource

/** What a research run covers: every combination is run once per seed. */
data class ResearchPlan(
    val scenarios: List<Scenario> = listOf(Scenario(ScenarioKind.ClassIncremental), Scenario(ScenarioKind.DataIncremental)),
    val strategies: List<Strategy> = listOf(
        Strategy.Joint, Strategy.Naive,
        Strategy.Replay(5), Strategy.Replay(10), Strategy.Replay(20), Strategy.Replay(50),
        Strategy.Prototypes,
    ),
    val seeds: List<Int> = (1..10).toList(),
    val options: TrainingOptions = TrainingOptions(classBalanced = true),
) {
    val runsPerBackbone: Int get() = scenarios.size * strategies.size * seeds.size

    fun describe(): String = buildString {
        append("scenarios=").append(scenarios.joinToString(";") { it.id })
        append(", strategies=").append(strategies.joinToString(";") { it.id })
        append(", seeds=").append(seeds.joinToString(";"))
        append(", options=").append(options)
    }
}

/** A dataset's photos, already turned into embeddings by one backbone. */
class EmbeddedDataset(val backbone: String, val labels: List<String>, val examples: List<Example>)

sealed interface ResearchProgress {
    /** Identifies the job, so a resumed session can skip it. */
    val key: String
    val backbone: String

    /**
     * Run [number] of [total] for [backbone]. [rows] are its rows of each CSV table (no header),
     * for appending to files as the session goes.
     */
    data class Run(
        override val key: String,
        override val backbone: String,
        val number: Int,
        val total: Int,
        val result: RunResult,
        val rows: Map<String, String>,
    ) : ResearchProgress

    /** The model trained on every photo, to publish. */
    data class FinalModel(override val key: String, override val backbone: String, val head: ClassifierHead) : ResearchProgress
}

/** The results of a research run: CSV tables, and a model per backbone trained on all the data. */
class ResearchResults(val csv: Map<String, String>, val models: Map<String, ClassifierHead>, val elapsedMillis: Long)

/**
 * Runs a [ResearchPlan] over datasets embedded by one or more backbones: the experiment grid of
 * the study. One job at a time ([next]), so a caller can yield, show progress, save what's done
 * or stop in between; jobs whose keys are in [done] are skipped (resuming a stopped session).
 */
class ResearchSession(
    private val datasets: List<EmbeddedDataset>,
    private val plan: ResearchPlan,
    private val context: (backbone: String) -> RunContext,
    private val monitor: ResourceMonitor = ResourceMonitor { ResourceSample() },
    done: Set<String> = emptySet(),
) {
    private sealed interface Job {
        val key: String
        val dataset: EmbeddedDataset
        class Run(override val dataset: EmbeddedDataset, val scenario: Scenario, val strategy: Strategy, val seed: Int, val index: Int) : Job {
            override val key = "${dataset.backbone}|${scenario.id}|${strategy.id}|$seed"
        }
        class FinalModel(override val dataset: EmbeddedDataset) : Job {
            override val key = "${dataset.backbone}|final"
        }
    }

    private val allJobs: List<Job> = datasets.flatMap { dataset ->
        var index = 0
        val runs = plan.scenarios.flatMap { scenario ->
            plan.strategies.flatMap { strategy -> plan.seeds.map { seed -> Job.Run(dataset, scenario, strategy, seed, ++index) } }
        }
        runs + Job.FinalModel(dataset)
    }
    private val jobs = allJobs.filter { it.key !in done }
    private val tables = datasets.associate { it.backbone to StudyCsv(context(it.backbone)) }
    private val models = mutableMapOf<String, ClassifierHead>()
    private val mark = TimeSource.Monotonic.markNow()
    private var position = 0

    /** Every job in the plan, including any done before a resume. */
    val totalJobs: Int get() = allJobs.size
    val jobsDone: Int get() = allJobs.size - jobs.size + position
    fun hasNext(): Boolean = position < jobs.size

    /** Does the next job and says what it was. */
    fun next(): ResearchProgress {
        val job = jobs[position++]
        val dataset = job.dataset
        return when (job) {
            is Job.Run -> {
                val result = ContinualRunner.run(dataset.examples, dataset.labels.size, job.scenario, job.strategy, job.seed, plan.options, monitor)
                tables.getValue(dataset.backbone).add(result)
                val rows = StudyCsv(context(dataset.backbone)).apply { add(result) }.files().mapValues { it.value.substringAfter("\r\n") }
                ResearchProgress.Run(job.key, dataset.backbone, job.index, plan.runsPerBackbone, result, rows)
            }
            is Job.FinalModel -> {
                // The model to publish: every photo, the usual validation split for early stopping
                val head = HeadTrainer.train(dataset.examples, dataset.labels.size, plan.options.copy(seed = plan.seeds.first())).head
                models[dataset.backbone] = head
                ResearchProgress.FinalModel(job.key, dataset.backbone, head)
            }
        }
    }

    /** What this session has done (everything, once [hasNext] is false, unless it was resumed). */
    fun results(): ResearchResults =
        ResearchResults(mergeTables(datasets.map { tables.getValue(it.backbone).files() }), models.toMap(), mark.elapsedNow().inWholeMilliseconds)

    companion object {
        /** Runs every job in one go. */
        fun runAll(
            datasets: List<EmbeddedDataset>,
            plan: ResearchPlan,
            context: (backbone: String) -> RunContext,
            monitor: ResourceMonitor = ResourceMonitor { ResourceSample() },
            onProgress: (ResearchProgress) -> Unit = {},
        ): ResearchResults {
            val session = ResearchSession(datasets, plan, context, monitor)
            while (session.hasNext()) onProgress(session.next())
            return session.results()
        }

        /** Each CSV table's header line (with its line break), by file name. */
        fun headers(): Map<String, String> = StudyCsv(RunContext("", "", "", emptyList())).files()

        /** Stacks same-named CSV files, keeping the first header. */
        internal fun mergeTables(files: List<Map<String, String>>): Map<String, String> {
            if (files.isEmpty()) return emptyMap()
            return files.first().keys.associateWith { name ->
                files.mapIndexed { i, f -> f.getValue(name).let { if (i == 0) it else it.substringAfter("\r\n") } }.joinToString("")
            }
        }
    }
}

/** Saving and restoring a session's settings and embeddings, to resume after the app is stopped. */
object ResearchFormats {

    /** The plan as "key=value" lines. */
    fun encodePlan(plan: ResearchPlan): String = buildString {
        appendLine("scenarios=" + plan.scenarios.joinToString(",") { it.id })
        appendLine("strategies=" + plan.strategies.joinToString(",") { it.id })
        appendLine("seeds=" + plan.seeds.joinToString(","))
        val o = plan.options
        appendLine("learningRate=${o.learningRate}")
        appendLine("l2=${o.l2}")
        appendLine("batchSize=${o.batchSize}")
        appendLine("maxEpochs=${o.maxEpochs}")
        appendLine("patience=${o.patience}")
        appendLine("validationFraction=${o.validationFraction}")
        appendLine("hiddenUnits=${o.hiddenUnits}")
        appendLine("classBalanced=${o.classBalanced}")
    }

    fun decodePlan(text: String): ResearchPlan {
        val values = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        fun list(key: String) = values[key].orEmpty().split(',').filter { it.isNotEmpty() }
        val defaults = TrainingOptions()
        return ResearchPlan(
            scenarios = list("scenarios").map(::scenario),
            strategies = list("strategies").map(::strategy),
            seeds = list("seeds").map { it.toInt() },
            options = TrainingOptions(
                learningRate = values["learningRate"]?.toFloat() ?: defaults.learningRate,
                l2 = values["l2"]?.toFloat() ?: defaults.l2,
                batchSize = values["batchSize"]?.toInt() ?: defaults.batchSize,
                maxEpochs = values["maxEpochs"]?.toInt() ?: defaults.maxEpochs,
                patience = values["patience"]?.toInt() ?: defaults.patience,
                validationFraction = values["validationFraction"]?.toFloat() ?: defaults.validationFraction,
                hiddenUnits = values["hiddenUnits"]?.toInt() ?: defaults.hiddenUnits,
                classBalanced = values["classBalanced"]?.toBoolean() ?: true,
            ),
        )
    }

    private fun scenario(id: String): Scenario {
        val kind = ScenarioKind.entries.first { id.startsWith(it.id + "-") }
        return Scenario(kind, id.removePrefix(kind.id + "-").toInt())
    }

    private fun strategy(id: String): Strategy = when {
        id == Strategy.Joint.id -> Strategy.Joint
        id == Strategy.Naive.id -> Strategy.Naive
        id == Strategy.Prototypes.id -> Strategy.Prototypes
        id.startsWith("replay-") -> Strategy.Replay(id.removePrefix("replay-").toInt())
        else -> error("Unknown strategy $id")
    }

    /** Embeddings as bytes: "HLE1", count, dimensions, then per example its label and values (little-endian). */
    fun encodeEmbeddings(examples: List<Example>): ByteArray {
        val dimensions = examples.firstOrNull()?.embedding?.size ?: 0
        val out = ByteArray(12 + examples.size * (4 + 4 * dimensions))
        var at = 0
        fun int(v: Int) { for (i in 0 until 4) out[at++] = (v shr (8 * i)).toByte() }
        "HLE1".encodeToByteArray().copyInto(out); at = 4
        int(examples.size); int(dimensions)
        for (e in examples) {
            int(e.label)
            for (v in e.embedding) int(v.toRawBits())
        }
        return out
    }

    fun decodeEmbeddings(bytes: ByteArray): List<Example> {
        require(bytes.size >= 12 && bytes.decodeToString(0, 4) == "HLE1") { "Not an embeddings file" }
        val r = FlatBufferReader(bytes)
        val count = r.int(4)
        val dimensions = r.int(8)
        var at = 12
        return List(count) {
            val label = r.int(at); at += 4
            val embedding = FloatArray(dimensions) { Float.fromBits(r.int(at + 4 * it)) }
            at += 4 * dimensions
            Example(embedding, label)
        }
    }
}

/**
 * Everything a research run produced, in one zip for Zenodo: the CSV tables, and per backbone a
 * standalone .tflite with its labels.txt, plus a README describing the run.
 */
object ResearchArchive {

    fun build(
        results: ResearchResults,
        backbones: Map<String, ByteArray>,
        labels: List<String>,
        readme: String,
        modelName: String = "Herb Lens user-trained classifier",
    ): ByteArray {
        val files = linkedMapOf<String, ByteArray>()
        files["README.txt"] = readme.encodeToByteArray()
        for ((name, csv) in results.csv) files["results/$name"] = csv.encodeToByteArray()
        for ((backbone, head) in results.models) {
            val backboneBytes = backbones[backbone] ?: continue
            files["models/$backbone/model.tflite"] = TfliteExport.export(backboneBytes, head, labels, "$modelName ($backbone)")
            files["models/$backbone/labels.txt"] = labels.joinToString("\n", postfix = "\n").encodeToByteArray()
            files["models/$backbone/head.json"] = ModelPack(backbone, labels, head).toJson().encodeToByteArray()
        }
        return StoredZip.append(ByteArray(0), files)
    }
}
