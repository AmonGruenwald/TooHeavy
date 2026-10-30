package com.tooheavy.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tooheavy.app.data.TrendPoint
import com.tooheavy.app.data.dayOfWeek
import com.tooheavy.app.data.daysBetween
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.today
import com.tooheavy.app.ui.theme.BrandColors
import com.tooheavy.app.ui.theme.ChartColors
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Hairline
import com.tooheavy.app.ui.theme.Surface1
import com.tooheavy.app.ui.theme.Surface3
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.TextPrimary
import com.tooheavy.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** One point of a line/area chart. [label] names the point in the tooltip and on the x axis. */
data class ChartPoint(val x: Float, val y: Double, val label: String = "")

// ---------- formatting & scales ----------

/** 950 -> "950", 1234 -> "1.2K", 12345 -> "12K", 80.25 -> "80.3" */
fun compact(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1_000_000 -> trim1(v / 1_000_000) + "M"
        a >= 10_000 -> "${(v / 1000).roundToInt()}K"
        a >= 1000 -> trim1(v / 1000) + "K"
        v == floor(v) -> v.toLong().toString()
        else -> trim1(v)
    }
}

private fun trim1(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

private class Scale(val min: Double, val max: Double, val ticks: List<Double>) {
    val span get() = (max - min).takeIf { it > 0 } ?: 1.0
}

private fun niceScale(lo: Double, hi: Double, zeroBased: Boolean, targetTicks: Int = 3): Scale {
    var a = if (zeroBased) min(0.0, lo) else lo
    var b = hi
    if (b - a < 1e-9) {
        if (zeroBased) b = if (b <= 0) 1.0 else b * 1.2 else { a -= 1; b += 1 }
    }
    val raw = (b - a) / targetTicks
    val mag = 10.0.pow(floor(log10(raw)))
    val step = when (val n = raw / mag) {
        in 0.0..1.0 -> 1.0
        in 1.0..2.0 -> 2.0
        in 2.0..2.5 -> 2.5
        in 2.5..5.0 -> 5.0
        else -> if (n > 5) 10.0 else 1.0
    } * mag
    val min = floor(a / step) * step
    val max = ceil(b / step) * step
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
    return Scale(min, max, ticks)
}

/** 0 → 1 on first show (already 1 in previews); read `.value` inside draw lambdas so only drawing re-runs. */
@Composable
private fun rememberReveal(): Animatable<Float, *> {
    val inspection = LocalInspectionMode.current
    val a = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    return a
}

private val AxisStyle = TextStyle(fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
private val TipValueStyle = TextStyle(fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
private val TipLabelStyle = TextStyle(fontSize = 11.sp, color = TextSecondary)

/** Tooltip bubble anchored above [anchor], kept inside the canvas. */
private fun DrawScope.tooltip(tm: TextMeasurer, anchor: Offset, lines: List<Pair<String, TextStyle>>, swatches: List<Color?> = emptyList()) {
    val layouts = lines.map { (t, s) -> tm.measure(t, s) }
    val pad = 8.dp.toPx()
    val sw = if (swatches.any { it != null }) 12.dp.toPx() else 0f
    val w = layouts.maxOf { it.size.width } + pad * 2 + sw
    val h = layouts.sumOf { it.size.height } + pad * 2
    var left = anchor.x - w / 2
    left = left.coerceIn(0f, max(0f, size.width - w))
    var top = anchor.y - h - 10.dp.toPx()
    if (top < 0f) top = anchor.y + 12.dp.toPx()
    drawRoundRect(Surface3, Offset(left, top), Size(w, h), CornerRadius(10.dp.toPx()))
    drawRoundRect(Hairline, Offset(left, top), Size(w, h), CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
    var y = top + pad
    layouts.forEachIndexed { i, l ->
        swatches.getOrNull(i)?.let { c ->
            drawCircle(c, 3.5.dp.toPx(), Offset(left + pad + 3.5.dp.toPx(), y + l.size.height / 2f))
        }
        drawText(l, topLeft = Offset(left + pad + sw, y))
        y += l.size.height
    }
}

// ---------- line / area ----------

/** Monotone cubic (Fritsch–Carlson) through [pts]: smooth, but never overshoots between points. */
private fun smoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return path
    val n = pts.size
    val d = FloatArray(n - 1) { i -> (pts[i + 1].y - pts[i].y) / (pts[i + 1].x - pts[i].x).coerceAtLeast(1e-3f) }
    val m = FloatArray(n) { i ->
        when (i) {
            0 -> d[0]
            n - 1 -> d[n - 2]
            else -> if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2
        }
    }
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f; m[i + 1] = 0f
        } else {
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / kotlin.math.sqrt(s)
                m[i] = t * a * d[i]; m[i + 1] = t * b * d[i]
            }
        }
    }
    for (i in 0 until n - 1) {
        val h = pts[i + 1].x - pts[i].x
        path.cubicTo(
            pts[i].x + h / 3, pts[i].y + m[i] * h / 3,
            pts[i + 1].x - h / 3, pts[i + 1].y - m[i + 1] * h / 3,
            pts[i + 1].x, pts[i + 1].y,
        )
    }
    return path
}

/**
 * Smooth area chart with nice y ticks, an animated reveal and touch scrubbing (tap or drag for a tooltip).
 * [scatter] draws extra dots (e.g. raw weigh-ins behind a trend line). [goal] draws a labeled reference line.
 */
@Composable
fun AreaChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    color: Color = ChartColors.series[0],
    valueFormat: (Double) -> String = ::compact,
    scatter: List<ChartPoint> = emptyList(),
    goal: Double? = null,
    zeroBased: Boolean = false,
    emptyText: String = "Not enough data yet",
) {
    if (points.size < 2) {
        EmptyChartNote(emptyText, modifier, height)
        return
    }
    val tm = rememberTextMeasurer()
    val reveal = rememberReveal()
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val ys = points.map { it.y } + scatter.map { it.y } + listOfNotNull(goal)
    val scale = remember(ys) { niceScale(ys.min(), ys.max(), zeroBased) }
    val xs = points.map { it.x } + scatter.map { it.x }
    val minX = xs.min()
    val spanX = (xs.max() - minX).takeIf { it > 0f } ?: 1f
    val density = LocalDensity.current
    val gutter = remember(scale) {
        scale.ticks.maxOf { tm.measure(valueFormat(it), AxisStyle).size.width } + with(density) { 8.dp.toPx() }
    }
    val plot = remember { FloatArray(2) } // left, right — shared with the pointer handlers

    fun nearest(x: Float): Int {
        val (l, r) = plot[0] to plot[1]
        val fx = minX + (x - l) / (r - l).coerceAtLeast(1f) * spanX
        return points.indices.minBy { abs(points[it].x - fx) }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectTapGestures { o -> val i = nearest(o.x); selected = if (selected == i) null else i }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { selected = nearest(it.x) },
                    onHorizontalDrag = { change, _ -> selected = nearest(change.position.x) },
                )
            },
    ) {
        val xLabelH = 18.dp.toPx()
        val l = gutter
        val r = size.width - 6.dp.toPx()
        val t = 8.dp.toPx()
        val b = size.height - xLabelH
        plot[0] = l; plot[1] = r
        fun px(x: Float) = l + (x - minX) / spanX * (r - l)
        fun py(y: Double) = (b - (y - scale.min) / scale.span * (b - t)).toFloat()

        scale.ticks.forEach { v ->
            val y = py(v)
            drawLine(ChartColors.Grid, Offset(l, y), Offset(r, y), 1f)
            val lab = tm.measure(valueFormat(v), AxisStyle)
            drawText(lab, topLeft = Offset(0f, y - lab.size.height / 2f))
        }
        // x labels: first, middle, last
        val idx = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
        idx.forEachIndexed { k, i ->
            val text = points[i].label
            if (text.isEmpty()) return@forEachIndexed
            val lab = tm.measure(text, AxisStyle)
            val x = when (k) {
                0 -> px(points[i].x)
                idx.lastIndex -> px(points[i].x) - lab.size.width
                else -> px(points[i].x) - lab.size.width / 2f
            }.coerceIn(l - 4f, size.width - lab.size.width)
            drawText(lab, topLeft = Offset(x, b + 4.dp.toPx()))
        }

        val pts = points.map { Offset(px(it.x), py(it.y)) }
        val line = smoothPath(pts)
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, b)
            lineTo(pts.first().x, b)
            close()
        }
        val revealX = l + (r - l) * reveal.value + 8.dp.toPx()
        clipRect(right = revealX) {
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = t, endY = b))
            scatter.forEach { drawCircle(TextSecondary.copy(alpha = 0.55f), 2.5.dp.toPx(), Offset(px(it.x), py(it.y))) }
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        goal?.let { g ->
            val y = py(g)
            drawLine(TextSecondary.copy(alpha = 0.7f), Offset(l, y), Offset(r, y), 1.dp.toPx())
            val lab = tm.measure("Goal ${valueFormat(g)}", AxisStyle.copy(color = TextSecondary))
            drawText(lab, topLeft = Offset(r - lab.size.width, y - lab.size.height - 2.dp.toPx()))
        }
        if (reveal.value > 0.98f) {
            val end = pts.last()
            drawCircle(Surface1, 6.5.dp.toPx(), end)
            drawCircle(color, 4.5.dp.toPx(), end)
        }
        selected?.let { i ->
            val p = pts[i]
            drawLine(TextSecondary.copy(alpha = 0.6f), Offset(p.x, t), Offset(p.x, b), 1.dp.toPx())
            drawCircle(Surface1, 7.dp.toPx(), p)
            drawCircle(color, 5.dp.toPx(), p)
            tooltip(tm, p, listOf(valueFormat(points[i].y) to TipValueStyle, points[i].label to TipLabelStyle))
        }
    }
}

/** Evenly spaced convenience wrapper; [labels] (optional) name each value. */
@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    labels: List<String> = emptyList(),
    color: Color = ChartColors.series[0],
    valueFormat: (Double) -> String = { formatWeight(Math.round(it * 10) / 10.0) },
) {
    AreaChart(
        values.mapIndexed { i, v -> ChartPoint(i.toFloat(), v, labels.getOrElse(i) { "" }) },
        modifier, height, color, valueFormat,
    )
}

private val shortDate get() = SimpleDateFormat("MMM d", Locale.US)
private val isoDate get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** "2026-10-30" -> "Oct 30" */
fun shortDateLabel(date: String): String = runCatching { shortDate.format(isoDate.parse(date)!!) }.getOrDefault(date)

/** Body-weight chart: weigh-ins as faint dots and the smoothed trend as the line, spaced by date. */
@Composable
fun TrendChart(points: List<TrendPoint>, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    if (points.size < 2) {
        EmptyChartNote("Weigh in on a few days to see your trend", modifier, height)
        return
    }
    val first = points.first().date
    AreaChart(
        points = points.map { ChartPoint(daysBetween(first, it.date).toFloat(), Math.round(it.trendKg * 10) / 10.0, shortDateLabel(it.date)) },
        scatter = points.map { ChartPoint(daysBetween(first, it.date).toFloat(), it.scaleKg) },
        modifier = modifier,
        height = height,
        valueFormat = { trim1(it) },
    )
}

// ---------- bars ----------

private fun topRounded(left: Float, top: Float, right: Float, bottom: Float, r: Float) = Path().apply {
    val rr = min(r, (bottom - top) / 2).coerceAtLeast(0f)
    addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(rr), CornerRadius(rr), CornerRadius.Zero, CornerRadius.Zero))
}

/**
 * Column chart from a zero baseline. The [highlight] column (default: the latest) wears the brand gradient and its
 * value; the rest are de-emphasized. Tap a column for its tooltip.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    color: Color = ChartColors.series[0],
    highlight: Int? = values.lastIndex,
    goal: Double? = null,
    valueFormat: (Double) -> String = ::compact,
    tooltipLabels: List<String> = labels,
) {
    if (values.isEmpty() || values.all { it <= 0.0 }) {
        EmptyChartNote("Nothing logged yet", modifier, height)
        return
    }
    val tm = rememberTextMeasurer()
    val reveal = rememberReveal()
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val scale = remember(values, goal) { niceScale(0.0, max(values.max(), goal ?: 0.0), zeroBased = true) }
    val density = LocalDensity.current
    val gutter = remember(scale) {
        scale.ticks.maxOf { tm.measure(valueFormat(it), AxisStyle).size.width } + with(density) { 8.dp.toPx() }
    }
    val slotOf = remember { FloatArray(2) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values) {
                detectTapGestures { o ->
                    val i = ((o.x - slotOf[0]) / slotOf[1]).toInt()
                    selected = if (i !in values.indices || selected == i) null else i
                }
            },
    ) {
        val xLabelH = 18.dp.toPx()
        val l = gutter
        val r = size.width
        val t = 18.dp.toPx()
        val b = size.height - xLabelH
        val slot = (r - l) / values.size
        slotOf[0] = l; slotOf[1] = slot
        val barW = min(slot * 0.62f, 22.dp.toPx())
        fun py(y: Double) = (b - (y - scale.min) / scale.span * (b - t)).toFloat()

        scale.ticks.forEach { v ->
            val y = py(v)
            drawLine(ChartColors.Grid, Offset(l, y), Offset(r, y), 1f)
            val lab = tm.measure(valueFormat(v), AxisStyle)
            drawText(lab, topLeft = Offset(0f, y - lab.size.height / 2f))
        }
        val every = if (values.size > 10) 2 else 1
        values.forEachIndexed { i, v ->
            val cx = l + slot * i + slot / 2
            if ((values.lastIndex - i) % every == 0) {
                val lab = tm.measure(labels.getOrElse(i) { "" }, AxisStyle.copy(color = if (i == highlight) TextPrimary else TextMuted))
                drawText(lab, topLeft = Offset(cx - lab.size.width / 2f, b + 4.dp.toPx()))
            }
            if (v <= 0) return@forEachIndexed
            val top = b - (b - py(v)) * reveal.value
            val path = topRounded(cx - barW / 2, top, cx + barW / 2, b, 6.dp.toPx())
            val active = i == highlight || i == selected
            if (active) {
                drawPath(path, Brush.verticalGradient(listOf(BrandColors[2], BrandColors[1], BrandColors[0]), startY = top, endY = b))
            } else {
                val a = if (selected != null) 0.3f else 0.55f
                drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = a), color.copy(alpha = a * 0.45f)), startY = top, endY = b))
            }
        }
        goal?.let { g ->
            val y = py(g)
            drawLine(TextSecondary.copy(alpha = 0.75f), Offset(l, y), Offset(r, y), 1.dp.toPx())
            val lab = tm.measure("Goal", AxisStyle.copy(color = TextSecondary))
            drawText(lab, topLeft = Offset(r - lab.size.width, y - lab.size.height - 1.dp.toPx()))
        }
        val i = selected ?: highlight
        if (i != null && i in values.indices && values[i] > 0 && reveal.value > 0.98f) {
            val cx = l + slot * i + slot / 2
            val top = py(values[i])
            if (selected != null) {
                tooltip(tm, Offset(cx, top), listOf(valueFormat(values[i]) to TipValueStyle, tooltipLabels.getOrElse(i) { "" } to TipLabelStyle))
            } else {
                val lab = tm.measure(valueFormat(values[i]), AxisStyle.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                drawText(lab, topLeft = Offset((cx - lab.size.width / 2f).coerceIn(l, r - lab.size.width), top - lab.size.height - 2.dp.toPx()))
            }
        }
    }
}

/** Legend row: colored dot + name per series. */
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (name, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

/** Stacked columns (e.g. kcal from protein / carbs / fat per day) with a legend and per-column tooltip. */
@Composable
fun StackedBarChart(
    stacks: List<List<Double>>,
    labels: List<String>,
    colors: List<Color>,
    names: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    goal: Double? = null,
    valueFormat: (Double) -> String = ::compact,
    tooltipLabels: List<String> = labels,
) {
    val totals = stacks.map { it.sum() }
    Column(modifier.fillMaxWidth()) {
        ChartLegend(names.zip(colors))
        Spacer(Modifier.height(10.dp))
        if (totals.isEmpty() || totals.all { it <= 0 }) {
            EmptyChartNote("Nothing logged yet", height = height)
            return@Column
        }
        val tm = rememberTextMeasurer()
        val reveal = rememberReveal()
        var selected by remember(stacks) { mutableStateOf<Int?>(null) }
        val scale = remember(totals, goal) { niceScale(0.0, max(totals.max(), goal ?: 0.0), zeroBased = true) }
        val density = LocalDensity.current
        val gutter = remember(scale) {
            scale.ticks.maxOf { tm.measure(valueFormat(it), AxisStyle).size.width } + with(density) { 8.dp.toPx() }
        }
        val slotOf = remember { FloatArray(2) }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(stacks) {
                    detectTapGestures { o ->
                        val i = ((o.x - slotOf[0]) / slotOf[1]).toInt()
                        selected = if (i !in stacks.indices || selected == i) null else i
                    }
                },
        ) {
            val l = gutter
            val r = size.width
            val t = 8.dp.toPx()
            val b = size.height - 18.dp.toPx()
            val slot = (r - l) / stacks.size
            slotOf[0] = l; slotOf[1] = slot
            val barW = min(slot * 0.62f, 22.dp.toPx())
            val gap = 2.dp.toPx()
            fun h(v: Double) = (v / scale.span * (b - t)).toFloat()
            scale.ticks.forEach { v ->
                val y = b - h(v - scale.min)
                drawLine(ChartColors.Grid, Offset(l, y), Offset(r, y), 1f)
                val lab = tm.measure(valueFormat(v), AxisStyle)
                drawText(lab, topLeft = Offset(0f, y - lab.size.height / 2f))
            }
            stacks.forEachIndexed { i, seg ->
                val cx = l + slot * i + slot / 2
                val lab = tm.measure(labels.getOrElse(i) { "" }, AxisStyle)
                drawText(lab, topLeft = Offset(cx - lab.size.width / 2f, b + 4.dp.toPx()))
                var y = b
                val lastNonZero = seg.indexOfLast { it > 0 }
                seg.forEachIndexed { k, v ->
                    if (v <= 0) return@forEachIndexed
                    val hh = h(v) * reveal.value
                    val top = y - hh
                    val alpha = if (selected == null || selected == i) 1f else 0.35f
                    val c = colors[k].copy(alpha = alpha)
                    if (k == lastNonZero) drawPath(topRounded(cx - barW / 2, top, cx + barW / 2, y, 6.dp.toPx()), c)
                    else drawRect(c, Offset(cx - barW / 2, top), Size(barW, max(0f, hh)))
                    y = top - gap
                }
            }
            goal?.let { g ->
                val y = b - h(g - scale.min)
                drawLine(TextSecondary.copy(alpha = 0.75f), Offset(l, y), Offset(r, y), 1.dp.toPx())
                val lab = tm.measure("Goal", AxisStyle.copy(color = TextSecondary))
                drawText(lab, topLeft = Offset(r - lab.size.width, y - lab.size.height - 1.dp.toPx()))
            }
            selected?.let { i ->
                val cx = l + slot * i + slot / 2
                val lines = listOf("${valueFormat(totals[i])} · ${tooltipLabels.getOrElse(i) { "" }}" to TipValueStyle) +
                    names.mapIndexed { k, n -> "$n ${valueFormat(stacks[i].getOrElse(k) { 0.0 })}" to TipLabelStyle }
                tooltip(tm, Offset(cx, b - h(totals[i])), lines, listOf<Color?>(null) + colors)
            }
        }
    }
}

/** Ranked horizontal bars with the label above and the value at the end; for distributions such as sets per muscle. */
@Composable
fun RankedBars(
    items: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    color: Color = ChartColors.series[0],
    valueFormat: (Double) -> String = ::compact,
) {
    val reveal = rememberReveal()
    val maxV = (items.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1e-9)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (name, v) ->
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(valueFormat(v), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                }
                Spacer(Modifier.height(5.dp))
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    val r = size.height / 2
                    drawRoundRect(color.copy(alpha = 0.14f), cornerRadius = CornerRadius(r))
                    val w = (size.width * (v / maxV).toFloat() * reveal.value).coerceAtLeast(size.height)
                    val brush = if (i == 0) Brush.horizontalGradient(BrandColors, endX = w) else Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color), endX = w)
                    drawRoundRect(brush, size = Size(w, size.height), cornerRadius = CornerRadius(r))
                }
            }
        }
    }
}

// ---------- rings ----------

/** Donut of shares (e.g. macro calories) with 2dp gaps between segments; [center] sits in the hole. */
@Composable
fun DonutChart(
    values: List<Double>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    thickness: Dp = 14.dp,
    center: @Composable () -> Unit = {},
) {
    val reveal = rememberReveal()
    val total = values.sum()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = thickness.toPx()
            val inset = sw / 2
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Surface3, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            if (total <= 0) return@Canvas
            val gapDeg = Math.toDegrees((3.dp.toPx() / (arc.width / 2)).toDouble()).toFloat()
            var start = -90f
            val shown = values.count { it > 0 }
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val sweep = (v / total * 360f).toFloat() * reveal.value
                val g = if (shown > 1) gapDeg else 0f
                drawArc(colors[i], start + g / 2, (sweep - g).coerceAtLeast(0.5f), false, Offset(inset, inset), arc, style = Stroke(sw))
                start += sweep
            }
        }
        center()
    }
}

/** Progress ring on the brand sweep gradient; turns red past 100 %. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    thickness: Dp = 14.dp,
    over: Boolean = progress > 1f,
    content: @Composable () -> Unit = {},
) {
    val target by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "ring")
    val reveal = rememberReveal()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val animated = target * reveal.value
            val sw = thickness.toPx()
            val inset = sw / 2
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(Surface3, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            if (animated <= 0f) return@Canvas
            val brush = if (over) Brush.sweepGradient(listOf(Danger, Danger)) else Brush.sweepGradient(
                0f to BrandColors[0], 0.5f to BrandColors[1], 0.94f to BrandColors[2], 1f to BrandColors[2],
            )
            // Start the gradient half a cap before the arc so the round start cap is violet, not the wrapped-around ember.
            val cap = Math.toDegrees((sw / 2 / (arc.width / 2)).toDouble()).toFloat()
            rotate(-90f - cap) {
                val sweep = (360f - cap) * animated
                drawArc(brush, cap, sweep, false, Offset(inset, inset), arc, alpha = 0.22f, style = Stroke(sw * 1.9f, cap = StrokeCap.Round))
                drawArc(brush, cap, sweep, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Thin progress meter; the track is a faint step of the same hue. */
@Composable
fun Meter(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val target by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "meter")
    val reveal = rememberReveal()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val animated = target * reveal.value
        val r = size.height / 2
        drawRoundRect(color.copy(alpha = 0.16f), cornerRadius = CornerRadius(r))
        if (animated > 0f) drawRoundRect(color, size = Size(max(size.height, size.width * animated), size.height), cornerRadius = CornerRadius(r))
    }
}

/** Minimal trend line without axes. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = ChartColors.series[0]) {
    if (values.size < 2) return
    val reveal = rememberReveal()
    Canvas(modifier) {
        val lo = values.min()
        val span = (values.max() - lo).takeIf { it > 0 } ?: 1.0
        val pad = 3.dp.toPx()
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, (size.height - pad - (v - lo) / span * (size.height - pad * 2)).toFloat()) }
        val path = smoothPath(pts)
        clipRect(right = size.width * reveal.value + pad) {
            drawPath(Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() },
                Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(color, 3.dp.toPx(), pts.last())
    }
}

// ---------- calendar heatmap ----------

/**
 * Training calendar: one column per week (Mon → Sun downwards), cells shaded by [counts] on the violet ramp.
 * Tap a day to see its count in the caption.
 */
@Composable
fun ActivityHeatmap(
    counts: Map<String, Int>,
    modifier: Modifier = Modifier,
    weeks: Int = 17,
    unitName: String = "workout",
) {
    val end = today()
    // Monday of this week, then back (weeks - 1) weeks.
    val mondayOffset = (dayOfWeek(end) + 5) % 7
    val start = shiftDate(end, -mondayOffset - (weeks - 1) * 7)
    val tm = rememberTextMeasurer()
    val reveal = rememberReveal()
    var selected by remember { mutableStateOf<String?>(null) }
    val geo = remember { FloatArray(3) } // left, cell+gap, top
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val labelW = 16.dp
            val gapDp = 3.dp
            val cell = ((maxWidth - labelW - gapDp * (weeks - 1)) / weeks).coerceAtMost(18.dp)
            val heightDp = 16.dp + cell * 7 + gapDp * 6
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .pointerInput(start) {
                        detectTapGestures { o ->
                            val c = ((o.x - geo[0]) / geo[1]).toInt()
                            val r = ((o.y - geo[2]) / geo[1]).toInt()
                            val d = shiftDate(start, c * 7 + r)
                            selected = if (c in 0 until weeks && r in 0..6 && d <= end && selected != d) d else null
                        }
                    },
            ) {
                val cpx = cell.toPx()
                val g = gapDp.toPx()
                val left = with(density) { labelW.toPx() }
                val top = 16.dp.toPx()
                geo[0] = left; geo[1] = cpx + g; geo[2] = top
                listOf(0 to "M", 2 to "W", 4 to "F").forEach { (row, s) ->
                    val lab = tm.measure(s, AxisStyle)
                    drawText(lab, topLeft = Offset(0f, top + row * (cpx + g) + (cpx - lab.size.height) / 2))
                }
                var lastMonth = -1
                for (c in 0 until weeks) {
                    val weekStart = shiftDate(start, c * 7)
                    val cal = Calendar.getInstance().apply { time = isoDate.parse(weekStart)!! }
                    val month = cal.get(Calendar.MONTH)
                    if (month != lastMonth && c < weeks - 1) {
                        if (lastMonth != -1 || c == 0) {
                            val lab = tm.measure(SimpleDateFormat("MMM", Locale.US).format(cal.time), AxisStyle)
                            drawText(lab, topLeft = Offset(left + c * (cpx + g), 0f))
                        }
                        lastMonth = month
                    }
                    for (r in 0..6) {
                        val d = shiftDate(start, c * 7 + r)
                        if (d > end) continue
                        val n = counts[d] ?: 0
                        val colIdx = if (n <= 0) 0 else min(4, n + 1)
                        val shown = reveal.value * (weeks + 7) >= c + r
                        val col = if (shown) ChartColors.heat[colIdx] else ChartColors.heat[0]
                        val o = Offset(left + c * (cpx + g), top + r * (cpx + g))
                        drawRoundRect(col, o, Size(cpx, cpx), CornerRadius(cpx * 0.28f))
                        if (d == end || d == selected) {
                            drawRoundRect(if (d == selected) TextPrimary else BrandColors[2], o, Size(cpx, cpx), CornerRadius(cpx * 0.28f), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sel = selected
            val text = if (sel != null) {
                val n = counts[sel] ?: 0
                "${shortDateLabel(sel)} · ${if (n == 0) "rest day" else "$n $unitName${if (n == 1) "" else "s"}"}"
            } else {
                val total = counts.filterKeys { it >= start && it <= end }.values.sum()
                "$total ${unitName}s in the last $weeks weeks"
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("Less", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.width(4.dp))
            ChartColors.heat.forEach {
                Box(Modifier.padding(horizontal = 1.5.dp).size(9.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.5.dp)).background(it))
            }
            Spacer(Modifier.width(4.dp))
            Text("More", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

// ---------- chart card ----------

/** Glass card with an overline title, optional headline number and caption, then the chart. */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    unit: String? = null,
    caption: String? = null,
    icon: ImageVector? = null,
    tint: Color = TextSecondary,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardLabel(title, Modifier.weight(1f), icon = icon, tint = tint)
                trailing?.invoke()
            }
            if (value != null) {
                Spacer(Modifier.height(6.dp))
                BigNumber(value, unit, size = 28)
            }
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
