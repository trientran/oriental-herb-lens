package com.uri.lee.dl.core.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResearchTest {

    private val labels = List(4) { "Species $it" }
    private val plan = ResearchPlan(
        scenarios = listOf(Scenario(ScenarioKind.ClassIncremental, 2)),
        strategies = listOf(Strategy.Naive, Strategy.Prototypes),
        seeds = listOf(1, 2),
        options = TrainingOptions(classBalanced = true, maxEpochs = 20),
    )

    private fun datasets() = listOf("a", "b").map { EmbeddedDataset(it, labels, clusters(4, 20, 8, 0.3f)) }

    @Test
    fun runsEveryCombinationForEveryBackbone() {
        val progress = mutableListOf<ResearchProgress>()
        val results = ResearchSession.runAll(datasets(), plan, { RunContext("Test", "test", it, labels) }, onProgress = { progress += it })

        assertEquals(10, progress.size) // 4 runs and a final model, per backbone
        // One header, then 2 backbones × 4 runs
        assertEquals(1 + 8, results.csv.getValue("runs.csv").trim().lines().size)
        assertEquals(setOf("a", "b"), results.models.keys)
    }

    @Test
    fun archiveHoldsResultsAndStandaloneModels() {
        val results = ResearchSession.runAll(datasets().take(1), plan, { RunContext("Test", "test", it, labels) })
        val zip = ResearchArchive.build(results, mapOf("a" to identityBackbone(8)), labels, "readme")

        assertEquals("readme", StoredZip.read(zip, "README.txt")!!.decodeToString())
        assertTrue(StoredZip.read(zip, "results/steps.csv")!!.decodeToString().startsWith("device,"))
        val model = StoredZip.read(zip, "models/a/model.tflite")!!
        // The model's own labels survive being packed in the archive
        assertEquals(labels, TfliteExport.labels(model))
    }

    @Test
    fun resumedSessionSkipsWhatWasDoneAndGivesTheSameRows() {
        val context = { b: String -> RunContext("Test", "test", b, labels) }
        val full = ResearchSession(datasets(), plan, context)
        val all = mutableListOf<ResearchProgress>()
        while (full.hasNext()) all += full.next()

        val done = all.take(3).map { it.key }.toSet()
        val resumed = ResearchSession(datasets(), plan, context, done = done)
        assertEquals(3, resumed.jobsDone)
        assertEquals(full.totalJobs, resumed.totalJobs)
        val rest = mutableListOf<ResearchProgress>()
        while (resumed.hasNext()) rest += resumed.next()

        assertEquals(all.drop(3).map { it.key }, rest.map { it.key })
        // Rows don't depend on what ran before (time and memory columns aside)
        val before = (all[3] as ResearchProgress.Run).rows.getValue("runs.csv").split(',').take(12)
        val after = (rest[0] as ResearchProgress.Run).rows.getValue("runs.csv").split(',').take(12)
        assertEquals(before, after)
    }

    @Test
    fun planAndEmbeddingsSurviveSaving() {
        val saved = ResearchPlan(options = TrainingOptions(classBalanced = true, hiddenUnits = 100, learningRate = 0.005f))
        assertEquals(saved, ResearchFormats.decodePlan(ResearchFormats.encodePlan(saved)))

        val examples = clusters(3, 4, 8, 0.3f)
        val back = ResearchFormats.decodeEmbeddings(ResearchFormats.encodeEmbeddings(examples))
        assertEquals(examples.map { it.label }, back.map { it.label })
        assertTrue(examples.zip(back).all { (a, b) -> a.embedding.contentEquals(b.embedding) })
    }
}
