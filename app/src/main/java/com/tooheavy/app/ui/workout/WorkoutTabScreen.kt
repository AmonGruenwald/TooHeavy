package com.tooheavy.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.ProgramUpdate
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.Routine
import com.tooheavy.app.data.RoutineExercise
import com.tooheavy.app.data.isDeload
import com.tooheavy.app.data.mesoWeek
import com.tooheavy.app.data.mesocycle
import com.tooheavy.app.data.newId
import com.tooheavy.app.data.nextDayIndex
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.IconBubble
import com.tooheavy.app.ui.common.ScreenHeader
import com.tooheavy.app.ui.common.SectionTitle
import com.tooheavy.app.ui.navigateTab
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.BrandColors
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Surface3
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.TextSecondary
import com.tooheavy.app.ui.theme.Warmup

/** A start waiting on the "workout in progress" dialog: a routine, a program day, or an empty workout. */
private class PendingStart(val routine: Routine?, val programDay: Int? = null)

private fun findExerciseByName(name: String): Exercise? {
    val all = Repo.exercises
    return all.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: all.firstOrNull { it.name.contains(name, ignoreCase = true) }
}

private fun addStarterRoutines() {
    val defs = listOf(
        "Push" to listOf(
            "Barbell Bench Press - Medium Grip",
            "Dumbbell Shoulder Press",
            "Incline Dumbbell Press",
            "Triceps Pushdown",
        ),
        "Pull" to listOf(
            "Pullups",
            "Bent Over Barbell Row",
            "Seated Cable Rows",
            "Barbell Curl",
        ),
        "Legs" to listOf(
            "Barbell Squat",
            "Romanian Deadlift",
            "Leg Press",
            "Standing Calf Raises",
        ),
    )
    for ((routineName, names) in defs) {
        val found = names.mapNotNull { findExerciseByName(it) }.distinctBy { it.id }
        if (found.isEmpty()) continue
        Repo.saveRoutine(
            Routine(
                name = routineName,
                exercises = found.map {
                    RoutineExercise(
                        exerciseId = it.id,
                        sets = 3,
                        reps = 10,
                        restSeconds = Repo.settings.defaultRestSeconds,
                    )
                },
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTabScreen(nav: NavController) {
    var pending by remember { mutableStateOf<PendingStart?>(null) }
    var toDelete by remember { mutableStateOf<Routine?>(null) }

    fun requestStart(routine: Routine?, programDay: Int? = null) {
        if (Repo.activeWorkout != null) {
            pending = PendingStart(routine, programDay)
        } else {
            if (programDay != null) Repo.startProgramWorkout(programDay) else Repo.startWorkout(routine)
            nav.navigate(Routes.ACTIVE)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ScreenHeader("Train", overline = "Let's lift") {
                IconButton(onClick = { nav.navigate(Routes.routineEdit("new")) }) {
                    Icon(Icons.Filled.Add, contentDescription = "New routine")
                }
            }
        },
    ) { pad ->
        val routines = Repo.routines.toList()
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Repo.programUpdate?.let { update ->
                item(key = "program_update") {
                    ProgramUpdateCard(update) { Repo.programUpdate = null }
                }
            }
            item(key = "program") {
                ProgramSummaryCard(
                    onCreate = { nav.navigate(Routes.PROGRAM_SETUP) },
                    onOpen = { nav.navigate(Routes.PROGRAM) },
                    onStart = { requestStart(null, it) },
                )
            }
            item(key = "quick_header") {
                SectionTitle("Quick start", Modifier.padding(top = 4.dp))
            }
            item(key = "quick_button") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(
                        "Empty workout", "Log as you go", Icons.Filled.Bolt, BrandColors[1],
                        Modifier.weight(1f),
                    ) { requestStart(null) }
                    QuickTile(
                        "Exercise library", "${Repo.exercises.size} exercises", Icons.AutoMirrored.Filled.MenuBook, AccentBright,
                        Modifier.weight(1f),
                    ) { nav.navigateTab(Routes.EXERCISES) }
                }
            }
            item(key = "routines_header") {
                SectionTitle("Routines", Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { nav.navigate(Routes.routineEdit("new")) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }
            if (routines.isEmpty()) {
                item(key = "empty") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "No routines yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Routines are reusable workout templates. Build one once, then start it with a single tap " +
                                    "and every exercise, set and rest time is ready to go.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = { addStarterRoutines() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Add example routines")
                            }
                        }
                    }
                }
            } else {
                items(routines, key = { it.id }) { r ->
                    RoutineCard(
                        routine = r,
                        onStart = { requestStart(r) },
                        onEdit = { nav.navigate(Routes.routineEdit(r.id)) },
                        onDuplicate = { Repo.saveRoutine(r.copy(id = newId(), name = r.name + " (copy)")) },
                        onDelete = { toDelete = r },
                    )
                }
            }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("You have a workout in progress") },
            text = { Text("Resume it, or discard it and start a new one?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        pending = null
                        nav.navigate(Routes.ACTIVE)
                    }) { Text("Resume") }
                    TextButton(onClick = {
                        pending = null
                        Repo.discardWorkout()
                        if (p.programDay != null) Repo.startProgramWorkout(p.programDay) else Repo.startWorkout(p.routine)
                        nav.navigate(Routes.ACTIVE)
                    }) { Text("Discard & start new", color = Danger) }
                    TextButton(onClick = { pending = null }) { Text("Cancel") }
                }
            },
        )
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("\"${r.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.deleteRoutine(r.id)
                    toDelete = null
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProgramSummaryCard(onCreate: () -> Unit, onOpen: () -> Unit, onStart: (Int) -> Unit) {
    val program = Repo.program
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (program == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Filled.AutoAwesome, Color.White, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Personal program", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Get a plan built for your goal, schedule and equipment. It sets your weights, reps and sets, " +
                        "and adjusts them after every workout based on what you lifted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GradientButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Text("Create program")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        CardLabel("Your program", icon = Icons.Filled.AutoAwesome, tint = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            program.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (program.isDeload) "Deload week · mesocycle ${program.mesocycle}"
                            else "Week ${program.mesoWeek} of ${program.mesoWeeks} · mesocycle ${program.mesocycle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (program.isDeload) Warmup else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onOpen) { Text("View", color = Color.White) }
                }
                MesocycleTimeline(program.mesoWeek, program.mesoWeeks)
                val index = program.nextDayIndex
                val day = program.days.getOrNull(index)
                if (day != null) {
                    Text("Next: ${day.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        day.exercises.mapNotNull { Repo.exercise(it.exerciseId)?.name }.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GradientButton(onClick = { onStart(index) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start ${day.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramUpdateCard(update: ProgramUpdate, onDismiss: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Program updated after ${update.workoutName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (update.changes.isEmpty()) {
                Text("No changes this time.", style = MaterialTheme.typography.bodySmall)
            }
            update.changes.forEach {
                Text("•  $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val preview = routine.exercises
        .mapNotNull { Repo.exercise(it.exerciseId)?.name }
        .joinToString(", ")

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Danger) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (preview.isEmpty()) "No exercises" else preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (routine.exercises.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        routine.exercises.take(4).forEach { re ->
                            ExerciseThumbnail(Repo.exercise(re.exerciseId), 32.dp)
                        }
                        if (routine.exercises.size > 4) {
                            Text(
                                "+${routine.exercises.size - 4}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface3, contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AccentBright, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start routine", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            IconBubble(icon, tint, size = 40.dp)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
        }
    }
}

/** Training weeks of the current mesocycle as segments, then the deload; the current one glows. */
@Composable
private fun MesocycleTimeline(week: Int, weeks: Int) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..weeks + 1) {
                val deload = i == weeks + 1
                Box(
                    Modifier
                        .weight(if (deload) 0.7f else 1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                i < week -> Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.75f)))
                                i == week -> Brush.horizontalGradient(if (deload) listOf(Warmup, Warmup) else BrandColors)
                                else -> Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.14f)))
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Week 1", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("Deload", style = MaterialTheme.typography.labelSmall, color = if (week > weeks) Warmup else TextSecondary)
        }
    }
}
