package com.tooheavy.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.BuildConfig
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.SetType
import com.tooheavy.app.data.Updater
import com.tooheavy.app.data.Workout
import com.tooheavy.app.data.completedSetCount
import com.tooheavy.app.data.durationMs
import com.tooheavy.app.data.estimated1RM
import com.tooheavy.app.data.formatDuration
import com.tooheavy.app.data.formatVolume
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.data.volume
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.theme.Accent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Local midnight of the Monday of the week containing [ms]. */
private fun profWeekStart(ms: Long): Long {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    // DAY_OF_WEEK: SUNDAY=1 .. SATURDAY=7 -> days since Monday
    val diff = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    c.add(Calendar.DAY_OF_YEAR, -diff)
    return c.timeInMillis
}

private fun profAddWeeks(ms: Long, n: Int): Long {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    c.timeInMillis = ms
    c.add(Calendar.WEEK_OF_YEAR, n)
    return c.timeInMillis
}

/** Consecutive weeks (Mon-Sun) with at least one workout, ending this week or last week. */
private fun profStreak(workouts: List<Workout>, thisWeek: Long): Int {
    fun has(n: Int): Boolean {
        val lo = profAddWeeks(thisWeek, -n)
        val hi = profAddWeeks(thisWeek, -n + 1)
        return workouts.any { it.startTime >= lo && it.startTime < hi }
    }
    var n = if (has(0)) 0 else 1
    var streak = 0
    while (n < 2000 && has(n)) {
        streak++
        n++
    }
    return streak
}

private class ProfPR(
    val id: String,
    val name: String,
    val sets: Int,
    val bestWeight: Double?,
    val best1RM: Double?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: NavController) {
    val context = LocalContext.current
    val unit = Repo.settings.unit
    val workouts = Repo.workouts.toList()
    val now = System.currentTimeMillis()
    val labelFmt = remember { SimpleDateFormat("M/d", Locale.getDefault()) }

    // ---- week math ----
    val thisWeek = profWeekStart(now)
    val bounds = (-7..1).map { profAddWeeks(thisWeek, it) } // 9 boundaries -> 8 weeks
    val weekWorkouts = (0 until 8).map { i -> workouts.filter { it.startTime >= bounds[i] && it.startTime < bounds[i + 1] } }
    val weekCounts = weekWorkouts.map { it.size.toDouble() }
    val weekVolumes = weekWorkouts.map { ws -> ws.sumOf { it.volume() } }
    val weekLabels = (0 until 8).map { labelFmt.format(Date(bounds[it])) }
    val thisWeekCount = weekWorkouts.last().size
    val streak = profStreak(workouts, thisWeek)

    // ---- totals ----
    val totalVolume = workouts.sumOf { it.volume() }
    val totalSets = workouts.sumOf { it.completedSetCount() }
    val totalTime = workouts.sumOf { it.durationMs() }

    // ---- muscle distribution (last 30 days) ----
    val cutoff = now - 30L * 24L * 3600L * 1000L
    val muscleSets = HashMap<String, Int>()
    workouts.filter { it.startTime >= cutoff }.forEach { w ->
        w.exercises.forEach { we ->
            val n = we.sets.count { it.completed }
            Repo.exercise(we.exerciseId)?.primaryMuscles?.forEach { m ->
                muscleSets[m] = (muscleSets[m] ?: 0) + n
            }
        }
    }
    val topMuscles = muscleSets.toList().sortedByDescending { it.second }.take(8)
    val maxMuscle = (topMuscles.firstOrNull()?.second ?: 1).coerceAtLeast(1)

    // ---- personal records for the 10 most-trained exercises ----
    val prs = workouts.flatMap { it.exercises }
        .groupBy { it.exerciseId }
        .map { (id, entries) ->
            val sets = entries.flatMap { it.sets }.filter { it.completed && it.type != SetType.WARMUP }
            ProfPR(
                id = id,
                name = Repo.exercise(id)?.name ?: "Unknown exercise",
                sets = sets.size,
                bestWeight = sets.mapNotNull { it.weight }.filter { it > 0 }.maxOrNull(),
                best1RM = sets.mapNotNull { it.estimated1RM() }.maxOrNull(),
            )
        }
        .sortedByDescending { it.sets }
        .take(10)

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Accent,
        selectedLabelColor = Color.White,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Profile", fontWeight = FontWeight.Bold) }) },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Stats grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfStatCard("Workouts", workouts.size.toString(), Modifier.weight(1f))
                ProfStatCard("Volume ($unit)", formatVolume(totalVolume), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfStatCard("Sets", totalSets.toString(), Modifier.weight(1f))
                ProfStatCard("Total time", formatDuration(totalTime), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfStatCard("This week", thisWeekCount.toString(), Modifier.weight(1f))
                ProfStatCard(
                    if (streak == 1) "Week streak" else "Weeks streak",
                    streak.toString(),
                    Modifier.weight(1f),
                )
            }

            ProfSection("Workouts per week") {
                BarChart(weekCounts, weekLabels)
            }

            ProfSection("Volume per week ($unit)") {
                BarChart(weekVolumes, weekLabels)
            }

            ProfSection("Muscle distribution (last 30 days)") {
                if (topMuscles.isEmpty()) {
                    Text("No data in the last 30 days.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    topMuscles.forEach { (muscle, n) ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(muscle.titleCase(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "$n sets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { n.toFloat() / maxMuscle.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }
            }

            ProfSection("Personal records") {
                if (prs.isEmpty()) {
                    Text("Complete a workout to see your records.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    prs.forEach { pr ->
                        val parts = mutableListOf<String>()
                        parts.add(if (pr.bestWeight != null) "Best ${formatWeight(pr.bestWeight)} $unit" else "Best —")
                        if (pr.best1RM != null) parts.add("Est. 1RM ${formatWeight(Math.round(pr.best1RM * 10) / 10.0)} $unit")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { nav.navigate(Routes.exercise(pr.id)) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseThumbnail(Repo.exercise(pr.id), 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pr.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    parts.joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            ProfSection("Settings") {
                Text("Weight unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("kg", "lb").forEach { u ->
                        FilterChip(
                            selected = Repo.settings.unit == u,
                            onClick = { Repo.updateSettings(Repo.settings.copy(unit = u)) },
                            label = { Text(u) },
                            colors = chipColors,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Default rest timer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 30, 60, 90, 120, 180).forEach { s ->
                        FilterChip(
                            selected = Repo.settings.defaultRestSeconds == s,
                            onClick = { Repo.updateSettings(Repo.settings.copy(defaultRestSeconds = s)) },
                            label = { Text(if (s == 0) "Off" else "${s}s") },
                            colors = chipColors,
                        )
                    }
                }
            }

            ProfSection("About") {
                Text(
                    "TooHeavy is an offline workout tracker. All data stays on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Exercise images & instructions: free-exercise-db (public domain, github.com/yuhonas/free-exercise-db)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { Updater.check(context, manual = true) }) { Text("Check for updates") }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
