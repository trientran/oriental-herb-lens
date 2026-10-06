package com.uri.lee.dl.shared.research

import kotlin.math.sqrt

/** One strategy's results over its seeds, for the charts. */
data class StrategyResult(
    val strategy: String,
    val seeds: Int,
    val finalMean: Float,
    val finalSd: Float,
    /** Mean forgetting (class-incremental only). */
    val forgetting: Float?,
    /** Mean training time per run, seconds. */
    val trainSeconds: Float,
    /** Mean accuracy after each step. */
    val stepAccuracy: List<Float>,
)

/** One backbone and scenario's strategies. */
data class ResultGroup(val backbone: String, val scenario: String, val strategies: List<StrategyResult>)

/** Summarises a run's runs.csv and steps.csv (as Research mode writes them) for the charts. */
object ResearchSummary {

    fun of(runsCsv: String, stepsCsv: String): List<ResultGroup> {
        val runs = parse(runsCsv)
        val steps = parse(stepsCsv)
        fun key(row: Map<String, String>) = Triple(row["backbone"].orEmpty(), row["scenario"].orEmpty(), row["strategy"].orEmpty())
        val stepsByKey = steps.groupBy(::key)
        return runs.groupBy { it["backbone"].orEmpty() to it["scenario"].orEmpty() }.map { (group, rows) ->
            val strategies = rows.groupBy { it["strategy"].orEmpty() }.map { (strategy, list) ->
                val finals = list.mapNotNull { it["final_accuracy"]?.toFloatOrNull() }
                val forgetting = list.mapNotNull { it["forgetting"]?.toFloatOrNull() }
                val stepRows = stepsByKey[Triple(group.first, group.second, strategy)].orEmpty()
                val byStep = stepRows.groupBy { it["step"]?.toIntOrNull() ?: 0 }.entries.sortedBy { it.key }.associate { it.key to it.value }
                StrategyResult(
                    strategy = strategy,
                    seeds = list.size,
                    finalMean = finals.mean(),
                    finalSd = finals.sd(),
                    forgetting = forgetting.takeIf { it.isNotEmpty() }?.mean(),
                    trainSeconds = list.mapNotNull { it["total_train_ms"]?.toFloatOrNull() }.mean() / 1000f,
                    stepAccuracy = byStep.values.map { s -> s.mapNotNull { it["accuracy"]?.toFloatOrNull() }.mean() },
                )
            }
            ResultGroup(group.first, group.second, strategies)
        }.sortedWith(compareBy({ it.backbone }, { it.scenario }))
    }

    private fun List<Float>.mean() = if (isEmpty()) 0f else sum() / size
    private fun List<Float>.sd(): Float {
        if (size < 2) return 0f
        val m = mean()
        return sqrt(sumOf { ((it - m) * (it - m)).toDouble() }.toFloat() / (size - 1))
    }

    /** Rows of a CSV (RFC 4180, quoted fields allowed) as maps from the header's names. */
    internal fun parse(csv: String): List<Map<String, String>> {
        val lines = csv.split("\r\n", "\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = fields(lines.first())
        return lines.drop(1).map { line -> header.zip(fields(line)).toMap() }
    }

    private fun fields(line: String): List<String> {
        val out = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += field.toString(); field.clear() }
                else -> field.append(c)
            }
            i++
        }
        out += field.toString()
        return out
    }
}
