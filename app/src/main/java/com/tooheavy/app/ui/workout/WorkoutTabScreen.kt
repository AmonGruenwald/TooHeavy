package com.tooheavy.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.Routine
import com.tooheavy.app.data.RoutineExercise
import com.tooheavy.app.data.newId
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.theme.Danger

private class PendingStart(val routine: Routine?)

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

    fun requestStart(routine: Routine?) {
        if (Repo.activeWorkout != null) {
            pending = PendingStart(routine)
        } else {
            Repo.startWorkout(routine)
            nav.navigate(Routes.ACTIVE)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Workout", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        val routines = Repo.routines.toList()
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "quick_header") {
                Text(
                    "Quick Start",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item(key = "quick_button") {
                Button(
                    onClick = { requestStart(null) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Empty Workout", fontWeight = FontWeight.SemiBold)
                }
            }
            item(key = "routines_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Routines",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { nav.navigate(Routes.routineEdit("new")) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("New Routine")
                    }
                }
            }
            if (routines.isEmpty()) {
                item(key = "empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                        Repo.startWorkout(p.routine)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Start Routine", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
