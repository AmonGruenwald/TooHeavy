package com.tooheavy.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.SetEntry
import com.tooheavy.app.data.SetType
import com.tooheavy.app.data.estimated1RM
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.AreaChart
import com.tooheavy.app.ui.common.AssetImage
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.ChartPoint
import com.tooheavy.app.ui.common.ExerciseAnimation
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.SegmentedTabs
import com.tooheavy.app.ui.common.StatTile
import com.tooheavy.app.ui.common.compact
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.Warmup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(nav: NavController, exerciseId: String, initialTab: Int = 0) {
    val exercise = Repo.exercise(exerciseId)
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = {
                    Text(
                        exercise?.name ?: "Exercise",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        if (exercise == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Exercise not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.padding(pad).fillMaxSize()) {
                SegmentedTabs(
                    listOf("About", "History", "Charts"), tab, { tab = it },
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                when (tab) {
                    0 -> DetailAboutTab(exercise)
                    1 -> DetailHistoryTab(exercise.id)
                    else -> DetailChartsTab(exercise.id)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- About

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailAboutTab(ex: Exercise) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ExerciseAnimation(ex)

        if (ex.images.size >= 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Start" to ex.images[0], "End" to ex.images[1]).forEach { (label, path) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        AssetImage(
                            path,
                            Modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(RoundedCornerShape(10.dp)),
                            ContentScale.Fit,
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        val pills = listOfNotNull(ex.level, ex.equipment, ex.mechanic, ex.force, ex.category).map { it.titleCase() }
        if (pills.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pills.forEach { DetailPill(it) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (ex.primaryMuscles.isNotEmpty()) {
                DetailLabeled("Primary: ", ex.primaryMuscles.joinToString(", ") { it.titleCase() })
            }
            if (ex.secondaryMuscles.isNotEmpty()) {
                DetailLabeled("Secondary: ", ex.secondaryMuscles.joinToString(", ") { it.titleCase() })
            }
        }

        if (ex.instructions.isNotEmpty()) {
            Text("Instructions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ex.instructions.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailPill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DetailLabeled(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
            append(value)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

// ---------------------------------------------------------------- History

private fun detailSetLabels(sets: List<SetEntry>): List<String> {
    var n = 0
    return sets.map { s ->
        when (s.type) {
            SetType.WARMUP -> "W"
            SetType.DROP -> "D"
            SetType.FAILURE -> "F"
            else -> {
                n++
                n.toString()
            }
        }
    }
}

@Composable
private fun DetailHistoryTab(exerciseId: String) {
    val history = Repo.exerciseHistory(exerciseId)
    val unit = Repo.settings.unit
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No history yet. Complete a set of this exercise in a workout to see it here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val fmt = remember { SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(history) { (w, we) ->
            GlassCard(
                Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        fmt.format(Date(w.startTime)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(w.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    val labels = detailSetLabels(we.sets)
                    we.sets.forEachIndexed { i, s ->
                        val weightText = if (s.weight != null) "${formatWeight(s.weight)} $unit" else "-"
                        val repsText = s.reps?.toString() ?: "-"
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                labels[i],
                                modifier = Modifier.width(32.dp),
                                color = if (s.type == SetType.WARMUP) Warmup else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("$weightText × $repsText", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Charts / records

private class Metric(val name: String, val unit: String?, val color: Color, val points: List<ChartPoint>, val format: (Double) -> String)

@Composable
private fun DetailChartsTab(exerciseId: String) {
    val history = Repo.exerciseHistory(exerciseId)
    val unit = Repo.settings.unit
    val sets = history.flatMap { it.second.sets }.filter { it.type != SetType.WARMUP }

    val heaviest = sets.mapNotNull { it.weight }.filter { it > 0 }.maxOrNull()
    val best1rm = sets.mapNotNull { it.estimated1RM() }.maxOrNull()
    val bestVolume = sets.map { (it.weight ?: 0.0) * (it.reps ?: 0) }.filter { it > 0 }.maxOrNull()
    val mostReps = sets.mapNotNull { it.reps }.filter { it > 0 }.maxOrNull()

    val fmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val chrono = history.sortedBy { it.first.startTime }
    fun series(pick: (List<SetEntry>) -> Double?) = chrono.mapNotNull { (w, we) ->
        pick(we.sets.filter { it.type != SetType.WARMUP })?.let { w.startTime to it }
    }.mapIndexed { i, (t, v) -> ChartPoint(i.toFloat(), Math.round(v * 10) / 10.0, fmt.format(Date(t))) }
    val one = { v: Double -> formatWeight(Math.round(v * 10) / 10.0) }
    val metrics = listOf(
        Metric("Est. 1RM", unit, Accent, series { s -> s.mapNotNull { it.estimated1RM() }.maxOrNull() }, one),
        Metric("Heaviest", unit, Accent, series { s -> s.mapNotNull { it.weight }.filter { it > 0 }.maxOrNull() }, one),
        Metric("Volume", unit, Accent, series { s -> s.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }.takeIf { it > 0 } }, ::compact),
        Metric("Reps", "reps", Accent, series { s -> s.sumOf { it.reps ?: 0 }.toDouble().takeIf { it > 0 } }, { "${it.toInt()}" }),
    )
    var selected by rememberSaveable { mutableStateOf(0) }
    val metric = metrics[selected]

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Best e1RM", best1rm?.let { one(it) } ?: "–", Modifier.weight(1f), unit = unit, icon = Icons.Filled.EmojiEvents)
            StatTile("Heaviest", heaviest?.let { formatWeight(it) } ?: "–", Modifier.weight(1f), unit = unit, icon = Icons.Filled.FitnessCenter)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Best set", bestVolume?.let { compact(it) } ?: "–", Modifier.weight(1f), unit = unit, icon = Icons.Filled.BarChart)
            StatTile("Most reps", mostReps?.toString() ?: "–", Modifier.weight(1f), icon = Icons.Filled.Repeat)
        }

        val pts = metric.points
        val change = if (pts.size > 1) pts.last().y - pts.first().y else null
        ChartCard(
            "Progress",
            value = pts.lastOrNull()?.let { metric.format(it.y) } ?: "–",
            unit = metric.unit,
            caption = change?.let { (if (it >= 0) "+" else "") + metric.format(it) + " ${metric.unit.orEmpty()} since ${pts.first().label}" } ?: "Latest session",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
        ) {
            SegmentedTabs(metrics.map { it.name }, selected, { selected = it })
            Spacer(Modifier.height(14.dp))
            key(selected) {
                AreaChart(pts, height = 190.dp, color = metric.color, valueFormat = metric.format, emptyText = "Log this exercise twice to see a trend")
            }
        }

        val recent = chrono.takeLast(10)
        ChartCard("Volume per session", caption = "Weight × reps of working sets, last ${recent.size} sessions", icon = Icons.Filled.BarChart) {
            BarChart(
                recent.map { (_, we) -> we.sets.filter { it.type != SetType.WARMUP }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) } },
                recent.map { (w, _) -> SimpleDateFormat("M/d", Locale.getDefault()).format(Date(w.startTime)) },
                height = 150.dp,
                tooltipLabels = recent.map { (w, _) -> fmt.format(Date(w.startTime)) },
            )
        }

        val buckets = listOf("1-5" to 1..5, "6-8" to 6..8, "9-12" to 9..12, "13-20" to 13..20, "20+" to 21..1000)
        val counts = buckets.map { (_, r) -> sets.count { (it.reps ?: 0) in r }.toDouble() }
        ChartCard("Rep ranges", caption = "How many working sets landed in each rep range", icon = Icons.Filled.Insights) {
            BarChart(
                counts, buckets.map { it.first }, height = 140.dp,
                highlight = counts.indices.maxByOrNull { counts[it] }, valueFormat = { "${it.toInt()}" },
                tooltipLabels = buckets.map { "${it.first} reps" },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailRecordRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "-", fontWeight = FontWeight.SemiBold)
    }
}
