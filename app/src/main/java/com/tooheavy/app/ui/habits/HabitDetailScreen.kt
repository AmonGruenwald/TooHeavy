package com.tooheavy.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Habit
import com.tooheavy.app.data.HabitFrequency
import com.tooheavy.app.data.HabitRepo
import com.tooheavy.app.data.bestStreak
import com.tooheavy.app.data.completionRate
import com.tooheavy.app.data.countOn
import com.tooheavy.app.data.currentStreak
import com.tooheavy.app.data.daysDone
import com.tooheavy.app.data.frequencyText
import com.tooheavy.app.data.isoWeekday
import com.tooheavy.app.data.mondayOf
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.today
import com.tooheavy.app.data.weekProgress
import com.tooheavy.app.data.weekdayShort
import com.tooheavy.app.data.weeklyCompletion
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.ActivityHeatmap
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.common.StatTile
import com.tooheavy.app.ui.common.shortDateLabel
import com.tooheavy.app.ui.theme.TextMuted
import kotlin.math.roundToInt

/** One habit: this week, streaks and completion, a calendar of done days and charts by week and day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(nav: NavController, id: String) {
    val habit = HabitRepo.habit(id)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(habit?.name ?: "Habit", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (habit != null) {
                        IconButton(onClick = { nav.navigate(Routes.habitEdit(habit.id)) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit habit")
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (habit == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Habit not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            HabitDetail(habit, Modifier.padding(pad))
        }
    }
}

@Composable
private fun HabitDetail(h: Habit, modifier: Modifier) {
    val day = today()
    val log = HabitRepo.log(h.id)
    val color = habitColor(h)
    val unitPlural = "${h.streakUnit}s"
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // This week, tappable.
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    buildString {
                        append(h.frequencyText())
                        if (h.counted) append(" · ${h.target}${if (h.unit.isBlank()) " times" else " ${h.unit}"} a day")
                        if (h.reminders.isNotEmpty()) append(" · reminders ${h.reminders.joinToString(", ") { it.label }}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (h.frequency == HabitFrequency.WEEKLY) {
                    val (d, g) = h.weekProgress(log, day)
                    Text("$d of $g days this week", style = MaterialTheme.typography.titleMedium)
                    Meter(if (g > 0) d.toFloat() / g else 0f, color)
                }
                WeekStrip(h, log, day, color)
                Text("Tap a day to mark it done or not.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Streak", "${h.currentStreak(log, day)}", Modifier.weight(1f), unit = unitPlural, icon = Icons.Filled.LocalFireDepartment)
            StatTile("Best streak", "${h.bestStreak(log, day)}", Modifier.weight(1f), unit = unitPlural, icon = Icons.Filled.EmojiEvents)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Last 30 days", h.completionRate(log, day, 30)?.let { "${(it * 100).roundToInt()}" } ?: "–",
                Modifier.weight(1f), unit = "%", icon = Icons.Filled.Percent,
            )
            StatTile("Days done", "${h.daysDone(log)}", Modifier.weight(1f), icon = Icons.Filled.TaskAlt, footnote = "since ${shortDateLabel(h.createdDate.ifEmpty { day })}")
        }

        // Completion over different spans.
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(7 to "Last 7 days", 30 to "Last 30 days", 90 to "Last 90 days", 365 to "Last year").forEach { (n, label) ->
                    val rate = h.completionRate(log, day, n)
                    Column {
                        Row {
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(rate?.let { "${(it * 100).roundToInt()} %" } ?: "–", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Meter((rate ?: 0.0).toFloat(), color, height = 6.dp)
                    }
                }
            }
        }

        ChartCard("Calendar", caption = "Days done over the last months; tap a day for its count", icon = Icons.Filled.CalendarMonth) {
            ActivityHeatmap(log.filterValues { it > 0 }, unitName = h.unit.ifBlank { "time" }.removeSuffix("s"))
        }

        val weeks = h.weeklyCompletion(log, day, 12)
        val mondays = (11 downTo 0).map { shiftDate(mondayOf(day), -7 * it) }
        ChartCard(
            "By week",
            caption = if (h.frequency == HabitFrequency.WEEKLY) "Days done out of ${h.timesPerWeek} each week" else "Share of the due days done each week",
            icon = Icons.Filled.BarChart,
        ) {
            BarChart(
                weeks.map { it * 100 },
                mondays.map { shortDateLabel(it) },
                height = 150.dp,
                goal = 100.0,
                valueFormat = { "${it.roundToInt()} %" },
                tooltipLabels = mondays.map { "Week of ${shortDateLabel(it)}" },
            )
        }

        if (h.counted) {
            val days = (13 downTo 0).map { shiftDate(day, -it) }
            ChartCard(
                "Last 14 days",
                caption = "${h.unit.ifBlank { "Times" }.replaceFirstChar { it.uppercase() }} per day against the goal of ${h.target}",
                icon = Icons.Filled.Insights,
            ) {
                BarChart(
                    days.map { h.countOn(log, it).toDouble() },
                    days.map { weekdayShort(isoWeekday(it)).take(1) },
                    height = 150.dp,
                    goal = h.target.toDouble(),
                    valueFormat = { "${it.roundToInt()}" },
                    tooltipLabels = days.map { shortDateLabel(it) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
