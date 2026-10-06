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
    /** Run [number] of [total] for [backbone]. */
    data class Run(val backbone: String, val number: Int, val total: Int, val result: RunResult) : ResearchProgress
    data class FinalModel(val backbone: String) : ResearchProgress
}

/** The results of a research run: CSV tables, and a model per backbone trained on all the data. */
class ResearchResults(val csv: Map<String, String>, val models: Map<String, ClassifierHead>, val elapsedMillis: Long)

/**
 * Runs a [ResearchPlan] over datasets embedded by one or more backbones: the experiment grid of
 * the study. One job at a time ([next]), so a caller can yield, show progress or stop in between.
 */
class ResearchSession(
    private val datasets: List<EmbeddedDataset>,
    private val plan: ResearchPlan,
    context: (backbone: String) -> RunContext,
    private val monitor: ResourceMonitor = ResourceMonitor { ResourceSample() },
) {
    private sealed interface Job {
        val dataset: EmbeddedDataset
        class Run(override val dataset: EmbeddedDataset, val scenario: Scenario, val strategy: Strategy, val seed: Int, val index: Int) : Job
        class FinalModel(override val dataset: EmbeddedDataset) : Job
    }

    private val jobs: List<Job> = datasets.flatMap { dataset ->
        var index = 0
        val runs = plan.scenarios.flatMap { scenario ->
            plan.strategies.flatMap { strategy -> plan.seeds.map { seed -> Job.Run(dataset, scenario, strategy, seed, ++index) } }
        }
        runs + Job.FinalModel(dataset)
    }
    private val tables = datasets.associate { it.backbone to StudyCsv(context(it.backbone)) }
    private val models = mutableMapOf<String, ClassifierHead>()
    private val mark = TimeSource.Monotonic.markNow()
    private var position = 0

    val totalJobs: Int get() = jobs.size
    val jobsDone: Int get() = position
    fun hasNext(): Boolean = position < jobs.size

    /** Does the next job and says what it was. */
    fun next(): ResearchProgress {
        val job = jobs[position++]
        val dataset = job.dataset
        return when (job) {
            is Job.Run -> {
                val result = ContinualRunner.run(dataset.examples, dataset.labels.size, job.scenario, job.strategy, job.seed, plan.options, monitor)
                tables.getValue(dataset.backbone).add(result)
                ResearchProgress.Run(dataset.backbone, job.index, plan.runsPerBackbone, result)
            }
            is Job.FinalModel -> {
                // The model to publish: every photo, the usual validation split for early stopping
                models[dataset.backbone] = HeadTrainer.train(dataset.examples, dataset.labels.size, plan.options.copy(seed = plan.seeds.first())).head
                ResearchProgress.FinalModel(dataset.backbone)
            }
        }
    }

    /** What's been done so far (everything, once [hasNext] is false). */
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

        /** Stacks same-named CSV files, keeping the first header. */
        internal fun mergeTables(files: List<Map<String, String>>): Map<String, String> {
            if (files.isEmpty()) return emptyMap()
            return files.first().keys.associateWith { name ->
                files.mapIndexed { i, f -> f.getValue(name).let { if (i == 0) it else it.substringAfter("\r\n") } }.joinToString("")
            }
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
