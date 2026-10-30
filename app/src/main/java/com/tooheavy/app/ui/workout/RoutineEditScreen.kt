package com.tooheavy.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.Routine
import com.tooheavy.app.data.RoutineExercise
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.theme.Danger

private val REST_OPTIONS = listOf(0, 30, 60, 90, 120, 180)

private fun updateDraft(f: (Routine) -> Routine) {
    val d = Repo.routineDraft ?: return
    Repo.routineDraft = f(d)
}

private fun updateExercise(index: Int, f: (RoutineExercise) -> RoutineExercise) {
    updateDraft { d ->
        d.copy(exercises = d.exercises.mapIndexed { i, e -> if (i == index) f(e) else e })
    }
}

private fun moveExercise(index: Int, delta: Int) {
    updateDraft { d ->
        val to = index + delta
        if (index !in d.exercises.indices || to !in d.exercises.indices) {
            d
        } else {
            val list = d.exercises.toMutableList()
            val item = list.removeAt(index)
            list.add(to, item)
            d.copy(exercises = list)
        }
    }
}

private fun removeExercise(index: Int) {
    updateDraft { d -> d.copy(exercises = d.exercises.filterIndexed { i, _ -> i != index }) }
}

private fun restLabel(sec: Int): String = when {
    sec <= 0 -> "Off"
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60}m ${sec % 60}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditScreen(nav: NavController, routineId: String) {
    // Make sure the first frame already has a draft.
    remember(routineId) { Repo.beginRoutineEdit(routineId) }
    LaunchedEffect(routineId) { Repo.beginRoutineEdit(routineId) }

    val draft = Repo.routineDraft

    fun cancel() {
        Repo.routineDraft = null
        nav.popBackStack()
    }

    fun save() {
        val d = Repo.routineDraft ?: return
        if (d.name.isBlank() || d.exercises.isEmpty()) return
        Repo.saveRoutine(d.copy(name = d.name.trim()))
        Repo.routineDraft = null
        nav.popBackStack()
    }

    BackHandler { cancel() }

    val canSave = draft != null && draft.name.isNotBlank() && draft.exercises.isNotEmpty()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(if (routineId == "new") "New Routine" else "Edit Routine") },
                navigationIcon = {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = canSave) {
                        Text("Save")
                    }
                },
            )
        },
    ) { pad ->
        if (draft == null) {
            Box(Modifier.padding(pad).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize().imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "name") {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { v -> updateDraft { it.copy(name = v) } },
                        label = { Text("Routine name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "notes") {
                    OutlinedTextField(
                        value = draft.notes,
                        onValueChange = { v -> updateDraft { it.copy(notes = v) } },
                        label = { Text("Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (draft.exercises.isEmpty()) {
                    item(key = "hint") {
                        Text(
                            "Add at least one exercise to save this routine.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(draft.exercises, key = { i, re -> "$i-${re.exerciseId}" }) { index, re ->
                    RoutineExerciseCard(
                        nav = nav,
                        index = index,
                        count = draft.exercises.size,
                        re = re,
                    )
                }
                item(key = "add") {
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.picker(Routes.PICK_FOR_ROUTINE)) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Exercise", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineExerciseCard(nav: NavController, index: Int, count: Int, re: RoutineExercise) {
    val ex = Repo.exercise(re.exerciseId)
    val unit = Repo.settings.unit
    var repsText by remember { mutableStateOf(re.reps?.toString() ?: "") }
    var weightText by remember { mutableStateOf(formatWeight(re.weight)) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumbnail(ex, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(enabled = ex != null) { nav.navigate(Routes.exercise(re.exerciseId)) },
                ) {
                    Text(
                        ex?.name ?: "Unknown exercise",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val muscle = ex?.primaryMuscles?.firstOrNull()
                    if (muscle != null) {
                        Text(
                            muscle.titleCase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { moveExercise(index, -1) },
                    enabled = index > 0,
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                IconButton(
                    onClick = { moveExercise(index, 1) },
                    enabled = index < count - 1,
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                IconButton(
                    onClick = { removeExercise(index) },
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Danger) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                SetsStepper(
                    value = re.sets,
                    onChange = { v -> updateExercise(index) { it.copy(sets = v) } },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { s ->
                        if (s.length <= 3 && s.all { it.isDigit() }) {
                            repsText = s
                            updateExercise(index) { it.copy(reps = s.toIntOrNull()) }
                        }
                    },
                    label = { Text("Target reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { s ->
                        if (s.length <= 7 && s.all { it.isDigit() || it == '.' } && s.count { it == '.' } <= 1) {
                            weightText = s
                            updateExercise(index) { it.copy(weight = s.replace(',', '.').toDoubleOrNull()) }
                        }
                    },
                    label = { Text("Weight ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                RestPicker(
                    seconds = re.restSeconds,
                    onChange = { v -> updateExercise(index) { it.copy(restSeconds = v) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SetsStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = { onChange((value - 1).coerceAtLeast(1)) },
            modifier = Modifier.size(40.dp),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer sets") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontWeight = FontWeight.SemiBold)
            Text(
                "Sets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onChange((value + 1).coerceAtMost(30)) },
            modifier = Modifier.size(40.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "More sets") }
    }
}

@Composable
private fun RestPicker(seconds: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = (REST_OPTIONS + seconds).distinct().sorted()
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text("Rest: ${restLabel(seconds)}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(restLabel(opt)) },
                    onClick = {
                        expanded = false
                        onChange(opt)
                    },
                )
            }
        }
    }
}
