package com.uri.lee.dl.shared.research

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A colour per strategy, the same in every chart. */
private fun strategyColor(strategy: String): Color = when {
    strategy == "joint" -> Color(0xFF378ADD)
    strategy == "naive" -> Color(0xFFE24B4A)
    strategy == "prototypes" -> Color(0xFF7F77DD)
    strategy == "replay-5" -> Color(0xFF97C459)
    strategy == "replay-10" -> Color(0xFF639922)
    strategy == "replay-20" -> Color(0xFF1D9E75)
    strategy == "replay-50" -> Color(0xFF0F6E56)
    else -> Color(0xFFBA7517)
}

/** A run's results: per backbone and scenario, final accuracy by strategy and accuracy step by step. */
@Composable
internal fun ResearchResultsView(groups: List<ResultGroup>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEach { group ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${group.backbone} · ${group.scenario}", style = MaterialTheme.typography.titleSmall)
                    val seeds = group.strategies.maxOfOrNull { it.seeds } ?: 0
                    Text("Final accuracy, mean ± SD over $seeds seeds (%)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BarChart(group.strategies)
                    if (group.strategies.any { it.stepAccuracy.size > 1 }) {
                        Text("Accuracy after each step (%)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StepChart(group.strategies)
                        Legend(group.strategies.map { it.strategy })
                    }
                    StrategyTable(group.strategies)
                }
            }
        }
    }
}

@Composable
private fun BarChart(strategies: List<StrategyResult>) {
    val measurer = rememberTextMeasurer()
    val tick = TextStyle(fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axis = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val errorColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 36.dp.toPx()
        val bottom = 36.dp.toPx()
        val top = 8.dp.toPx()
        val width = size.width - left
        val height = size.height - bottom - top
        fun y(v: Float) = top + height * (1 - v.coerceIn(0f, 1f))
        for (v in listOf(0f, 0.5f, 1f)) {
            drawLine(if (v == 0f) axis else grid, Offset(left, y(v)), Offset(size.width, y(v)), strokeWidth = 1f)
            val label = measurer.measure("${(v * 100).roundToInt()}", tick)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(v) - label.size.height / 2f))
        }
        val slot = width / strategies.size.coerceAtLeast(1)
        strategies.forEachIndexed { i, s ->
            val barWidth = slot * 0.6f
            val x = left + slot * i + (slot - barWidth) / 2
            drawRect(strategyColor(s.strategy), Offset(x, y(s.finalMean)), Size(barWidth, y(0f) - y(s.finalMean)))
            if (s.finalSd > 0f) {
                val cx = x + barWidth / 2
                val low = y(s.finalMean - s.finalSd)
                val high = y(s.finalMean + s.finalSd)
                drawLine(errorColor, Offset(cx, low), Offset(cx, high), strokeWidth = 2f)
                drawLine(errorColor, Offset(cx - 6f, high), Offset(cx + 6f, high), strokeWidth = 2f)
                drawLine(errorColor, Offset(cx - 6f, low), Offset(cx + 6f, low), strokeWidth = 2f)
            }
            val value = measurer.measure("${(s.finalMean * 100).roundToInt()}", tick)
            drawText(value, topLeft = Offset(x + barWidth / 2 - value.size.width / 2f, y(0f) + 2.dp.toPx()))
            val name = measurer.measure(s.strategy.replace("replay-", "r"), tick)
            drawText(name, topLeft = Offset(x + barWidth / 2 - name.size.width / 2f, y(0f) + 2.dp.toPx() + value.size.height))
        }
    }
}

@Composable
private fun StepChart(strategies: List<StrategyResult>) {
    val measurer = rememberTextMeasurer()
    val tick = TextStyle(fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axis = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val steps = strategies.maxOf { it.stepAccuracy.size }
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val left = 36.dp.toPx()
        val bottom = 20.dp.toPx()
        val top = 8.dp.toPx()
        val width = size.width - left - 8.dp.toPx()
        val height = size.height - bottom - top
        fun x(step: Int) = left + if (steps <= 1) 0f else width * step / (steps - 1)
        fun y(v: Float) = top + height * (1 - v.coerceIn(0f, 1f))
        for (v in listOf(0f, 0.5f, 1f)) {
            drawLine(if (v == 0f) axis else grid, Offset(left, y(v)), Offset(left + width, y(v)), strokeWidth = 1f)
            val label = measurer.measure("${(v * 100).roundToInt()}", tick)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(v) - label.size.height / 2f))
        }
        for (step in 0 until steps) {
            val label = measurer.measure("step ${step + 1}", tick)
            drawText(label, topLeft = Offset((x(step) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), y(0f) + 3.dp.toPx()))
        }
        strategies.forEach { s ->
            val color = strategyColor(s.strategy)
            s.stepAccuracy.forEachIndexed { i, v ->
                if (i > 0) drawLine(color, Offset(x(i - 1), y(s.stepAccuracy[i - 1])), Offset(x(i), y(v)), strokeWidth = 4f)
                drawCircle(color, radius = 5f, center = Offset(x(i), y(v)))
            }
        }
    }
}

@Composable
private fun Legend(strategies: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        strategies.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(strategyColor(s), CircleShape))
                Text(s, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Forgetting and training time, which the charts don't show. */
@Composable
private fun StrategyTable(strategies: List<StrategyResult>) {
    val style = MaterialTheme.typography.labelSmall
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text("Strategy", Modifier.weight(1.4f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Final %", Modifier.weight(1f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Forgetting %", Modifier.weight(1f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Train s/run", Modifier.weight(1f), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        strategies.forEach { s ->
            Row {
                Text(s.strategy, Modifier.weight(1.4f), style = style)
                Text("${(s.finalMean * 100).roundToInt()} ± ${(s.finalSd * 100).roundToInt()}", Modifier.weight(1f), style = style)
                Text(s.forgetting?.let { "${(it * 100).roundToInt()}" } ?: "–", Modifier.weight(1f), style = style)
                Text(((s.trainSeconds * 10).roundToInt() / 10.0).toString(), Modifier.weight(1f), style = style)
            }
        }
    }
}
