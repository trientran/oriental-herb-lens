package com.uri.lee.dl.core.training

import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.TimeSource

/** A dataset split into training, validation (early stopping) and test examples. */
class DataSplit(val train: List<Example>, val validation: List<Example>, val test: List<Example>) {
    companion object {
        /** A random split, stratified by class: each class is split in the same proportions. */
        fun stratified(examples: List<Example>, random: Random, trainShare: Float = 0.7f, validationShare: Float = 0.15f): DataSplit {
            val train = mutableListOf<Example>()
            val validation = mutableListOf<Example>()
            val test = mutableListOf<Example>()
            examples.groupBy { it.label }.entries.sortedBy { it.key }.forEach { (_, group) ->
                val shuffled = group.shuffled(random)
                val nTrain = (group.size * trainShare).roundToInt().coerceAtLeast(1)
                val nValidation = (group.size * validationShare).roundToInt().coerceAtMost(group.size - nTrain)
                train += shuffled.take(nTrain)
                validation += shuffled.subList(nTrain, nTrain + nValidation)
                test += shuffled.drop(nTrain + nValidation)
            }
            return DataSplit(train, validation, test)
        }
    }
}

enum class ScenarioKind(val id: String) {
    /** New species arrive in groups; the model must keep the earlier ones. */
    ClassIncremental("class-incremental"),

    /** All species from the start; more photos of each arrive at every step. */
    DataIncremental("data-incremental"),
}

data class Scenario(val kind: ScenarioKind, val steps: Int = 5) {
    val id: String get() = "${kind.id}-$steps"
}

/** How each step's model is trained from what came before. */
sealed class Strategy(val id: String) {
    /** Retrains from scratch on everything so far: the upper bound, at the cost of keeping all data. */
    data object Joint : Strategy("joint")

    /** Continues from the last model on the new data only: the lower bound (catastrophic forgetting). */
    data object Naive : Strategy("naive")

    /** Continues from the last model on the new data plus a stored sample of [perClass] embeddings per class. */
    data class Replay(val perClass: Int) : Strategy("replay-$perClass")

    /** Nearest class mean: no training, no forgetting (see [PrototypeClassifier]). */
    data object Prototypes : Strategy("prototypes")
}

/** One step of a scenario: the new data, with labels in the order classes arrive. */
class ScenarioStep(
    val index: Int,
    /** Classes known after this step (labels 0 until this). */
    val classesSeen: Int,
    val train: List<Example>,
    val validation: List<Example>,
    /** This step's own test examples: its new classes (class-incremental) or every class (data-incremental). */
    val test: List<Example>,
)

class ScenarioPlan(
    val scenario: Scenario,
    /** classOrder[label] is the dataset's class index of the label used in the plan. */
    val classOrder: List<Int>,
    val steps: List<ScenarioStep>,
) {
    companion object {
        /** Splits [split] into the scenario's steps; class order and chunks are random per [random]. */
        fun of(split: DataSplit, classes: Int, scenario: Scenario, random: Random): ScenarioPlan {
            val n = scenario.steps
            require(n >= 1) { "Needs a step" }
            return when (scenario.kind) {
                ScenarioKind.ClassIncremental -> {
                    require(classes >= n) { "Fewer classes than steps" }
                    val order = (0 until classes).shuffled(random)
                    val position = IntArray(classes).also { p -> order.forEachIndexed { i, c -> p[c] = i } }
                    fun relabel(examples: List<Example>) = examples.map { Example(it.embedding, position[it.label]) }
                    val bounds = (0..n).map { it * classes / n }
                    val steps = (0 until n).map { s ->
                        val range = bounds[s] until bounds[s + 1]
                        ScenarioStep(
                            index = s,
                            classesSeen = bounds[s + 1],
                            train = relabel(split.train).filter { it.label in range },
                            validation = relabel(split.validation).filter { it.label in range },
                            test = relabel(split.test).filter { it.label in range },
                        )
                    }
                    ScenarioPlan(scenario, order, steps)
                }
                ScenarioKind.DataIncremental -> {
                    fun chunks(examples: List<Example>): List<List<Example>> {
                        val result = List(n) { mutableListOf<Example>() }
                        examples.groupBy { it.label }.entries.sortedBy { it.key }.forEach { (_, group) ->
                            val shuffled = group.shuffled(random)
                            for (s in 0 until n) result[s] += shuffled.subList(s * shuffled.size / n, (s + 1) * shuffled.size / n)
                        }
                        return result
                    }
                    val train = chunks(split.train)
                    val validation = chunks(split.validation)
                    val steps = (0 until n).map { s -> ScenarioStep(s, classes, train[s], validation[s], split.test) }
                    ScenarioPlan(scenario, (0 until classes).toList(), steps)
                }
            }
        }
    }
}

/** What a device reports about its resources; anything it can't measure is null. */
data class ResourceSample(
    /** This app's memory in use (Android PSS, iOS footprint, the browser's JS heap). */
    val memoryBytes: Long? = null,
    /** CPU time this process has used. */
    val cpuMillis: Long? = null,
    /** Battery charge left (Android's charge counter), to estimate energy used. */
    val chargeMicroAmpHours: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    /** The platform's thermal state, e.g. "none", "light", "severe". */
    val thermal: String? = null,
)

fun interface ResourceMonitor {
    fun sample(): ResourceSample
}

class StepResult(
    val step: Int,
    val classesSeen: Int,
    /** Examples trained on at this step (new data plus anything replayed). */
    val trainExamples: Int,
    /** Embeddings (or class sums) kept for later steps: the strategy's memory cost. */
    val storedVectors: Int,
    /** Null for prototypes, which don't train. */
    val training: TrainingResult?,
    val trainMillis: Long,
    val before: ResourceSample,
    val after: ResourceSample,
    /** On the test examples of every class seen so far. */
    val evaluation: Evaluation,
    /** Accuracy on each step's own test examples so far (class-incremental: one task per step). */
    val taskAccuracies: List<Float>,
)

class RunResult(
    val scenario: Scenario,
    val strategy: Strategy,
    val seed: Int,
    val options: TrainingOptions,
    val classOrder: List<Int>,
    val steps: List<StepResult>,
    /** The final model. */
    val classifier: EmbeddingClassifier,
) {
    /** Forgetting and transfer; only meaningful when steps bring new classes. */
    val continual: ContinualMetrics? =
        if (scenario.kind == ScenarioKind.ClassIncremental) ContinualMetrics(steps.map { it.taskAccuracies }) else null

    val averageIncrementalAccuracy: Float get() = steps.map { it.evaluation.accuracy }.average().toFloat()
}

/**
 * Runs one strategy through one scenario: the experiment behind each row of the study. The same
 * [seed] gives the same split, class order and chunks for every strategy and on every device.
 */
object ContinualRunner {

    fun run(
        examples: List<Example>,
        classes: Int,
        scenario: Scenario,
        strategy: Strategy,
        seed: Int,
        options: TrainingOptions = TrainingOptions(classBalanced = true),
        monitor: ResourceMonitor = ResourceMonitor { ResourceSample() },
        onStep: (StepResult) -> Unit = {},
    ): RunResult {
        val random = Random(seed)
        val plan = ScenarioPlan.of(DataSplit.stratified(examples, random), classes, scenario, random)
        return run(plan, strategy, seed, options, monitor, onStep)
    }

    fun run(
        plan: ScenarioPlan,
        strategy: Strategy,
        seed: Int,
        options: TrainingOptions,
        monitor: ResourceMonitor = ResourceMonitor { ResourceSample() },
        onStep: (StepResult) -> Unit = {},
    ): RunResult {
        val dimensions = plan.steps.first().train.first().embedding.size
        val bufferRandom = Random(seed * 7919 + 1)
        val trainBuffer = ReplayBuffer((strategy as? Strategy.Replay)?.perClass ?: 0, bufferRandom)
        val validationBuffer = ReplayBuffer((strategy as? Strategy.Replay)?.perClass?.let { validationShare(it) } ?: 0, bufferRandom)
        val prototypes = PrototypeClassifier(dimensions)
        var head: ClassifierHead? = null
        val results = mutableListOf<StepResult>()

        for (step in plan.steps) {
            val seen = plan.steps.subList(0, step.index + 1)
            val stepOptions = options.copy(seed = seed * 1000 + step.index)
            val (train, validation) = when (strategy) {
                Strategy.Joint -> seen.flatMap { it.train } to seen.flatMap { it.validation }
                Strategy.Naive -> step.train to step.validation
                is Strategy.Replay -> (step.train + trainBuffer.contents()) to (step.validation + validationBuffer.contents())
                Strategy.Prototypes -> step.train to emptyList()
            }

            val before = monitor.sample()
            val mark = TimeSource.Monotonic.markNow()
            var training: TrainingResult? = null
            if (strategy == Strategy.Prototypes) {
                prototypes.learn(train)
            } else {
                val start = if (strategy == Strategy.Joint) null else head?.expanded(step.classesSeen)?.also { grown ->
                    // New classes start from their mean embedding rather than from zero
                    val previous = head!!.classes
                    val known = (0 until previous).toList()
                    for (c in previous until step.classesSeen) grown.imprint(c, step.train.filter { it.label == c }.map { it.embedding }, known)
                }
                training = HeadTrainer.train(train, validation, step.classesSeen, stepOptions, start)
                head = training.head
            }
            val trainMillis = mark.elapsedNow().inWholeMilliseconds
            val after = monitor.sample()

            if (strategy is Strategy.Replay) {
                trainBuffer.add(step.train)
                validationBuffer.add(step.validation)
            }

            val classifier: EmbeddingClassifier = if (strategy == Strategy.Prototypes) prototypes else head!!
            val tasks = if (plan.scenario.kind == ScenarioKind.ClassIncremental) seen.map { it.test } else listOf(step.test)
            val result = StepResult(
                step = step.index,
                classesSeen = step.classesSeen,
                trainExamples = train.size,
                storedVectors = when (strategy) {
                    Strategy.Joint -> seen.sumOf { it.train.size + it.validation.size }
                    Strategy.Naive -> 0
                    is Strategy.Replay -> trainBuffer.size + validationBuffer.size
                    Strategy.Prototypes -> prototypes.classes
                },
                training = training,
                trainMillis = trainMillis,
                before = before,
                after = after,
                evaluation = Evaluation.of(classifier, tasks.flatten(), step.classesSeen),
                taskAccuracies = tasks.map { task -> task.count { classifier.predict(it.embedding) == it.label }.toFloat() / task.size },
            )
            results += result
            onStep(result)
        }
        val final: EmbeddingClassifier = if (strategy == Strategy.Prototypes) prototypes else head!!
        return RunResult(plan.scenario, strategy, seed, options, plan.classOrder, results, final)
    }

    /** Validation embeddings kept per class alongside [perClass] training ones (the 15:70 split ratio). */
    internal fun validationShare(perClass: Int): Int = maxOf(1, (perClass * 15f / 70f).roundToInt())
}

/**
 * Keeps at most [perClass] examples of each class, a uniform random sample of all it has been
 * offered (reservoir sampling, Vitter 1985), whatever order they came in.
 */
internal class ReplayBuffer(private val perClass: Int, private val random: Random) {
    private val kept = mutableMapOf<Int, MutableList<Example>>()
    private val offered = mutableMapOf<Int, Int>()

    val size: Int get() = kept.values.sumOf { it.size }

    fun contents(): List<Example> = kept.entries.sortedBy { it.key }.flatMap { it.value }

    fun add(examples: List<Example>) {
        if (perClass <= 0) return
        for (example in examples) {
            val list = kept.getOrPut(example.label) { mutableListOf() }
            val seen = (offered[example.label] ?: 0) + 1
            offered[example.label] = seen
            if (list.size < perClass) list += example
            else {
                val slot = random.nextInt(seen)
                if (slot < perClass) list[slot] = example
            }
        }
    }
}
