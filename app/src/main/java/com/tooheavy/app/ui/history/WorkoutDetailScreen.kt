package com.tooheavy.app.ui.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.SetEntry
import com.tooheavy.app.data.SetType
import com.tooheavy.app.data.Workout
import com.tooheavy.app.data.WorkoutExercise
import com.tooheavy.app.data.completedSetCount
import com.tooheavy.app.data.durationMs
import com.tooheavy.app.data.estimated1RM
import com.tooheavy.app.data.formatDuration
import com.tooheavy.app.data.formatVolume
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.muscleSets
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.data.volume
import com.tooheavy.app.data.volumeByExercise
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.RankedBars
import com.tooheavy.app.ui.common.compact
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.ChartColors
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Overline
import com.tooheavy.app.ui.theme.StatNumber
import com.tooheavy.app.ui.theme.Warmup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(nav: NavController, workoutId: String) {
    val context = LocalContext.current
    val w = Repo.workout(workoutId)
    val unit = Repo.settings.unit
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(w?.name ?: "Workout", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (w != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Save as routine") },
                                    onClick = {
                                        menuOpen = false
                                        Repo.saveRoutine(Repo.routineFromWorkout(w))
                                        Toast.makeText(context, "Routine saved", Toast.LENGTH_SHORT).show()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Repeat workout") },
                                    onClick = {
                                        menuOpen = false
                                        if (Repo.activeWorkout == null) {
                                            Repo.startWorkout(Repo.routineFromWorkout(w))
                                            nav.navigate(Routes.ACTIVE)
                                        } else {
                                            Toast.makeText(context, "Finish your current workout first", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete workout", color = Danger) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Workout not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { DetailSummary(w, unit) }
                item { DetailCharts(w, unit) }
                items(w.exercises) { we -> DetailExerciseCard(nav, we, unit) }
            }
        }
    }

    if (confirmDelete && w != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("This workout will be permanently removed from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteWorkout(w.id)
                    nav.popBackStack()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailSummary(w: Workout, unit: String) {
    val dateFmt = remember { SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            CardLabel("Workout summary", icon = Icons.Filled.EmojiEvents, tint = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                dateFmt.format(Date(w.startTime)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                DetailStat("Duration", formatDuration(w.durationMs()), Modifier.weight(1f))
                DetailStat("Volume", "${formatVolume(w.volume())} $unit", Modifier.weight(1f))
                DetailStat("Sets", w.completedSetCount().toString(), Modifier.weight(1f))
            }
            if (w.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(w.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = Overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = StatNumber.copy(fontSize = 22.sp), maxLines = 1)
    }
}

@Composable
private fun DetailCharts(w: Workout, unit: String) {
    val byExercise = w.volumeByExercise().filter { it.second > 0 }
    val muscles = muscleSets(listOf(w), 0L, Repo::exercise)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (byExercise.isNotEmpty()) {
            ChartCard("Volume by exercise", icon = Icons.Filled.BarChart, tint = AccentBright) {
                RankedBars(
                    byExercise.map { (id, v) -> (Repo.exercise(id)?.name ?: "Exercise") to v },
                    valueFormat = { "${compact(it)} $unit" },
                )
            }
        }
        if (muscles.isNotEmpty()) {
            ChartCard("Muscles worked", icon = Icons.Filled.AccessibilityNew, tint = ChartColors.series[4]) {
                RankedBars(
                    muscles.map { it.first.titleCase() to it.second.toDouble() },
                    color = ChartColors.series[4],
                    valueFormat = { "${it.toInt()} sets" },
                )
            }
        }
    }
}

private fun detailSetLabels(sets: List<SetEntry>): List<String> {
    var working = 0
    return sets.map { s ->
        when (s.type) {
            SetType.WARMUP -> "W"
            SetType.DROP -> "D"
            SetType.FAILURE -> "F"
            else -> {
                working += 1
                working.toString()
            }
        }
    }
}

@Composable
private fun DetailExerciseCard(nav: NavController, we: WorkoutExercise, unit: String) {
    val ex = Repo.exercise(we.exerciseId)
    val labels = detailSetLabels(we.sets)
    val bestId: String? = we.sets
        .filter { it.type != SetType.WARMUP && it.estimated1RM() != null }
        .maxByOrNull { it.estimated1RM() ?: 0.0 }
        ?.id

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { nav.navigate(Routes.exercise(we.exerciseId)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExerciseThumbnail(ex, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    ex?.name ?: "Unknown exercise",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentBright,
                    modifier = Modifier.weight(1f),
                )
            }
            if (we.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(we.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text("SET", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("WEIGHT × REPS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("1RM", Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(4.dp))
            we.sets.forEachIndexed { i, s ->
                val isBest = s.id == bestId
                val weightText = if (s.weight != null) "${formatWeight(s.weight)} $unit" else "—"
                val repsText = s.reps?.toString() ?: "—"
                val rm = s.estimated1RM()
                val rmText = if (rm != null) formatWeight(Math.round(rm * 10) / 10.0) else "—"
                val labelColor = when (s.type) {
                    SetType.WARMUP -> Warmup
                    SetType.DROP, SetType.FAILURE -> Danger
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (isBest) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(labels[i], Modifier.width(40.dp), color = labelColor, fontWeight = FontWeight.Bold)
                    Text("$weightText × $repsText", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.width(80.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isBest) {
                            Icon(
                                Icons.Filled.EmojiEvents,
                                contentDescription = "Best set",
                                modifier = Modifier.size(14.dp),
                                tint = Warmup,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(rmText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
