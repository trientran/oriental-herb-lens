package com.uri.lee.dl.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One line of a [LineChart]: a value per round (round 1 first). */
class ChartLine(val color: Color, val values: List<Float>)

/**
 * Values by training round, with labelled axes: rounds along the bottom, [yLabel] up the side
 * from 0 to [yMax], ticks formatted by [format]. [marker] (a round) is drawn as a dashed line.
 */
@Composable
internal fun LineChart(
    lines: List<ChartLine>,
    yMax: Float,
    yLabel: String,
    xLabel: String,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    marker: Int? = null,
) {
    val measurer = rememberTextMeasurer()
    val tick = TextStyle(fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axis = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rounds = lines.maxOfOrNull { it.values.size } ?: 0
    Column(modifier) {
        Text(yLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val left = 44.dp.toPx()
            val bottom = 22.dp.toPx()
            val width = size.width - left - 8.dp.toPx()
            val chartHeight = size.height - bottom - 6.dp.toPx()
            val top = 6.dp.toPx()
            fun x(round: Int) = left + if (rounds <= 1) 0f else width * (round - 1) / (rounds - 1)
            fun y(value: Float) = top + chartHeight * (1 - (value / yMax).coerceIn(0f, 1f))

            // Grid and y ticks at 0, half and the top
            for (fraction in listOf(0f, 0.5f, 1f)) {
                val value = yMax * fraction
                drawLine(if (fraction == 0f) axis else grid, Offset(left, y(value)), Offset(left + width, y(value)), strokeWidth = 1f)
                val label = measurer.measure(format(value), tick)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(value) - label.size.height / 2f))
            }
            drawLine(axis, Offset(left, top), Offset(left, top + chartHeight), strokeWidth = 1f)

            // x ticks: first, middle and last round, and the axis name
            if (rounds > 0) {
                for (round in listOf(1, (rounds + 1) / 2, rounds).distinct()) {
                    val label = measurer.measure(round.toString(), tick)
                    drawText(label, topLeft = Offset(x(round) - label.size.width / 2f, top + chartHeight + 3.dp.toPx()))
                }
            }

            marker?.takeIf { it in 1..rounds }?.let { round ->
                drawLine(axis, Offset(x(round), top), Offset(x(round), top + chartHeight), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            for (line in lines) {
                for (i in 1 until line.values.size) {
                    drawLine(line.color, Offset(x(i), y(line.values[i - 1])), Offset(x(i + 1), y(line.values[i])), strokeWidth = 4f)
                }
            }
        }
        Text(xLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}
