package com.tooheavy.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tooheavy.app.data.TrendPoint
import com.tooheavy.app.data.daysBetween
import com.tooheavy.app.data.formatWeight

/** Simple line chart of values in order (oldest first). Shows "Not enough data" for fewer than 2 points. */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (values.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Not enough data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        Text(
            "max ${com.tooheavy.app.data.formatWeight(max)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 8.dp)) {
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val step = size.width / (values.size - 1)
            val pts = values.mapIndexed { i, v ->
                Offset(i * step, (size.height - ((v - min) / range * size.height)).toFloat())
            }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            pts.forEach { drawCircle(color, radius = 4.dp.toPx(), center = it) }
        }
        Text(
            "min ${com.tooheavy.app.data.formatWeight(min)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Body-weight chart: weigh-ins as dots and the smoothed trend as a line, spaced by date. */
@Composable
fun TrendChart(points: List<TrendPoint>, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val line = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Weigh in on a few days to see your trend", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val values = points.flatMap { listOf(it.scaleKg, it.trendKg) }
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    val first = points.first().date
    val totalDays = daysBetween(first, points.last().date).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Text(
            "max ${formatWeight(Math.round(max * 10) / 10.0)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 8.dp)) {
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            fun x(p: TrendPoint) = size.width * daysBetween(first, p.date) / totalDays
            fun y(v: Double) = (size.height - (v - min) / range * size.height).toFloat()
            points.forEach { drawCircle(dot, radius = 3.dp.toPx(), center = Offset(x(it), y(it.scaleKg))) }
            val path = Path().apply {
                moveTo(x(points[0]), y(points[0].trendKg))
                points.drop(1).forEach { lineTo(x(it), y(it.trendKg)) }
            }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
        }
        Text(
            "min ${formatWeight(Math.round(min * 10) / 10.0)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Simple bar chart; labels.size must equal values.size. */
@Composable
fun BarChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    val color = MaterialTheme.colorScheme.primary
    val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barW = slot * 0.6f
            values.forEachIndexed { i, v ->
                val h = (v / max * size.height).toFloat()
                drawRoundRect(
                    color,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
