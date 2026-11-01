package com.tooheavy.app.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.Habit
import com.tooheavy.app.data.HabitFrequency
import com.tooheavy.app.data.HabitRepo
import com.tooheavy.app.data.completionRate
import com.tooheavy.app.data.countOn
import com.tooheavy.app.data.currentStreak
import com.tooheavy.app.data.doneOn
import com.tooheavy.app.data.frequencyText
import com.tooheavy.app.data.isDueOn
import com.tooheavy.app.data.isoWeekday
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.today
import com.tooheavy.app.data.weekProgress
import com.tooheavy.app.data.weekdayShort
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.ProgressRing
import com.tooheavy.app.ui.common.ScreenHeader
import com.tooheavy.app.ui.common.SectionTitle
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.ChartColors
import com.tooheavy.app.ui.theme.Hairline
import com.tooheavy.app.ui.theme.OnAccent
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.Warmup
import kotlin.math.roundToInt

/** Colours a habit can have (Habit.color is an index). */
val HabitColors: List<Color> = ChartColors.series + listOf(ChartColors.Water, Success, Warmup)

fun habitColor(h: Habit): Color = HabitColors[h.color.mod(HabitColors.size)]

/** Ideas offered while there are no habits; each opens the editor filled in. */
private val suggestions = listOf("Drink water" to 8, "Read" to 1, "Meditate" to 1, "Stretch" to 1, "Walk 10,000 steps" to 1, "No sugar" to 1)

/** Habits for today and the last week, with a summary of how today is going. */
@Composable
fun HabitsScreen(nav: NavController) {
    val day = today()
    val active = HabitRepo.active()
    val dueToday = active.filter { it.isDueOn(day) }
    val notToday = active.filter { !it.isDueOn(day) }
    val archived = HabitRepo.habits.filter { it.archived }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ScreenHeader("Habits", overline = if (dueToday.isEmpty()) "Build your routine" else "${dueToday.count { it.doneOn(HabitRepo.log(it.id), day) }} of ${dueToday.size} done today") {
                IconButton(onClick = { nav.navigate(Routes.habitEdit("new")) }) {
                    Icon(Icons.Filled.Add, contentDescription = "New habit")
                }
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (active.isEmpty()) {
                item(key = "empty") { EmptyHabits(nav) }
            } else {
                item(key = "summary") { TodaySummary(dueToday, active) }
                items(dueToday, key = { it.id }) { h -> HabitCard(h, nav) }
                if (notToday.isNotEmpty()) {
                    item(key = "not_today") { SectionTitle("Not today", Modifier.padding(top = 4.dp)) }
                    items(notToday, key = { it.id }) { h -> HabitCard(h, nav) }
                }
            }
            if (archived.isNotEmpty()) {
                item(key = "archived") { SectionTitle("Archived", Modifier.padding(top = 4.dp)) }
                items(archived, key = { it.id }) { h ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.habitDetail(h.id)) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(habitColor(h)))
                            Spacer(Modifier.width(12.dp))
                            Text(h.name, style = MaterialTheme.typography.bodyLarge, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyHabits(nav: NavController) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardLabel("Habits", icon = Icons.Filled.TaskAlt)
            Text("Track anything you want to do regularly", style = MaterialTheme.typography.titleLarge)
            Text(
                "Every day, on chosen days or a few times a week, as a simple yes or a count like 8 glasses of water. " +
                    "Get reminders, keep streaks going and see how you're doing over the weeks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { (name, target) ->
                    AssistChip(onClick = { nav.navigate(Routes.habitEdit("new", name, target)) }, label = { Text(name) })
                }
            }
            GradientButton(onClick = { nav.navigate(Routes.habitEdit("new")) }, modifier = Modifier.fillMaxWidth()) {
                Text("New habit")
            }
        }
    }
}

@Composable
private fun TodaySummary(dueToday: List<Habit>, active: List<Habit>) {
    val day = today()
    val done = dueToday.count { it.doneOn(HabitRepo.log(it.id), day) }
    val rates = active.mapNotNull { it.completionRate(HabitRepo.log(it.id), day, 30) }
    val bestStreak = active.maxOfOrNull { it.currentStreak(HabitRepo.log(it.id), day) } ?: 0
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(if (dueToday.isEmpty()) 0f else done.toFloat() / dueToday.size, size = 96.dp, thickness = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$done/${dueToday.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("today", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryLine("Last 30 days", if (rates.isEmpty()) "–" else "${(rates.average() * 100).roundToInt()} %")
                SummaryLine("Longest streak now", "$bestStreak")
                SummaryLine("Habits", "${active.size}")
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** A habit with today's check (or count) and the last seven days, each tappable. */
@Composable
private fun HabitCard(h: Habit, nav: NavController) {
    val day = today()
    val log = HabitRepo.log(h.id)
    val color = habitColor(h)
    val streak = h.currentStreak(log, day)
    GlassCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.habitDetail(h.id)) }) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (streak > 0) {
                            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Warmup, modifier = Modifier.size(14.dp))
                            Text(
                                "$streak ${h.streakUnit}${if (streak == 1) "" else "s"}  ·  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = Warmup,
                            )
                        }
                        Text(
                            buildString {
                                append(h.frequencyText())
                                if (h.frequency == HabitFrequency.WEEKLY) {
                                    val (d, g) = h.weekProgress(log, day)
                                    append(" · $d/$g")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (h.reminders.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.NotificationsActive, contentDescription = "Has reminders", tint = TextMuted, modifier = Modifier.size(13.dp))
                        }
                    }
                }
                TodayCheck(h, log, day, color)
            }
            WeekStrip(h, log, day, color)
        }
    }
}

/** Today's big check: tap to mark done (a counted habit goes up by one; past its goal it starts over). */
@Composable
private fun TodayCheck(h: Habit, log: Map<String, Int>, day: String, color: Color) {
    val count = h.countOn(log, day)
    val done = count >= h.target
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (done) color else Color.Transparent)
            .border(2.dp, if (done || count > 0) color else Hairline, CircleShape)
            .clickable { HabitRepo.toggle(h.id, day) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            done && !h.counted -> Icon(Icons.Filled.Check, contentDescription = "Done", tint = OnAccent)
            h.counted -> Text(
                "$count/${h.target}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (done) OnAccent else MaterialTheme.colorScheme.onSurface,
            )
            else -> Icon(Icons.Filled.Check, contentDescription = "Mark done", tint = TextMuted)
        }
    }
}

/** The last seven days as dots: filled when done, ringed when partly done or due. Tap to change a past day. */
@Composable
fun WeekStrip(h: Habit, log: Map<String, Int>, day: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).map { shiftDate(day, -it) }.forEach { d ->
            val count = h.countOn(log, d)
            val done = count >= h.target
            val due = h.isDueOn(d)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    weekdayShort(isoWeekday(d)).take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == day) AccentBright else TextMuted,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> color
                                count > 0 -> color.copy(alpha = 0.35f)
                                else -> Color.Transparent
                            },
                        )
                        .border(BorderStroke(1.dp, if (due) color.copy(alpha = 0.6f) else Hairline), CircleShape)
                        .clickable { HabitRepo.toggle(h.id, d) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (h.counted && count > 0 && !done) Text("$count", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
