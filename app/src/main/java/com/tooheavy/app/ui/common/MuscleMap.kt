package com.tooheavy.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.MuscleGroup
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Surface3
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.TextSecondary
import java.util.Locale

// A stylised front and back body whose muscles are shaded by how much they were trained. Shapes live in a
// 100 x 200 box per figure; muscle names are the exercise library's.

private sealed interface Part
private class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val rot: Float = 0f) : Part
private class RRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : Part
private class Poly(vararg val pts: Float) : Part

/** Drawn but never shaded. */
private const val BODY = ""

private val front: List<Pair<String, List<Part>>> = listOf(
    BODY to listOf(Oval(50f, 14f, 8.5f, 10.5f), Oval(20.5f, 96f, 3.5f, 5f, 12f), Oval(79.5f, 96f, 3.5f, 5f, -12f),
        Oval(40f, 183f, 4.5f, 4f), Oval(60f, 183f, 4.5f, 4f)),
    "neck" to listOf(RRect(45.5f, 23f, 9f, 8f, 2f)),
    "traps" to listOf(Poly(45f, 27f, 36f, 33f, 45f, 33f), Poly(55f, 27f, 64f, 33f, 55f, 33f)),
    "shoulders" to listOf(Oval(31f, 40f, 7f, 8f, -15f), Oval(69f, 40f, 7f, 8f, 15f)),
    "chest" to listOf(RRect(37.5f, 33.5f, 12f, 17f, 5f), RRect(50.5f, 33.5f, 12f, 17f, 5f)),
    "biceps" to listOf(Oval(27.5f, 58f, 4.8f, 10f, 8f), Oval(72.5f, 58f, 4.8f, 10f, -8f)),
    "forearms" to listOf(Oval(23.5f, 80f, 4.2f, 11f, 12f), Oval(76.5f, 80f, 4.2f, 11f, -12f)),
    "abdominals" to listOf(RRect(43f, 53f, 14f, 32f, 4f), RRect(37f, 55f, 5f, 24f, 2.5f), RRect(58f, 55f, 5f, 24f, 2.5f)),
    "abductors" to listOf(Oval(35.5f, 95f, 3.2f, 8f, -8f), Oval(64.5f, 95f, 3.2f, 8f, 8f)),
    "quadriceps" to listOf(Oval(40.5f, 120f, 7f, 21f, 4f), Oval(59.5f, 120f, 7f, 21f, -4f)),
    "adductors" to listOf(Oval(47.6f, 108f, 2.4f, 10f, -6f), Oval(52.4f, 108f, 2.4f, 10f, 6f)),
    "calves" to listOf(Oval(40.5f, 162f, 5f, 15f, 2f), Oval(59.5f, 162f, 5f, 15f, -2f)),
)

private val back: List<Pair<String, List<Part>>> = listOf(
    BODY to listOf(Oval(50f, 14f, 8.5f, 10.5f), Oval(20.5f, 96f, 3.5f, 5f, 12f), Oval(79.5f, 96f, 3.5f, 5f, -12f),
        Oval(40f, 183f, 4.5f, 4f), Oval(60f, 183f, 4.5f, 4f)),
    "neck" to listOf(RRect(45.5f, 23f, 9f, 7f, 2f)),
    "traps" to listOf(Poly(50f, 25f, 37f, 35f, 44f, 40f, 50f, 58f, 56f, 40f, 63f, 35f)),
    "shoulders" to listOf(Oval(31f, 40f, 7f, 8f, -15f), Oval(69f, 40f, 7f, 8f, 15f)),
    "lats" to listOf(Poly(38f, 44f, 45f, 46f, 47f, 62f, 44f, 78f, 37f, 66f), Poly(62f, 44f, 55f, 46f, 53f, 62f, 56f, 78f, 63f, 66f)),
    "middle back" to listOf(Oval(45.5f, 50f, 2.4f, 7f), Oval(54.5f, 50f, 2.4f, 7f)),
    "lower back" to listOf(RRect(44f, 66f, 12f, 18f, 4f)),
    "triceps" to listOf(Oval(27.5f, 57f, 4.8f, 10f, 8f), Oval(72.5f, 57f, 4.8f, 10f, -8f)),
    "forearms" to listOf(Oval(23.5f, 80f, 4.2f, 11f, 12f), Oval(76.5f, 80f, 4.2f, 11f, -12f)),
    "abductors" to listOf(Oval(35f, 92f, 3f, 7f, -8f), Oval(65f, 92f, 3f, 7f, 8f)),
    "glutes" to listOf(Oval(43f, 97f, 7.5f, 9f), Oval(57f, 97f, 7.5f, 9f)),
    "hamstrings" to listOf(Oval(41.5f, 126f, 6.5f, 18f, 3f), Oval(58.5f, 126f, 6.5f, 18f, -3f)),
    "calves" to listOf(Oval(41f, 160f, 6f, 14f, 2f), Oval(59f, 160f, 6f, 14f, -2f)),
)

private val BodyColor = Color(0xFF2C2824)
private val IdleColor = Color(0xFF3A3530)

/** Shade for a muscle trained at [level] (0..1): the idle tone at 0, full gold at 1. */
private fun shade(level: Float): Color =
    if (level <= 0f) IdleColor else Accent.copy(alpha = 0.3f + 0.7f * level.coerceIn(0f, 1f))

private fun DrawScope.figure(parts: List<Pair<String, List<Part>>>, levels: Map<String, Float>) {
    val k = size.width / 100f
    parts.forEach { (muscle, shapes) ->
        val color = if (muscle == BODY) BodyColor else shade(levels[muscle] ?: 0f)
        shapes.forEach { s ->
            when (s) {
                is Oval -> rotate(s.rot, Offset(s.cx * k, s.cy * k)) {
                    drawOval(color, Offset((s.cx - s.rx) * k, (s.cy - s.ry) * k), Size(2 * s.rx * k, 2 * s.ry * k))
                }
                is RRect -> drawRoundRect(color, Offset(s.x * k, s.y * k), Size(s.w * k, s.h * k), CornerRadius(s.r * k))
                is Poly -> drawPath(
                    Path().apply {
                        moveTo(s.pts[0] * k, s.pts[1] * k)
                        for (i in 2 until s.pts.size step 2) lineTo(s.pts[i] * k, s.pts[i + 1] * k)
                        close()
                    },
                    color,
                )
            }
        }
    }
}

/** Front and back view with each library muscle shaded by [levels] (0..1). */
@Composable
fun BodyMap(levels: Map<String, Float>, modifier: Modifier = Modifier, figureWidth: Dp = 110.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        listOf("Front" to front, "Back" to back).forEach { (label, parts) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(figureWidth).aspectRatio(0.5f)) { figure(parts, levels) }
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

/** Levels for [credit] (sets per muscle) relative to [full] sets, or to the biggest value when [full] is null. */
fun muscleLevels(credit: Map<String, Double>, full: Double? = null): Map<String, Float> {
    val top = full ?: credit.values.maxOrNull() ?: return emptyMap()
    if (top <= 0) return emptyMap()
    return credit.mapValues { (it.value / top).toFloat().coerceIn(0f, 1f) }
}

/** An exercise's muscles on the body map: primary ones fully, secondary ones lightly. */
fun exerciseLevels(e: Exercise): Map<String, Float> =
    e.secondaryMuscles.associateWith { 0.35f } + e.primaryMuscles.associateWith { 1f }

/** "4" / "4.5" */
fun formatSets(v: Double): String = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

/**
 * Body map plus ranked bars of sets per muscle group, for one workout or a period. [credit] is per library
 * muscle (for the map) and [groups] per MuscleGroup (for the bars).
 */
@Composable
fun MuscleOverview(credit: Map<String, Double>, groups: Map<String, Double>, modifier: Modifier = Modifier, full: Double? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BodyMap(muscleLevels(credit, full), Modifier.fillMaxWidth())
        val ranked = groups.filterValues { it > 0 }.toList().sortedByDescending { it.second }
        if (ranked.isNotEmpty()) {
            RankedBars(ranked.map { (g, v) -> MuscleGroup.label(g) to v }, valueFormat = { "${formatSets(it)} sets" })
            Text(
                "Sets on the main muscles count fully, sets that also work a muscle count half.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

/** The muscles an exercise works as small labels: primary ones in gold, secondary ones muted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleTags(e: Exercise?, modifier: Modifier = Modifier) {
    if (e == null || (e.primaryMuscles.isEmpty() && e.secondaryMuscles.isEmpty())) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        e.primaryMuscles.forEach { Pill(it.titleCase(), AccentBright) }
        e.secondaryMuscles.filter { it !in e.primaryMuscles }.forEach { Pill(it.titleCase(), TextSecondary) }
    }
}

/** A labelled body map for one exercise, for its detail page. */
@Composable
fun ExerciseMuscleMap(e: Exercise, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BodyMap(exerciseLevels(e), figureWidth = 90.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Gold: main muscles · faint: also worked",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Normal,
        )
    }
}
