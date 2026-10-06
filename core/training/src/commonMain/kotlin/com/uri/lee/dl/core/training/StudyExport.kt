package com.uri.lee.dl.core.training

import kotlin.math.pow

/** Where and how a run happened, written on every row so files from several devices can be stacked. */
data class RunContext(
    /** e.g. "Redmi Note 12", "iPhone 17 Pro Max", "Chrome 141 on macOS". */
    val device: String,
    /** "android", "ios" or "web". */
    val platform: String,
    val backbone: String,
    /** Class names, by the dataset's class index. */
    val labels: List<String>,
    val appVersion: String = "",
)

/**
 * The study's results as CSV files (RFC 4180), one row per observation, ready for R or pandas:
 * runs, steps, per-class scores, confusion matrices (long format), task accuracies and learning
 * curves. Class columns use the dataset's names, not the run's internal order.
 */
class StudyCsv(private val context: RunContext) {
    private val runs = Table(RUN_KEYS + listOf("classes", "steps", "final_accuracy", "average_incremental_accuracy", "average_accuracy_over_tasks", "forgetting", "backward_transfer", "total_train_ms", "class_order"))
    private val steps = Table(
        RUN_KEYS + listOf(
            "step", "classes_seen", "train_examples", "stored_vectors", "epochs", "best_epoch", "train_ms",
            "train_accuracy", "validation_accuracy", "validation_loss",
            "test_examples", "accuracy", "top3_accuracy", "balanced_accuracy", "macro_f1", "cohens_kappa", "log_loss", "ece",
            "memory_before_bytes", "memory_after_bytes", "cpu_ms", "charge_used_uah", "battery_percent", "charging", "thermal_before", "thermal_after",
        ),
    )
    private val perClass = Table(RUN_KEYS + listOf("step", "class", "precision", "recall", "f1", "support"))
    private val confusion = Table(RUN_KEYS + listOf("step", "actual", "predicted", "count"))
    private val tasks = Table(RUN_KEYS + listOf("step", "task", "accuracy"))
    private val epochs = Table(RUN_KEYS + listOf("step", "epoch", "train_loss", "validation_loss", "validation_accuracy"))

    fun add(run: RunResult) {
        val keys = listOf(
            context.device, context.platform, context.appVersion, context.backbone,
            run.scenario.id, run.strategy.id, run.seed, run.options.hiddenUnits, run.options.learningRate, run.options.l2, run.options.batchSize,
        )
        fun name(label: Int) = context.labels.getOrElse(run.classOrder[label]) { "class ${run.classOrder[label]}" }
        val continual = run.continual
        runs += keys + listOf(
            run.classOrder.size, run.steps.size, run.steps.last().evaluation.accuracy, run.averageIncrementalAccuracy,
            continual?.finalAverageAccuracy, continual?.forgetting, continual?.backwardTransfer,
            run.steps.sumOf { it.trainMillis }, run.classOrder.joinToString(";") { context.labels.getOrElse(it) { "class $it" } },
        )
        for (step in run.steps) {
            val e = step.evaluation
            val t = step.training
            steps += keys + listOf(
                step.step, step.classesSeen, step.trainExamples, step.storedVectors, t?.epochs, t?.bestEpoch, step.trainMillis,
                t?.trainAccuracy, t?.validationAccuracy, t?.validationLoss,
                e.examples, e.accuracy, e.topKAccuracy, e.balancedAccuracy, e.macroF1, e.cohensKappa, e.logLoss, e.calibrationError,
                step.before.memoryBytes, step.after.memoryBytes,
                difference(step.before.cpuMillis, step.after.cpuMillis),
                // The charge counter falls as the battery drains
                difference(step.after.chargeMicroAmpHours, step.before.chargeMicroAmpHours),
                step.after.batteryPercent, step.after.charging, step.before.thermal, step.after.thermal,
            )
            e.perClass.forEachIndexed { c, s -> perClass += keys + listOf(step.step, name(c), s.precision, s.recall, s.f1, s.support) }
            for (actual in 0 until e.classes) for (predicted in 0 until e.classes) {
                val count = e.confusion[actual][predicted]
                if (count > 0) confusion += keys + listOf(step.step, name(actual), name(predicted), count)
            }
            step.taskAccuracies.forEachIndexed { task, accuracy -> tasks += keys + listOf(step.step, task, accuracy) }
            t?.history?.forEach { h -> epochs += keys + listOf(step.step, h.epoch, h.trainLoss, h.validationLoss, h.validationAccuracy) }
        }
    }

    /** File name to CSV text. */
    fun files(): Map<String, String> = mapOf(
        "runs.csv" to runs.text(),
        "steps.csv" to steps.text(),
        "per_class.csv" to perClass.text(),
        "confusion.csv" to confusion.text(),
        "task_accuracy.csv" to tasks.text(),
        "epochs.csv" to epochs.text(),
    )

    private fun difference(from: Long?, to: Long?): Long? = if (from == null || to == null) null else to - from

    private class Table(private val header: List<String>) {
        private val rows = StringBuilder(header.joinToString(",") { csvField(it) }).append("\r\n")

        operator fun plusAssign(values: List<Any?>) {
            check(values.size == header.size) { "Row has ${values.size} values, header ${header.size}" }
            values.joinTo(rows, ",") { csvField(it) }
            rows.append("\r\n")
        }

        fun text() = rows.toString()
    }

    private companion object {
        val RUN_KEYS = listOf("device", "platform", "app_version", "backbone", "scenario", "strategy", "seed", "hidden_units", "learning_rate", "l2", "batch_size")
    }
}

/**
 * [value] to 7 significant digits (a float's precision), so every platform writes the same text:
 * JavaScript would otherwise print a float's double expansion (0.1f as 0.10000000149011612).
 */
internal fun significant(value: Double): String {
    if (value == 0.0) return "0"
    if (value.isInfinite()) return value.toString()
    val magnitude = kotlin.math.floor(kotlin.math.log10(kotlin.math.abs(value))).toInt()
    val decimals = (6 - magnitude).coerceIn(0, 20)
    val scale = 10.0.pow(decimals)
    val rounded = kotlin.math.round(value * scale) / scale
    // Whole numbers without ".0" on the JVM, and no exponent for small values on either platform
    val text = if (decimals == 0) kotlin.math.round(rounded).toLong().toString() else plainDecimal(rounded, decimals)
    return text
}

private fun plainDecimal(value: Double, decimals: Int): String {
    val negative = value < 0
    val scaled = kotlin.math.round(kotlin.math.abs(value) * 10.0.pow(decimals)).toLong()
    val digits = scaled.toString().padStart(decimals + 1, '0')
    val whole = digits.dropLast(decimals)
    val fraction = digits.takeLast(decimals).trimEnd('0')
    return (if (negative) "-" else "") + whole + if (fraction.isEmpty()) "" else ".$fraction"
}

/** A value as a CSV field: empty for null or NaN, quoted when it holds a comma, quote or line break. */
internal fun csvField(value: Any?): String {
    val text = when (value) {
        null -> ""
        is Float -> if (value.isNaN()) "" else significant(value.toDouble())
        is Double -> if (value.isNaN()) "" else significant(value)
        else -> value.toString()
    }
    return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}

/**
 * A trained head with what's needed to use it again: the class names in output order and the
 * backbone that makes its embeddings. Saved as JSON next to the CSV files.
 */
class ModelPack(val backbone: String, val labels: List<String>, val head: ClassifierHead, val createdAt: String = "") {
    init {
        require(labels.size == head.classes) { "A label per class" }
    }

    fun toJson(): String = buildString {
        append("{\"format\":\"herblens-head\",\"version\":1")
        append(",\"backbone\":").append(jsonString(backbone))
        append(",\"createdAt\":").append(jsonString(createdAt))
        append(",\"dimensions\":").append(head.dimensions)
        append(",\"hidden\":").append(head.hidden)
        append(",\"labels\":[").append(labels.joinToString(",") { jsonString(it) }).append(']')
        append(",\"params\":[")
        head.params.forEachIndexed { i, v -> if (i > 0) append(','); append(v.toString()) }
        append("]}")
    }

    companion object {
        /** Reads what [toJson] wrote. */
        fun fromJson(json: String): ModelPack {
            fun field(name: String): String {
                val start = json.indexOf("\"$name\":")
                require(start >= 0) { "Missing $name" }
                return json.substring(start + name.length + 3)
            }
            fun number(name: String) = field(name).takeWhile { it.isDigit() || it == '-' }.toInt()
            fun string(name: String) = parseJsonString(field(name)).first
            fun array(name: String) = field(name).let { it.substring(1, it.indexOf(']')) }

            val labels = mutableListOf<String>()
            var rest = array("labels").trim()
            while (rest.isNotEmpty()) {
                val (label, length) = parseJsonString(rest)
                labels += label
                rest = rest.substring(length).trimStart(',', ' ')
            }
            val params = array("params").split(',').filter { it.isNotBlank() }.map { it.trim().toFloat() }.toFloatArray()
            val dimensions = number("dimensions")
            val hidden = number("hidden")
            return ModelPack(string("backbone"), labels, ClassifierHead(dimensions, hidden, labels.size, params), string("createdAt"))
        }
    }
}

internal fun jsonString(text: String): String = buildString {
    append('"')
    for (ch in text) when {
        ch == '"' -> append("\\\"")
        ch == '\\' -> append("\\\\")
        ch == '\n' -> append("\\n")
        ch < ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
        else -> append(ch)
    }
    append('"')
}

/** The JSON string at the start of [text], and how many characters it took. */
internal fun parseJsonString(text: String): Pair<String, Int> {
    require(text.startsWith('"')) { "Not a string" }
    val out = StringBuilder()
    var i = 1
    while (text[i] != '"') {
        if (text[i] == '\\') {
            i++
            when (text[i]) {
                'n' -> out.append('\n')
                'u' -> { out.append(text.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                else -> out.append(text[i])
            }
        } else out.append(text[i])
        i++
    }
    return out.toString() to i + 1
}
