package com.uri.lee.dl.core.training

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContinualLearningTest {

    private val data = clusters(classes = 8, perClass = 40, dimensions = 24, spread = 0.9f, seed = 11)

    @Test
    fun splitKeepsEveryClassInProportion() {
        val split = DataSplit.stratified(data, Random(1))

        assertEquals(data.size, split.train.size + split.validation.size + split.test.size)
        for (c in 0 until 8) {
            assertEquals(28, split.train.count { it.label == c })
            assertEquals(6, split.validation.count { it.label == c })
            assertEquals(6, split.test.count { it.label == c })
        }
    }

    @Test
    fun classIncrementalBringsNewClassesInARandomOrder() {
        val plan = ScenarioPlan.of(DataSplit.stratified(data, Random(1)), 8, Scenario(ScenarioKind.ClassIncremental, 4), Random(1))

        assertEquals((0 until 8).toSet(), plan.classOrder.toSet())
        assertEquals(listOf(2, 4, 6, 8), plan.steps.map { it.classesSeen })
        plan.steps.forEach { step -> assertEquals(setOf(step.classesSeen - 2, step.classesSeen - 1), step.train.map { it.label }.toSet()) }
    }

    @Test
    fun dataIncrementalSpreadsEachClassOverTheSteps() {
        val plan = ScenarioPlan.of(DataSplit.stratified(data, Random(1)), 8, Scenario(ScenarioKind.DataIncremental, 4), Random(1))

        plan.steps.forEach { step -> assertEquals(8, step.train.map { it.label }.toSet().size) }
        assertEquals(8 * 28, plan.steps.sumOf { it.train.size })
    }

    @Test
    fun replayBufferKeepsAFairSampleOfEachClass() {
        val buffer = ReplayBuffer(5, Random(1))
        buffer.add(data.filter { it.label < 2 })
        buffer.add(data.filter { it.label == 2 })

        assertEquals(15, buffer.size)
        assertEquals(setOf(0, 1, 2), buffer.contents().map { it.label }.toSet())
    }

    @Test
    fun strategiesRankAsExpected() {
        val scenario = Scenario(ScenarioKind.ClassIncremental, 4)
        fun final(strategy: Strategy) = ContinualRunner.run(data, 8, scenario, strategy, seed = 3).steps.last().evaluation.accuracy
        val naive = final(Strategy.Naive)
        val replay = final(Strategy.Replay(10))
        val joint = final(Strategy.Joint)

        assertTrue(naive < replay, "naive $naive, replay $replay")
        assertTrue(replay <= joint + 0.05f, "replay $replay, joint $joint")
        assertTrue(final(Strategy.Prototypes) > naive)
    }

    @Test
    fun naiveFineTuningForgets() {
        val run = ContinualRunner.run(data, 8, Scenario(ScenarioKind.ClassIncremental, 4), Strategy.Naive, seed = 3)

        assertTrue(run.continual!!.forgetting > 0.3f, "forgetting ${run.continual!!.forgetting}")
    }

    @Test
    fun sameSeedSameResultsForEveryStrategy() {
        val scenario = Scenario(ScenarioKind.DataIncremental, 3)
        val a = ContinualRunner.run(data, 8, scenario, Strategy.Replay(5), seed = 4)
        val b = ContinualRunner.run(data, 8, scenario, Strategy.Replay(5), seed = 4)

        assertEquals(a.steps.map { it.evaluation.accuracy }, b.steps.map { it.evaluation.accuracy })
        assertEquals(a.classOrder, ContinualRunner.run(data, 8, scenario, Strategy.Joint, seed = 4).classOrder)
    }

    @Test
    fun exportsEveryTable() {
        val run = ContinualRunner.run(data, 8, Scenario(ScenarioKind.ClassIncremental, 2), Strategy.Replay(5), seed = 1)
        val csv = StudyCsv(RunContext("Test phone", "android", "mobilenet_v3_large", List(8) { "Species, $it" }))
        csv.add(run)
        val files = csv.files()

        assertEquals(setOf("runs.csv", "steps.csv", "per_class.csv", "confusion.csv", "task_accuracy.csv", "epochs.csv"), files.keys)
        assertEquals(3, files.getValue("steps.csv").trim().lines().size)
        assertTrue(files.getValue("per_class.csv").contains("\"Species, "))
    }

    @Test
    fun modelPackRoundTrips() {
        val head = HeadTrainer.train(data, 8, TrainingOptions(hiddenUnits = 4, maxEpochs = 3)).head
        val pack = ModelPack("mobilenet_v3_small", List(8) { "Cây \"$it\", ok" }, head, "2026-10-06")
        val back = ModelPack.fromJson(pack.toJson())

        assertEquals(pack.labels, back.labels)
        assertEquals(pack.backbone, back.backbone)
        assertTrue(head.params.contentEquals(back.head.params))
    }
}
