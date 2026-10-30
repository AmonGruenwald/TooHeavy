package com.tooheavy.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.DashboardWidget
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.completedSetCount
import com.tooheavy.app.data.dayOfWeek
import com.tooheavy.app.data.durationMs
import com.tooheavy.app.data.formatClock
import com.tooheavy.app.data.formatDuration
import com.tooheavy.app.data.formatVolume
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.isDeload
import com.tooheavy.app.data.mesoWeek
import com.tooheavy.app.data.muscleSets
import com.tooheavy.app.data.nextDayIndex
import com.tooheavy.app.data.oneRepMaxSeries
import com.tooheavy.app.data.personalRecords
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.data.today
import com.tooheavy.app.data.volume
import com.tooheavy.app.data.weekStreak
import com.tooheavy.app.data.weeklyBuckets
import com.tooheavy.app.data.workoutsByDate
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.ActivityHeatmap
import com.tooheavy.app.ui.common.AreaChart
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.BigNumber
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.ChartPoint
import com.tooheavy.app.ui.common.DonutChart
import com.tooheavy.app.ui.common.EmptyChartNote
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.common.Pill
import com.tooheavy.app.ui.common.ProgressRing
import com.tooheavy.app.ui.common.RankedBars
import com.tooheavy.app.ui.common.Sparkline
import com.tooheavy.app.ui.common.StackedBarChart
import com.tooheavy.app.ui.common.TrendChart
import com.tooheavy.app.ui.common.compact
import com.tooheavy.app.ui.common.shortDateLabel
import com.tooheavy.app.ui.navigateTab
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.BrandBrush
import com.tooheavy.app.ui.theme.BrandColors
import com.tooheavy.app.ui.theme.ChartColors
import com.tooheavy.app.ui.theme.Ember
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.Surface3
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.TextSecondary
import com.tooheavy.app.ui.theme.Warmup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Catalog of Home dashboard widgets. */
object DashboardWidgets {
    class Def(val id: String, val title: String, val description: String, val icon: ImageVector, val tint: Color)

    const val UP_NEXT = "up_next"
    const val STREAK = "streak"
    const val CALORIES = "calories"
    const val MACROS = "macros"
    const val WEEKLY_VOLUME = "weekly_volume"
    const val WEEKLY_WORKOUTS = "weekly_workouts"
    const val CONSISTENCY = "consistency"
    const val BODY_WEIGHT = "body_weight"
    const val CALORIES_WEEK = "calories_week"
    const val MACRO_SPLIT = "macro_split"
    const val MUSCLES = "muscles"
    const val RECORDS = "records"
    const val TOP_LIFT = "top_lift"
    const val EXPENDITURE = "expenditure"
    const val RECENT = "recent"
    const val LIFETIME = "lifetime"
    const val TRAINING_TIME = "training_time"

    val all = listOf(
        Def(UP_NEXT, "Up next", "Your next program day, routine or a quick start", Icons.Filled.Bolt, BrandColors[1]),
        Def(STREAK, "Week streak", "Consecutive weeks trained and this week's days", Icons.Filled.LocalFireDepartment, Ember),
        Def(CALORIES, "Calories today", "Ring of today's calories against your goal", Icons.Filled.Restaurant, BrandColors[0]),
        Def(MACROS, "Macros today", "Protein, carbs and fat split for today", Icons.Filled.DonutLarge, ChartColors.Protein),
        Def(WEEKLY_VOLUME, "Weekly volume", "Total weight lifted per week", Icons.Filled.BarChart, AccentBright),
        Def(WEEKLY_WORKOUTS, "Workouts per week", "How many sessions you fit in each week", Icons.Filled.Insights, AccentBright),
        Def(CONSISTENCY, "Consistency", "Calendar heatmap of your training days", Icons.Filled.CalendarMonth, Success),
        Def(BODY_WEIGHT, "Body weight", "Weigh-ins and your smoothed weight trend", Icons.Filled.MonitorWeight, ChartColors.Fat),
        Def(CALORIES_WEEK, "Calories this week", "Daily intake against your calorie goal", Icons.Filled.LocalFireDepartment, Ember),
        Def(MACRO_SPLIT, "Macro split", "Calories from protein, carbs and fat per day", Icons.Filled.PieChart, ChartColors.Carbs),
        Def(MUSCLES, "Muscle focus", "Working sets per muscle over the last 30 days", Icons.Filled.AccessibilityNew, BrandColors[1]),
        Def(RECORDS, "Personal records", "Best estimated 1RMs of your main lifts", Icons.Filled.EmojiEvents, Warmup),
        Def(TOP_LIFT, "Top lift progress", "Estimated 1RM over time for your most-trained lift", Icons.AutoMirrored.Filled.TrendingUp, Success),
        Def(EXPENDITURE, "Daily burn", "The coach's estimate of what you burn per day", Icons.Filled.Speed, Ember),
        Def(RECENT, "Recent workouts", "Your latest sessions at a glance", Icons.Filled.History, AccentBright),
        Def(LIFETIME, "Lifetime stats", "Workouts, volume, sets and time, all-time", Icons.AutoMirrored.Filled.ShowChart, AccentBright),
        Def(TRAINING_TIME, "Training time", "Minutes trained per week", Icons.Filled.Timer, ChartColors.Fat),
    )

    fun def(id: String): Def? = all.firstOrNull { it.id == id }

    val defaultLayout = listOf(
        DashboardWidget(UP_NEXT, wide = true),
        DashboardWidget(STREAK, wide = false),
        DashboardWidget(CALORIES, wide = false),
        DashboardWidget(WEEKLY_VOLUME, wide = true),
        DashboardWidget(CONSISTENCY, wide = true),
        DashboardWidget(MACROS, wide = false),
        DashboardWidget(EXPENDITURE, wide = false),
        DashboardWidget(BODY_WEIGHT, wide = true),
        DashboardWidget(TOP_LIFT, wide = true),
        DashboardWidget(RECORDS, wide = true),
    )
}

/** Height of half-width widgets, so paired tiles line up. */
val HalfWidgetHeight: Dp = 188.dp

@Composable
fun WidgetBody(w: DashboardWidget, nav: NavController, modifier: Modifier = Modifier) {
    val def = DashboardWidgets.def(w.id) ?: return
    val m = if (w.wide) modifier.fillMaxWidth() else modifier.fillMaxWidth().height(HalfWidgetHeight)
    when (w.id) {
        DashboardWidgets.UP_NEXT -> UpNext(nav, w.wide, m)
        DashboardWidgets.STREAK -> Streak(def, w.wide, m)
        DashboardWidgets.CALORIES -> CaloriesToday(def, w.wide, m) { nav.navigateTab(Routes.NUTRITION) }
        DashboardWidgets.MACROS -> MacrosToday(def, w.wide, m) { nav.navigateTab(Routes.NUTRITION) }
        DashboardWidgets.WEEKLY_VOLUME -> WeeklyVolume(def, w.wide, m)
        DashboardWidgets.WEEKLY_WORKOUTS -> WeeklyWorkouts(def, w.wide, m)
        DashboardWidgets.CONSISTENCY -> Consistency(def, w.wide, m)
        DashboardWidgets.BODY_WEIGHT -> BodyWeight(def, w.wide, m) { nav.navigate(Routes.COACH) }
        DashboardWidgets.CALORIES_WEEK -> CaloriesWeek(def, w.wide, m)
        DashboardWidgets.MACRO_SPLIT -> MacroSplit(def, w.wide, m)
        DashboardWidgets.MUSCLES -> Muscles(def, w.wide, m)
        DashboardWidgets.RECORDS -> Records(def, w.wide, m, nav)
        DashboardWidgets.TOP_LIFT -> TopLift(def, w.wide, m, nav)
        DashboardWidgets.EXPENDITURE -> Expenditure(def, w.wide, m) { nav.navigate(Routes.COACH) }
        DashboardWidgets.RECENT -> Recent(def, w.wide, m, nav)
        DashboardWidgets.LIFETIME -> Lifetime(def, w.wide, m)
        DashboardWidgets.TRAINING_TIME -> TrainingTime(def, w.wide, m)
    }
}

// ---------- scaffolding ----------

@Composable
private fun WidgetCard(
    def: DashboardWidgets.Def,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardLabel(def.title, Modifier.weight(1f), icon = def.icon, tint = def.tint)
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Half-size layout: a headline number, a caption and a sparkline filling the rest. */
@Composable
private fun ColumnScope.HalfStat(value: String, unit: String?, caption: String, spark: List<Double>, color: Color = ChartColors.series[0]) {
    BigNumber(value, unit, size = 26)
    Text(caption, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.weight(1f))
    if (spark.size >= 2) Sparkline(spark, Modifier.fillMaxWidth().height(46.dp), color)
}

private fun kcal(v: Double) = String.format(Locale.US, "%,d", v.roundToInt())

private fun pctChange(now: Double, before: Double): String? {
    if (before <= 0) return null
    val p = ((now - before) / before * 100).roundToInt()
    return (if (p >= 0) "+$p" else "$p") + " % vs last week"
}

// ---------- widgets ----------

@Composable
private fun UpNext(nav: NavController, wide: Boolean, modifier: Modifier) {
    val active = Repo.activeWorkout
    val program = Repo.program
    val highlight = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    GlassCard(modifier, colors = highlight) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardLabel("Up next", icon = Icons.Filled.Bolt, tint = Color.White)
            when {
                active != null -> {
                    Text(active.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "In progress · ${formatClock(System.currentTimeMillis() - active.startTime)} · ${active.completedSetCount()} sets done",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                    if (wide) Spacer(Modifier.height(6.dp)) else Spacer(Modifier.weight(1f))
                    GradientButton(onClick = { nav.navigate(Routes.ACTIVE) }, modifier = Modifier.fillMaxWidth(), height = if (wide) 52.dp else 44.dp) {
                        Text("Resume workout")
                    }
                }
                program != null && program.days.isNotEmpty() -> {
                    val index = program.nextDayIndex
                    val day = program.days[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (program.isDeload && wide) {
                            Spacer(Modifier.width(8.dp))
                            Pill("Deload", Warmup)
                        }
                    }
                    Text(
                        if (wide) "${program.name} · week ${program.mesoWeek} · ${day.exercises.size} exercises · ${day.exercises.sumOf { it.sets }} sets"
                        else "${day.exercises.size} exercises",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (wide) {
                        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            day.exercises.take(6).forEach { ExerciseThumbnail(Repo.exercise(it.exerciseId), 36.dp) }
                            if (day.exercises.size > 6) Text("+${day.exercises.size - 6}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    GradientButton(
                        onClick = {
                            Repo.startProgramWorkout(index)
                            nav.navigate(Routes.ACTIVE)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        height = if (wide) 52.dp else 44.dp,
                    ) { Text(if (wide) "Start ${day.name}" else "Start") }
                }
                else -> {
                    Text("Ready when you are", style = MaterialTheme.typography.headlineSmall)
                    if (wide) {
                        Text(
                            "Start an empty workout, pick a routine, or let TooHeavy build a program that adapts after every session.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton(
                            onClick = {
                                Repo.startWorkout()
                                nav.navigate(Routes.ACTIVE)
                            },
                            modifier = Modifier.weight(1f),
                            height = if (wide) 52.dp else 44.dp,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        ) { Text(if (wide) "Empty workout" else "Start") }
                        if (wide) {
                            OutlinedButton(
                                onClick = { nav.navigate(Routes.PROGRAM_SETUP) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) { Text("Build a program") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Streak(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val workouts = Repo.workouts.toList()
    val streak = weekStreak(workouts)
    val byDate = workoutsByDate(workouts)
    val t = today()
    val monday = shiftDate(t, -((dayOfWeek(t) + 5) % 7))
    val days = (0..6).map { shiftDate(monday, it) }
    val thisWeek = days.sumOf { byDate[it] ?: 0 }
    WidgetCard(def, modifier) {
        BigNumber("$streak", if (streak == 1) "week" else "weeks", size = if (wide) 34 else 30, brush = BrandBrush)
        Text(
            "$thisWeek workout${if (thisWeek == 1) "" else "s"} this week",
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                val trained = (byDate[d] ?: 0) > 0
                val isToday = d == t
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val dot = if (wide) 26.dp else 17.dp
                    Box(
                        Modifier
                            .size(dot)
                            .clip(CircleShape)
                            .then(
                                when {
                                    trained -> Modifier.background(BrandBrush)
                                    d > t -> Modifier.background(Surface3.copy(alpha = 0.5f))
                                    else -> Modifier.background(Surface3)
                                },
                            )
                            .then(if (isToday && !trained) Modifier.border(1.5.dp, Ember, CircleShape) else Modifier),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("MTWTFSS"[i].toString(), style = MaterialTheme.typography.labelSmall, color = if (isToday) MaterialTheme.colorScheme.onSurface else TextMuted)
                }
            }
        }
    }
}

@Composable
private fun CaloriesToday(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = today()
    val totals = NutritionRepo.totals(t)
    val goals = NutritionRepo.goals
    val remaining = goals.kcal - totals.kcal
    val progress = if (goals.kcal > 0) (totals.kcal / goals.kcal).toFloat() else 0f
    WidgetCard(def, modifier, onClick = onClick) {
        @Composable
        fun Ring(size: Dp) {
            ProgressRing(progress, size = size, thickness = if (wide) 12.dp else 10.dp, over = remaining < 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BigNumber(kcal(abs(remaining)), size = if (wide) 26 else 20)
                    Text(if (remaining < 0) "over" else "left", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(120.dp)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroMeter("Protein", totals.protein, goals.protein, ChartColors.Protein)
                    MacroMeter("Carbs", totals.carbs, goals.carbs, ChartColors.Carbs)
                    MacroMeter("Fat", totals.fat, goals.fat, ChartColors.Fat)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Ring(112.dp) }
        }
    }
}

@Composable
private fun MacroMeter(name: String, value: Double, goal: Int, color: Color) {
    Column {
        Row {
            Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} / $goal g", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Meter(if (goal > 0) (value / goal).toFloat() else 0f, color, height = 6.dp)
    }
}

@Composable
private fun MacrosToday(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val totals = NutritionRepo.totals(today())
    val goals = NutritionRepo.goals
    val kcals = listOf(totals.protein * 4, totals.carbs * 4, totals.fat * 9)
    val colors = listOf(ChartColors.Protein, ChartColors.Carbs, ChartColors.Fat)
    val grams = listOf(totals.protein, totals.carbs, totals.fat)
    WidgetCard(def, modifier, onClick = onClick) {
        @Composable
        fun Donut(size: Dp) {
            DonutChart(kcals, colors, size = size, thickness = if (wide) 14.dp else 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BigNumber(kcal(totals.kcal), size = if (wide) 22 else 17)
                    Text("kcal", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Donut(120.dp)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroMeter("Protein", totals.protein, goals.protein, ChartColors.Protein)
                    MacroMeter("Carbs", totals.carbs, goals.carbs, ChartColors.Carbs)
                    MacroMeter("Fat", totals.fat, goals.fat, ChartColors.Fat)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Donut(96.dp) }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("P", "C", "F").forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(colors[i]))
                        Spacer(Modifier.width(4.dp))
                        Text("$n ${grams[i].roundToInt()}g", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyVolume(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val unit = Repo.settings.unit
    val weeks = weeklyBuckets(Repo.workouts.toList(), 8)
    val vols = weeks.map { b -> b.workouts.sumOf { it.volume() } }
    val now = vols.last()
    WidgetCard(def, modifier) {
        if (wide) {
            BigNumber(formatVolume(now), unit, size = 28)
            Text(pctChange(now, vols[vols.lastIndex - 1]) ?: "This week", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(14.dp))
            BarChart(vols, weeks.map { it.label }, height = 150.dp, tooltipLabels = weeks.map { "Week of ${it.label}" })
        } else {
            HalfStat(compact(now), unit, "This week", vols)
        }
    }
}

@Composable
private fun WeeklyWorkouts(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val weeks = weeklyBuckets(Repo.workouts.toList(), 12)
    val counts = weeks.map { it.workouts.size.toDouble() }
    WidgetCard(def, modifier) {
        val avg = counts.dropLast(1).takeLast(4).average()
        if (wide) {
            BigNumber("${counts.last().toInt()}", "this week", size = 28)
            Text("4-week average ${String.format(Locale.US, "%.1f", avg)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(14.dp))
            BarChart(counts, weeks.map { it.label }, height = 140.dp, tooltipLabels = weeks.map { "Week of ${it.label}" })
        } else {
            HalfStat("${counts.last().toInt()}", "this week", "Avg ${String.format(Locale.US, "%.1f", avg)} / week", counts)
        }
    }
}

@Composable
private fun Consistency(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val byDate = workoutsByDate(Repo.workouts.toList())
    WidgetCard(def, modifier) {
        ActivityHeatmap(byDate, weeks = if (wide) 17 else 7)
    }
}

@Composable
private fun BodyWeight(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val trend = NutritionRepo.trend().filter { it.date >= shiftDate(today(), -90) }
    val last = trend.lastOrNull()
    val weekAgo = trend.lastOrNull { it.date <= shiftDate(today(), -7) }
    val change = if (last != null && weekAgo != null) last.trendKg - weekAgo.trendKg else null
    val changeText = change?.let { (if (it >= 0) "+" else "") + String.format(Locale.US, "%.1f kg in 7 days", it) }
    WidgetCard(def, modifier, onClick = onClick) {
        if (last == null) {
            Text("No weigh-ins yet", style = MaterialTheme.typography.titleMedium)
            Text("Log your weight on the coach page to see the trend.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            return@WidgetCard
        }
        if (wide) {
            BigNumber(String.format(Locale.US, "%.1f", last.trendKg), "kg trend", size = 28)
            Text(changeText ?: "Since ${shortDateLabel(trend.first().date)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(14.dp))
            TrendChart(trend, height = 160.dp)
        } else {
            HalfStat(String.format(Locale.US, "%.1f", last.trendKg), "kg", changeText ?: "Trend weight", trend.map { it.trendKg }, ChartColors.Fat)
        }
    }
}

@Composable
private fun CaloriesWeek(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val t = today()
    val days = (6 downTo 0).map { shiftDate(t, -it) }
    val kcals = days.map { NutritionRepo.totals(it).kcal }
    val goal = NutritionRepo.goals.kcal.toDouble()
    val logged = kcals.filter { it > 0 }
    val avg = if (logged.isEmpty()) 0.0 else logged.average()
    WidgetCard(def, modifier) {
        if (wide) {
            BigNumber(kcal(avg), "kcal / day", size = 28)
            Text("Average of ${logged.size} logged days · goal ${kcal(goal)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(14.dp))
            BarChart(kcals, days.map { weekday(it) }, height = 150.dp, goal = goal, tooltipLabels = days.map { shortDateLabel(it) })
        } else {
            HalfStat(compact(avg), "kcal/day", "7-day average", kcals, ChartColors.series[1])
        }
    }
}

@Composable
private fun MacroSplit(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val t = today()
    val days = (6 downTo 0).map { shiftDate(t, -it) }
    val totals = days.map { NutritionRepo.totals(it) }
    val stacks = totals.map { listOf(it.protein * 4, it.carbs * 4, it.fat * 9) }
    val logged = totals.filter { it.kcal > 0 }
    val protein = if (logged.isEmpty()) 0.0 else logged.map { it.protein }.average()
    val goalP = NutritionRepo.goals.protein
    WidgetCard(def, modifier) {
        if (wide) {
            StackedBarChart(
                stacks, days.map { weekday(it) },
                listOf(ChartColors.Protein, ChartColors.Carbs, ChartColors.Fat), listOf("Protein", "Carbs", "Fat"),
                height = 160.dp, goal = NutritionRepo.goals.kcal.toDouble(), tooltipLabels = days.map { shortDateLabel(it) },
            )
        } else {
            BigNumber("${protein.roundToInt()}", "g protein", size = 26)
            Text("Daily average · goal $goalP g", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.weight(1f))
            Meter(if (goalP > 0) (protein / goalP).toFloat() else 0f, ChartColors.Protein)
            Spacer(Modifier.height(8.dp))
            Sparkline(totals.map { it.protein }, Modifier.fillMaxWidth().height(36.dp), ChartColors.Protein)
        }
    }
}

@Composable
private fun Muscles(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val sets = muscleSets(Repo.workouts.toList(), System.currentTimeMillis() - 30L * 86_400_000L, Repo::exercise)
    WidgetCard(def, modifier) {
        if (sets.isEmpty()) {
            EmptyChartNote("No sets in the last 30 days", height = 80.dp)
        } else {
            RankedBars(
                sets.take(if (wide) 7 else 3).map { it.first.titleCase() to it.second.toDouble() },
                valueFormat = { "${it.toInt()} sets" },
            )
        }
    }
}

@Composable
private fun Records(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier, nav: NavController) {
    val unit = Repo.settings.unit
    val prs = personalRecords(Repo.workouts.toList()).filter { it.best1RM != null }.take(if (wide) 5 else 3)
    WidgetCard(def, modifier) {
        if (prs.isEmpty()) {
            EmptyChartNote("Finish a workout to set records", height = 80.dp)
            return@WidgetCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (wide) 10.dp else 6.dp)) {
            prs.forEachIndexed { i, pr ->
                val ex = Repo.exercise(pr.exerciseId)
                Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .then(if (wide) Modifier.clickable { nav.navigate(Routes.exercise(pr.exerciseId)) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (wide) {
                        ExerciseThumbnail(ex, 38.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(ex?.name ?: "Exercise", style = if (wide) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (wide) Text("Best ${formatWeight(pr.bestWeight)} $unit · ${pr.sets} sets", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatWeight(Math.round(pr.best1RM!! * 10) / 10.0),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.tooheavy.app.ui.theme.NumberFont, fontWeight = FontWeight.Bold),
                        color = if (i == 0) Warmup else MaterialTheme.colorScheme.onSurface,
                    )
                    if (wide) Text(" $unit e1RM", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun TopLift(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier, nav: NavController) {
    val unit = Repo.settings.unit
    val workouts = Repo.workouts.toList()
    val top = personalRecords(workouts).firstOrNull { it.best1RM != null }
    val series = top?.let { oneRepMaxSeries(workouts, it.exerciseId) }.orEmpty()
    val name = top?.let { Repo.exercise(it.exerciseId)?.name } ?: ""
    WidgetCard(def, modifier, onClick = top?.let { { nav.navigate(Routes.exercise(it.exerciseId)) } }) {
        if (series.isEmpty()) {
            EmptyChartNote("Lift a few sessions to see progress", height = 80.dp)
            return@WidgetCard
        }
        val last = series.last().second
        val first = series.first().second
        val gain = last - first
        if (wide) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BigNumber(formatWeight(Math.round(last * 10) / 10.0), "$unit e1RM", size = 28)
            Text(
                if (series.size > 1) "${if (gain >= 0) "+" else ""}${formatWeight(Math.round(gain * 10) / 10.0)} $unit over ${series.size} sessions" else "First session logged",
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
            )
            Spacer(Modifier.height(14.dp))
            val fmt = SimpleDateFormat("MMM d", Locale.US)
            AreaChart(
                series.mapIndexed { i, (ms, v) -> ChartPoint(i.toFloat(), Math.round(v * 10) / 10.0, fmt.format(Date(ms))) },
                height = 160.dp, color = Success, valueFormat = { formatWeight(Math.round(it * 10) / 10.0) },
            )
        } else {
            Text(name, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HalfStat(formatWeight(Math.round(last * 10) / 10.0), unit, "Estimated 1RM", series.map { it.second }, Success)
        }
    }
}

@Composable
private fun Expenditure(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val e = NutritionRepo.expenditure()
    WidgetCard(def, modifier, onClick = onClick) {
        BigNumber(kcal(e.kcal.toDouble()), "kcal", size = if (wide) 30 else 26)
        Text(
            if (e.dataKcal == null) "Formula estimate · log food & weight to adapt" else "${(e.confidence * 100).roundToInt()} % from your data",
            style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Meter(e.confidence.toFloat(), Ember)
        if (wide) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${e.loggedDays} logged days · ${e.weighIns} weigh-ins · formula ${kcal(e.formulaKcal.toDouble())} kcal",
                style = MaterialTheme.typography.labelMedium, color = TextSecondary,
            )
        }
    }
}

@Composable
private fun Recent(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier, nav: NavController) {
    val unit = Repo.settings.unit
    val recent = Repo.workouts.take(if (wide) 3 else 1)
    val fmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    WidgetCard(def, modifier) {
        if (recent.isEmpty()) {
            EmptyChartNote("No workouts yet", height = 80.dp)
            return@WidgetCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            recent.forEach { w ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate(Routes.workoutDetail(w.id)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmt.format(Date(w.startTime)), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        if (!wide) {
                            Spacer(Modifier.height(8.dp))
                            Text(formatDuration(w.durationMs()), style = MaterialTheme.typography.labelLarge)
                            Text("${compact(w.volume())} $unit · ${w.completedSetCount()} sets", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                    if (wide) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${compact(w.volume())} $unit", style = MaterialTheme.typography.labelLarge)
                            Text("${formatDuration(w.durationMs())} · ${w.completedSetCount()} sets", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Lifetime(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val unit = Repo.settings.unit
    val ws = Repo.workouts.toList()
    val stats = listOf(
        "Workouts" to "${ws.size}",
        "Volume $unit" to compact(ws.sumOf { it.volume() }),
        "Sets" to compact(ws.sumOf { it.completedSetCount() }.toDouble()),
        "Time" to formatDuration(ws.sumOf { it.durationMs() }),
    )
    WidgetCard(def, modifier) {
        val rows = stats.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            BigNumber(value, size = if (wide) 26 else 20)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingTime(def: DashboardWidgets.Def, wide: Boolean, modifier: Modifier) {
    val weeks = weeklyBuckets(Repo.workouts.toList(), 8)
    val minutes = weeks.map { b -> b.workouts.sumOf { it.durationMs() } / 60_000.0 }
    WidgetCard(def, modifier) {
        if (wide) {
            BigNumber("${minutes.last().roundToInt()}", "min this week", size = 28)
            Text(pctChange(minutes.last(), minutes[minutes.lastIndex - 1]) ?: "Minutes per week", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(14.dp))
            BarChart(minutes, weeks.map { it.label }, height = 140.dp, color = ChartColors.Fat, valueFormat = { "${it.roundToInt()}" }, tooltipLabels = weeks.map { "Week of ${it.label}" })
        } else {
            HalfStat("${minutes.last().roundToInt()}", "min", "This week", minutes, ChartColors.Fat)
        }
    }
}

private fun weekday(date: String): String =
    runCatching { SimpleDateFormat("EEE", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!).take(2) }.getOrDefault("")
