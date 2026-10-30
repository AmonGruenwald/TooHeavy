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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.tooheavy.app.data.Updater
import com.tooheavy.app.data.completedSetCount
import com.tooheavy.app.data.durationMs
import com.tooheavy.app.data.formatDuration
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.muscleSets
import com.tooheavy.app.data.personalRecords
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.data.volume
import com.tooheavy.app.data.weekStreak
import com.tooheavy.app.data.weeklyBuckets
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.BrandMark
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.EmptyChartNote
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.RankedBars
import com.tooheavy.app.ui.common.StatTile
import com.tooheavy.app.ui.common.compact
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.BrandBrush
import com.tooheavy.app.ui.theme.ChartColors
import com.tooheavy.app.ui.theme.Ember
import com.tooheavy.app.ui.theme.NumberFont
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.Warmup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: NavController) {
    val context = LocalContext.current
    val unit = Repo.settings.unit
    val workouts = Repo.workouts.toList()
    val weeks = weeklyBuckets(workouts, 8)
    val streak = weekStreak(workouts)
    val topMuscles = muscleSets(workouts, System.currentTimeMillis() - 30L * 86_400_000L, Repo::exercise).take(8)
    val prs = personalRecords(workouts).take(10)

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Accent,
        selectedLabelColor = Color.White,
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text("Profile") },
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
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 60.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your training", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            workouts.lastOrNull()?.let { "Lifting since ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it.startTime))}" }
                                ?: "Your first workout is waiting",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$streak", style = MaterialTheme.typography.displaySmall.copy(brush = BrandBrush))
                        Text("week streak", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Workouts", workouts.size.toString(), Modifier.weight(1f), icon = Icons.Filled.FitnessCenter, tint = AccentBright)
                StatTile("Volume", compact(workouts.sumOf { it.volume() }), Modifier.weight(1f), unit = unit, icon = Icons.Filled.BarChart, tint = Ember)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sets", compact(workouts.sumOf { it.completedSetCount() }.toDouble()), Modifier.weight(1f), icon = Icons.Filled.Repeat, tint = Success)
                StatTile("Total time", formatDuration(workouts.sumOf { it.durationMs() }), Modifier.weight(1f), icon = Icons.Filled.Timer, tint = ChartColors.Protein)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("This week", weeks.last().workouts.size.toString(), Modifier.weight(1f), icon = Icons.Filled.Today, tint = AccentBright)
                StatTile(if (streak == 1) "Week streak" else "Weeks streak", streak.toString(), Modifier.weight(1f), icon = Icons.Filled.LocalFireDepartment, tint = Ember)
            }

            ChartCard("Workouts per week", icon = Icons.Filled.Insights, tint = AccentBright) {
                BarChart(weeks.map { it.workouts.size.toDouble() }, weeks.map { it.label }, height = 140.dp, tooltipLabels = weeks.map { "Week of ${it.label}" })
            }

            ChartCard("Volume per week", unit = null, caption = "Total weight × reps in $unit", icon = Icons.Filled.BarChart, tint = Ember) {
                BarChart(weeks.map { b -> b.workouts.sumOf { it.volume() } }, weeks.map { it.label }, height = 150.dp, tooltipLabels = weeks.map { "Week of ${it.label}" })
            }

            ChartCard("Muscle focus · last 30 days", icon = Icons.Filled.AccessibilityNew, tint = ChartColors.series[4]) {
                if (topMuscles.isEmpty()) {
                    EmptyChartNote("No data in the last 30 days.", height = 60.dp)
                } else {
                    RankedBars(topMuscles.map { it.first.titleCase() to it.second.toDouble() }, valueFormat = { "${it.toInt()} sets" })
                }
            }

            ProfSection("Personal records", Icons.Filled.EmojiEvents, Warmup) {
                if (prs.isEmpty()) {
                    Text("Complete a workout to see your records.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    prs.forEach { pr ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { nav.navigate(Routes.exercise(pr.exerciseId)) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseThumbnail(Repo.exercise(pr.exerciseId), 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Repo.exercise(pr.exerciseId)?.name ?: "Unknown exercise",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (pr.bestWeight != null) "Best ${formatWeight(pr.bestWeight)} $unit · ${pr.sets} sets" else "${pr.sets} sets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (pr.best1RM != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatWeight(Math.round(pr.best1RM * 10) / 10.0),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = NumberFont, fontWeight = FontWeight.Bold),
                                    )
                                    Text("$unit e1RM", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
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
                Spacer(Modifier.height(16.dp))
                Text("Home dashboard", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { Repo.updateDashboard(null) }, modifier = Modifier.padding(start = 0.dp)) {
                    Text("Reset widgets to the default layout")
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
        }
    }
}

@Composable
private fun ProfSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            CardLabel(title, icon = icon, tint = tint)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
