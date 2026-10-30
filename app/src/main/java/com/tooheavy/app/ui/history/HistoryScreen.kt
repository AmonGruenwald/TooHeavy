package com.tooheavy.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.Workout
import com.tooheavy.app.data.completedSetCount
import com.tooheavy.app.data.durationMs
import com.tooheavy.app.data.formatDuration
import com.tooheavy.app.data.formatVolume
import com.tooheavy.app.data.volume
import com.tooheavy.app.data.weeklyBuckets
import com.tooheavy.app.data.workoutsByDate
import com.tooheavy.app.ui.Routes
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.AccessibilityNew
import com.tooheavy.app.ui.common.ActivityHeatmap
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.ScreenHeader
import com.tooheavy.app.ui.common.StatTile
import com.tooheavy.app.ui.common.compact
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Ember
import com.tooheavy.app.ui.theme.Overline
import com.tooheavy.app.ui.theme.StatNumber
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavController) {
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val workouts = Repo.workouts.toList()
    val unit = Repo.settings.unit

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ScreenHeader(
                "History",
                overline = if (workouts.isEmpty()) "Your training log" else "${workouts.size} workouts logged",
            ) {
                if (workouts.isNotEmpty()) {
                    IconButton(onClick = { nav.navigate(Routes.MUSCLES) }) {
                        Icon(Icons.Filled.AccessibilityNew, contentDescription = "Muscles trained")
                    }
                }
            }
        },
    ) { pad ->
        if (workouts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No workouts yet. Start one from the Train tab.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val groups = workouts.groupBy { monthFmt.format(Date(it.startTime)) }
            val weeks = weeklyBuckets(workouts, 12)
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "stats") {
                    val monthStart = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    }.timeInMillis
                    val month = workouts.filter { it.startTime >= monthStart }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Month", "${month.size}", Modifier.weight(1f), icon = Icons.Filled.CalendarMonth)
                        StatTile("Volume", compact(month.sumOf { it.volume() }), Modifier.weight(1f), unit = unit, icon = Icons.Filled.BarChart)
                        StatTile(
                            "Avg time",
                            if (month.isEmpty()) "–" else formatDuration(month.sumOf { it.durationMs() } / month.size),
                            Modifier.weight(1f), icon = Icons.Filled.Timer,
                        )
                    }
                }
                item(key = "weekly") {
                    val counts = weeks.map { it.workouts.size.toDouble() }
                    ChartCard(
                        "Workouts per week",
                        value = String.format(Locale.US, "%.1f", counts.takeLast(4).average()),
                        unit = "avg / week · last 4",
                        icon = Icons.Filled.Insights,
                    ) {
                        BarChart(counts, weeks.map { it.label }, height = 140.dp, tooltipLabels = weeks.map { "Week of ${it.label}" })
                    }
                }
                item(key = "calendar") {
                    ChartCard("Training calendar", icon = Icons.Filled.CalendarMonth) {
                        ActivityHeatmap(workoutsByDate(workouts))
                    }
                }
                for ((month, list) in groups) {
                    item(key = "header_$month") {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                            Text(month, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Text(
                                "${list.size} workouts · ${compact(list.sumOf { it.volume() })} $unit",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted,
                            )
                        }
                    }
                    items(list, key = { it.id }) { w ->
                        HistoryWorkoutCard(w, unit) { nav.navigate(Routes.workoutDetail(w.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryWorkoutCard(w: Workout, unit: String, onClick: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()) }
    val shape = RoundedCornerShape(16.dp)
    GlassCard(
        shape = shape,
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                w.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dateFmt.format(Date(w.startTime)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                HistoryStat("Duration", formatDuration(w.durationMs()), Modifier.weight(1f))
                HistoryStat("Volume", "${formatVolume(w.volume())} $unit", Modifier.weight(1f))
                HistoryStat("Sets", w.completedSetCount().toString(), Modifier.weight(1f))
            }
            if (w.exercises.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
            }
            w.exercises.take(4).forEach { we ->
                val ex = Repo.exercise(we.exerciseId)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumbnail(ex, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${we.sets.size} × ${ex?.name ?: "Unknown exercise"}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (w.exercises.size > 4) {
                Text(
                    "and ${w.exercises.size - 4} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = Overline, color = TextMuted)
        Text(value, style = StatNumber.copy(fontSize = 20.sp), maxLines = 1)
    }
}
