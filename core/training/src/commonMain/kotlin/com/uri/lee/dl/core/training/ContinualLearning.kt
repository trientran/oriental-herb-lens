package com.uri.lee.dl.core.training

import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * How a dataset is split: [trainShare] and [validationShare] of each class, the rest for testing.
 * With a separate test set, the two shares are used in proportion and nothing is held out to test.
 */
data class SplitSettings(val trainShare: Float = 0.7f, val validationShare: Float = 0.15f) {
    init {
        require(trainShare > 0f && validationShare >= 0f && trainShare + validationShare <= 1f + 1e-4f) { "Shares must be positive and add up to at most 1" }
    }

    val testShare: Float get() = (1f - trainShare - validationShare).coerceAtLeast(0f)

    /** e.g. "70/15/15". */
    val id: String get() = listOf(trainShare, validationShare, testShare).joinToString("/") { (it * 100).roundToInt().toString() }
}

/** A dataset split into training, validation (early stopping) and test examples. */
class DataSplit(val train: List<Example>, val validation: List<Example>, val test: List<Example>) {
    companion object {
        /**
         * [examples] split by [settings], or, when a separate [test] set is given (photos from
         * another device, site or season), [examples] split into training and validation only.
         */
        fun of(examples: List<Example>, random: Random, settings: SplitSettings, test: List<Example>? = null): DataSplit {
            if (test == null) return stratified(examples, random, settings.trainShare, settings.validationShare)
            val share = settings.trainShare / (settings.trainShare + settings.validationShare)
            val split = stratified(examples, random, share, 1f - share)
            // Rounding can leave a photo of a class over: it trains rather than being lost
            return DataSplit(split.train + split.test, split.validation, test)
        }

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

    /** Like class-incremental, but the steps are the dataset's stages, in order (e.g. one country, then another). */
    StageIncremental("stage-incremental"),
    ;

    /** Whether each step brings new classes, so forgetting can be measured per step. */
    val addsClasses: Boolean get() = this != DataIncremental
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

    /**
     * Learning without Forgetting (Li & Hoiem, 2017): continues on the new data only, while
     * keeping the last model's answers on it for the classes that model knew (knowledge
     * distillation at temperature 2, weight 1). Stores no data.
     */
    data object LwF : Strategy("lwf")

    /** Nearest class mean: no training, no forgetting (see [PrototypeClassifier]). */
    data object Prototypes : Strategy("prototypes")

    /** Class means with a shared covariance: no gradient training, no forgetting (see [SldaClassifier]). */
    data object Slda : Strategy("slda")
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
        /**
         * Splits [split] into the scenario's steps; class order and chunks are random per [random].
         * Stage-incremental steps follow [stages] (each class's stage, from 0), classes within a
         * stage in random order.
         */
        fun of(split: DataSplit, classes: Int, scenario: Scenario, random: Random, stages: List<Int>? = null): ScenarioPlan {
            val n = scenario.steps
            require(n >= 1) { "Needs a step" }
            return when (scenario.kind) {
                ScenarioKind.ClassIncremental, ScenarioKind.StageIncremental -> {
                    require(classes >= n) { "Fewer classes than steps" }
                    val order: List<Int>
                    val bounds: List<Int>
                    if (scenario.kind == ScenarioKind.StageIncremental) {
                        requireNotNull(stages) { "Stage-incremental runs need a dataset in stage folders" }
                        require(stages.size == classes && stages.distinct().sorted() == (0 until n).toList()) { "Every class needs one of the $n stages" }
                        order = (0 until classes).shuffled(random).sortedBy { stages[it] }
                        bounds = listOf(0) + (0 until n).map { s -> stages.count { it <= s } }
                    } else {
                        order = (0 until classes).shuffled(random)
                        bounds = (0..n).map { it * classes / n }
                    }
                    val position = IntArray(classes).also { p -> order.forEachIndexed { i, c -> p[c] = i } }
                    fun relabel(examples: List<Example>) = examples.map { Example(it.embedding, position[it.label]) }
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
    /**
     * Whether the app was on screen (visible, screen on). Phones slow down work in the background,
     * so timings are only comparable between steps run in the foreground.
     */
    val foreground: Boolean? = null,
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
        if (scenario.kind.addsClasses) ContinualMetrics(steps.map { it.taskAccuracies }) else null

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
        split: SplitSettings = SplitSettings(),
        /** A separate test set (e.g. another device's photos); otherwise the test photos are held out of [examples]. */
        test: List<Example>? = null,
        /** Each class's stage, for stage-incremental runs. */
        stages: List<Int>? = null,
    ): RunResult {
        val random = Random(seed)
        val plan = ScenarioPlan.of(DataSplit.of(examples, random, split, test), classes, scenario, random, stages)
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
        val slda = SldaClassifier(dimensions)
        var head: ClassifierHead? = null
        val results = mutableListOf<StepResult>()

        for (step in plan.steps) {
            val seen = plan.steps.subList(0, step.index + 1)
            val stepOptions = options.copy(seed = seed * 1000 + step.index)
            val (train, validation) = when (strategy) {
                Strategy.Joint -> seen.flatMap { it.train } to seen.flatMap { it.validation }
                Strategy.Naive, Strategy.LwF -> step.train to step.validation
                is Strategy.Replay -> (step.train + trainBuffer.contents()) to (step.validation + validationBuffer.contents())
                Strategy.Prototypes, Strategy.Slda -> step.train to emptyList()
            }

            val before = monitor.sample()
            val mark = TimeSource.Monotonic.markNow()
            var training: TrainingResult? = null
            if (strategy == Strategy.Prototypes) {
                prototypes.learn(train)
            } else if (strategy == Strategy.Slda) {
                slda.learn(train)
                // Computing the classifier is part of learning, so it counts in the step's time
                slda.head()
            } else {
                val start = if (strategy == Strategy.Joint) null else head?.expanded(step.classesSeen)?.also { grown ->
                    // New classes start from their mean embedding rather than from zero
                    val previous = head!!.classes
                    val known = (0 until previous).toList()
                    for (c in previous until step.classesSeen) grown.imprint(c, step.train.filter { it.label == c }.map { it.embedding }, known)
                }
                val teacher = if (strategy == Strategy.LwF) head?.let { Distillation(it) } else null
                training = HeadTrainer.train(train, validation, step.classesSeen, stepOptions, start, distillation = teacher)
                head = training.head
            }
            val trainMillis = mark.elapsedNow().inWholeMilliseconds
            val after = monitor.sample()

            if (strategy is Strategy.Replay) {
                trainBuffer.add(step.train)
                validationBuffer.add(step.validation)
            }

            val classifier: EmbeddingClassifier = when (strategy) {
                Strategy.Prototypes -> prototypes
                Strategy.Slda -> slda.head()
                else -> head!!
            }
            val tasks = if (plan.scenario.kind.addsClasses) seen.map { it.test } else listOf(step.test)
            val result = StepResult(
                step = step.index,
                classesSeen = step.classesSeen,
                trainExamples = train.size,
                storedVectors = when (strategy) {
                    Strategy.Joint -> seen.sumOf { it.train.size + it.validation.size }
                    Strategy.Naive, Strategy.LwF -> 0
                    is Strategy.Replay -> trainBuffer.size + validationBuffer.size
                    Strategy.Prototypes -> prototypes.classes
                    // Class sums plus the shared scatter matrix, one row per dimension
                    Strategy.Slda -> slda.classes + dimensions
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
        val final: EmbeddingClassifier = when (strategy) {
            Strategy.Prototypes -> prototypes
            Strategy.Slda -> slda.head()
            else -> head!!
        }
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
