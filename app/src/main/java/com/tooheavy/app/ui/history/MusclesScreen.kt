package com.tooheavy.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.MuscleGroup
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.TrainingGoal
import com.tooheavy.app.data.Experience
import com.tooheavy.app.data.addWeeks
import com.tooheavy.app.data.groupCredit
import com.tooheavy.app.data.muscleCredit
import com.tooheavy.app.data.weekStart
import com.tooheavy.app.data.weeklyBuckets
import com.tooheavy.app.data.weeklyGroupCredit
import com.tooheavy.app.data.weeklySetRange
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.BodyMap
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.EmptyChartNote
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.common.SegmentedTabs
import com.tooheavy.app.ui.common.formatSets
import com.tooheavy.app.ui.common.muscleLevels
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.Warmup

private val periods = listOf("This week" to 1, "4 weeks" to 4, "12 weeks" to 12)

/** Sets per muscle group per week over a chosen period, against the range research suggests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusclesScreen(nav: NavController) {
    var period by rememberSaveable { mutableIntStateOf(1) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val workouts = Repo.workouts.toList()
    val weeks = periods[period].second
    val now = System.currentTimeMillis()
    val from = addWeeks(weekStart(now), -(weeks - 1))
    val inPeriod = workouts.filter { it.startTime >= from }.flatMap { it.exercises }
    val perWeek = remember(workouts, weeks) {
        groupCredit(inPeriod, Repo::exercise).mapValues { it.value / weeks }
    }
    val muscles = remember(workouts, weeks) {
        muscleCredit(inPeriod, Repo::exercise).mapValues { it.value / weeks }
    }
    val program = Repo.program
    fun range(g: String) = weeklySetRange(
        program?.goal ?: TrainingGoal.HYPERTROPHY,
        program?.experience ?: Experience.INTERMEDIATE,
        program?.focus?.contains(g) == true,
        g,
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text("Muscles") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedTabs(periods.map { it.first }, period, { period = it })

            ChartCard(
                if (weeks == 1) "Sets this week" else "Sets per week · average",
                icon = Icons.Filled.AccessibilityNew,
                caption = if (weeks == 1) "Monday until now" else null,
            ) {
                if (perWeek.isEmpty()) {
                    EmptyChartNote("No workouts in this period.", height = 80.dp)
                } else {
                    // Full gold at the top of the usual weekly range.
                    BodyMap(muscleLevels(muscles, full = 20.0), Modifier.fillMaxWidth())
                }
            }

            ChartCard("Weekly sets per muscle group", icon = Icons.Filled.Insights) {
                Text(
                    "Most lifters grow on about 10 to 20 hard sets per muscle a week" +
                        (if (program != null) "; the marked range is your program's target." else ".") +
                        " Sets that also work a muscle count half. Tap a group for its trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MuscleGroup.all.forEach { g ->
                        val v = perWeek[g] ?: 0.0
                        val r = range(g)
                        val color = when {
                            v < r.start - 0.01 -> Warmup
                            v > r.endInclusive + 0.01 -> Danger
                            else -> AccentBright
                        }
                        val status = when {
                            v <= 0.0 -> "Not trained"
                            v < r.start - 0.01 -> "Below ${r.start.toInt()}"
                            v > r.endInclusive + 0.01 -> "Above ${r.endInclusive.toInt()}"
                            else -> "In range"
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selected = if (selected == g) null else g }
                                .padding(vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    MuscleGroup.label(g),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected == g) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${formatSets(Math.round(v * 2) / 2.0)} sets · $status",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = color,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Meter((v / (r.endInclusive * 1.25)).toFloat().coerceIn(0f, 1f), color, height = 6.dp)
                        }
                    }
                }
            }

            val group = selected ?: perWeek.maxByOrNull { it.value }?.key
            if (group != null) {
                val buckets = weeklyBuckets(workouts, 12, now)
                val series = weeklyGroupCredit(workouts, group, 12, Repo::exercise, now)
                ChartCard(
                    "${MuscleGroup.label(group)} · last 12 weeks",
                    icon = Icons.Filled.Insights,
                    caption = "Sets per week; the line marks the start of the target range",
                ) {
                    BarChart(
                        series,
                        buckets.map { it.label },
                        height = 150.dp,
                        goal = range(group).start,
                        valueFormat = ::formatSets,
                        tooltipLabels = buckets.map { "Week of ${it.label}" },
                    )
                }
            }
        }
    }
}
