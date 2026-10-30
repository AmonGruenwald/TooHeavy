@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tooheavy.app.ui.active

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.SetEntry
import com.tooheavy.app.data.SetTarget
import com.tooheavy.app.data.SetType
import com.tooheavy.app.data.Workout
import com.tooheavy.app.data.WorkoutExercise
import com.tooheavy.app.data.completedSetCount
import com.tooheavy.app.data.formatClock
import com.tooheavy.app.data.formatDuration
import com.tooheavy.app.data.formatVolume
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.volume
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.Warmup
import kotlinx.coroutines.delay

private val SetColW = 36.dp
private val CheckColW = 40.dp
private const val PrevWeight = 1.3f

// ---------- state update helpers ----------

private fun updateExercise(weId: String, transform: (WorkoutExercise) -> WorkoutExercise) {
    Repo.updateActive { w ->
        w.copy(exercises = w.exercises.map { if (it.id == weId) transform(it) else it })
    }
}

private fun updateSet(weId: String, setId: String, transform: (SetEntry) -> SetEntry) {
    updateExercise(weId) { we ->
        we.copy(sets = we.sets.map { if (it.id == setId) transform(it) else it })
    }
}

private fun moveExercise(weId: String, delta: Int) {
    Repo.updateActive { w ->
        val i = w.exercises.indexOfFirst { it.id == weId }
        val j = i + delta
        if (i < 0 || j < 0 || j > w.exercises.lastIndex) {
            w
        } else {
            val list = w.exercises.toMutableList()
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
            w.copy(exercises = list)
        }
    }
}

private fun removeExercise(weId: String) {
    Repo.updateActive { w -> w.copy(exercises = w.exercises.filter { it.id != weId }) }
}

private fun parseWeight(t: String): Double? = t.replace(',', '.').toDoubleOrNull()

private fun previousText(p: SetEntry?, unit: String): String {
    if (p == null) return "-"
    val w = p.weight
    val r = p.reps
    return when {
        w != null && r != null -> "${formatWeight(w)} $unit × $r"
        w != null -> "${formatWeight(w)} $unit"
        r != null -> "$r reps"
        else -> "-"
    }
}

private fun vibrateBriefly(context: Context) {
    runCatching {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

// ---------- screen ----------

@Composable
fun ActiveWorkoutScreen(nav: NavController) {
    val workout = Repo.activeWorkout
    val missing = workout == null
    LaunchedEffect(missing) {
        if (missing) nav.popBackStack()
    }
    if (workout == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No workout in progress", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { nav.popBackStack() }) { Text("Back") }
            }
        }
    } else {
        ActiveWorkoutContent(nav, workout)
    }
}

@Composable
private fun ActiveWorkoutContent(nav: NavController, workout: Workout) {
    val unit = Repo.settings.unit
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    var showRename by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column(Modifier.clickable { showRename = true }) {
                        Text(
                            workout.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            formatClock(now - workout.startTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { showFinish = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(end = 8.dp).height(36.dp),
                    ) { Text("Finish", fontWeight = FontWeight.SemiBold) }
                },
            )
        },
        bottomBar = {
            if (Repo.restEndsAt != null) RestTimerBar()
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryItem("Duration", formatDuration(now - workout.startTime), Modifier.weight(1f))
                SummaryItem("Volume", "${formatVolume(workout.volume())} $unit", Modifier.weight(1f))
                SummaryItem("Sets", workout.completedSetCount().toString(), Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(workout.exercises, key = { _, we -> we.id }) { index, we ->
                    ExerciseCard(nav, we, index, workout.exercises.size, unit)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (workout.exercises.isEmpty()) {
                            Text(
                                "Get started by adding an exercise to your workout.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            )
                        }
                        Button(
                            onClick = { nav.navigate(Routes.picker(Routes.PICK_FOR_ACTIVE)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Exercise", fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { showDiscard = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Discard Workout", color = Danger, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        var text by remember { mutableStateOf(workout.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Workout name") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = text.trim().ifEmpty { "Workout" }
                    Repo.updateActive { w -> w.copy(name = n) }
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }

    if (showFinish) {
        val done = workout.completedSetCount()
        if (done == 0) {
            AlertDialog(
                onDismissRequest = { showFinish = false },
                title = { Text("Finish workout?") },
                text = { Text("No sets completed — discard workout?") },
                confirmButton = {
                    TextButton(onClick = {
                        showFinish = false
                        Repo.discardWorkout()
                    }) { Text("Discard", color = Danger) }
                },
                dismissButton = { TextButton(onClick = { showFinish = false }) { Text("Cancel") } },
            )
        } else {
            val hasUnfinished = workout.exercises.any { we -> we.sets.any { !it.completed } }
            AlertDialog(
                onDismissRequest = { showFinish = false },
                title = { Text("Finish workout?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Duration: ${formatDuration(now - workout.startTime)}")
                        Text("Volume: ${formatVolume(workout.volume())} $unit")
                        Text("Sets: $done")
                        if (hasUnfinished) {
                            Text(
                                "Sets that are not completed will be removed.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showFinish = false
                        Repo.finishWorkout()
                    }) { Text("Finish", color = Success) }
                },
                dismissButton = { TextButton(onClick = { showFinish = false }) { Text("Cancel") } },
            )
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("All progress in this workout will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    Repo.discardWorkout()
                }) { Text("Discard", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- rest timer ----------

@Composable
private fun RestTimerBar() {
    val context = LocalContext.current
    val endsAt = Repo.restEndsAt
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            val end = Repo.restEndsAt ?: break
            if (end - now <= 0) {
                Repo.stopRest()
                if (now - end < 3000) vibrateBriefly(context)
                break
            }
            delay(250)
        }
    }
    val remaining = ((endsAt ?: now) - now).coerceAtLeast(0L)
    val total = (Repo.restTotalSeconds * 1000f).coerceAtLeast(1000f)
    val progress = (remaining / total).coerceIn(0f, 1f)

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
                trackColor = MaterialTheme.colorScheme.outline,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Rest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatClock(remaining), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { Repo.adjustRest(-15) }) { Text("-15") }
                TextButton(onClick = { Repo.adjustRest(15) }) { Text("+15") }
                Button(
                    onClick = { Repo.stopRest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text("Skip") }
            }
        }
    }
}

// ---------- exercise card ----------

@Composable
private fun ExerciseCard(nav: NavController, we: WorkoutExercise, index: Int, count: Int, unit: String) {
    val exercise = Repo.exercise(we.exerciseId)
    val previous = remember(we.exerciseId) { Repo.previousSets(we.exerciseId) }
    var menuOpen by remember { mutableStateOf(false) }
    var restDialog by remember { mutableStateOf(false) }
    var notes by remember(we.id) { mutableStateOf(we.notes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumbnail(exercise, 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    exercise?.name ?: "Unknown exercise",
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { nav.navigate(Routes.exercise(we.exerciseId)) },
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            enabled = index > 0,
                            onClick = {
                                menuOpen = false
                                moveExercise(we.id, -1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            enabled = index < count - 1,
                            onClick = {
                                menuOpen = false
                                moveExercise(we.id, 1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (we.restSeconds <= 0) "Rest timer: Off" else "Rest timer: ${we.restSeconds}s") },
                            onClick = {
                                menuOpen = false
                                restDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove exercise", color = Danger) },
                            onClick = {
                                menuOpen = false
                                removeExercise(we.id)
                            },
                        )
                    }
                }
            }

            we.target?.let { t ->
                Spacer(Modifier.height(8.dp))
                ProgramTargetBox(we, t, unit)
            }

            Spacer(Modifier.height(8.dp))
            CompactField(
                value = notes,
                onValueChange = { txt ->
                    notes = txt
                    updateExercise(we.id) { e -> e.copy(notes = txt) }
                },
                placeholder = "Add notes here...",
                modifier = Modifier.fillMaxWidth(),
                center = false,
            )

            Spacer(Modifier.height(8.dp))
            // table header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HeaderCell("SET", Modifier.width(SetColW))
                HeaderCell("PREVIOUS", Modifier.weight(PrevWeight))
                HeaderCell(unit.uppercase(), Modifier.weight(1f))
                HeaderCell("REPS", Modifier.weight(1f))
                Box(Modifier.width(CheckColW), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            we.sets.forEachIndexed { idx, entry ->
                key(entry.id) {
                    val number = we.sets.take(idx + 1).count { it.type != SetType.WARMUP }
                    SetRow(
                        weId = we.id,
                        entry = entry,
                        setNumber = number,
                        previous = previous.getOrNull(idx),
                        unit = unit,
                        restSeconds = we.restSeconds,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    val last = we.sets.lastOrNull()
                    updateExercise(we.id) { e ->
                        e.copy(sets = e.sets + SetEntry(weight = last?.weight, reps = last?.reps))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Set")
            }
        }
    }

    if (restDialog) {
        AlertDialog(
            onDismissRequest = { restDialog = false },
            title = { Text("Rest timer") },
            text = {
                Column {
                    listOf(0, 30, 60, 90, 120, 180).forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    updateExercise(we.id) { e -> e.copy(restSeconds = s) }
                                    restDialog = false
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = we.restSeconds == s,
                                onClick = {
                                    updateExercise(we.id) { e -> e.copy(restSeconds = s) }
                                    restDialog = false
                                },
                            )
                            Text(if (s == 0) "Off" else "${s}s")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { restDialog = false }) { Text("Close") } },
        )
    }
}

/** What the program asks for on this exercise, plus why it changed or how to pick a first weight. */
@Composable
private fun ProgramTargetBox(we: WorkoutExercise, t: SetTarget, unit: String) {
    val bodyweight = Repo.exercise(we.exerciseId)?.equipment.let { it == null || it == "body only" }
    val note = Repo.program?.days?.flatMap { it.exercises }?.firstOrNull { it.id == we.programExerciseId }?.note
    val hint = when {
        t.deload -> "Deload: keep every set comfortable."
        t.weight == null && !bodyweight -> "First session: pick a weight you could lift for about ${t.rir} more reps at the end of each set."
        !note.isNullOrBlank() -> note
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildString {
                    append("Target ${t.sets} × ${t.reps}")
                    t.weight?.let { append(" @ ${formatWeight(it)} $unit") }
                    append("  ·  range ${t.repMin}-${t.repMax}  ·  ~${t.rir} in reserve")
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (t.deload) Warmup else Accent,
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

// ---------- set row ----------

@Composable
private fun SetRow(
    weId: String,
    entry: SetEntry,
    setNumber: Int,
    previous: SetEntry?,
    unit: String,
    restSeconds: Int,
) {
    val focus = LocalFocusManager.current
    var weightText by remember(entry.id) { mutableStateOf(formatWeight(entry.weight)) }
    var repsText by remember(entry.id) { mutableStateOf(entry.reps?.toString() ?: "") }
    var typeMenu by remember { mutableStateOf(false) }

    val label = when (entry.type) {
        SetType.WARMUP -> "W"
        SetType.DROP -> "D"
        SetType.FAILURE -> "F"
        else -> setNumber.toString()
    }
    val labelColor = when (entry.type) {
        SetType.WARMUP -> Warmup
        SetType.DROP -> Accent
        SetType.FAILURE -> Danger
        else -> MaterialTheme.colorScheme.onSurface
    }

    val onCheck: () -> Unit = {
        focus.clearFocus()
        if (!entry.completed) {
            val w = parseWeight(weightText) ?: previous?.weight
            val r = repsText.toIntOrNull() ?: previous?.reps
            weightText = formatWeight(w)
            repsText = r?.toString() ?: ""
            updateSet(weId, entry.id) { it.copy(weight = w, reps = r, completed = true) }
            Repo.startRest(restSeconds)
        } else {
            updateSet(weId, entry.id) { it.copy(completed = false) }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (entry.completed) Success.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // set label + type menu
        Box(Modifier.width(SetColW), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 30.dp, height = 30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { typeMenu = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = labelColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Normal set") },
                    onClick = {
                        typeMenu = false
                        updateSet(weId, entry.id) { it.copy(type = SetType.NORMAL) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Warm-up set", color = Warmup) },
                    onClick = {
                        typeMenu = false
                        updateSet(weId, entry.id) { it.copy(type = SetType.WARMUP) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Drop set", color = Accent) },
                    onClick = {
                        typeMenu = false
                        updateSet(weId, entry.id) { it.copy(type = SetType.DROP) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Failure set", color = Danger) },
                    onClick = {
                        typeMenu = false
                        updateSet(weId, entry.id) { it.copy(type = SetType.FAILURE) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete set", color = Danger) },
                    onClick = {
                        typeMenu = false
                        updateExercise(weId) { e -> e.copy(sets = e.sets.filter { it.id != entry.id }) }
                    },
                )
            }
        }

        // previous
        Box(
            Modifier
                .weight(PrevWeight)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = previous != null) {
                    if (previous != null) {
                        weightText = formatWeight(previous.weight)
                        repsText = previous.reps?.toString() ?: ""
                        updateSet(weId, entry.id) { it.copy(weight = previous.weight, reps = previous.reps) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                previousText(previous, unit),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        // weight
        CompactField(
            value = weightText,
            onValueChange = { raw ->
                val t = raw.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)
                weightText = t
                updateSet(weId, entry.id) { it.copy(weight = parseWeight(t)) }
            },
            placeholder = previous?.weight?.let { formatWeight(it) } ?: "-",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Decimal,
        )

        // reps
        CompactField(
            value = repsText,
            onValueChange = { raw ->
                val t = raw.filter { c -> c.isDigit() }.take(4)
                repsText = t
                updateSet(weId, entry.id) { it.copy(reps = t.toIntOrNull()) }
            },
            placeholder = previous?.reps?.toString() ?: "-",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
        )

        // check
        Box(Modifier.width(CheckColW), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 34.dp, height = 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (entry.completed) Success else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onCheck() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (entry.completed) "Mark set incomplete" else "Mark set complete",
                    modifier = Modifier.size(20.dp),
                    tint = if (entry.completed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- compact text field ----------

@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    center: Boolean = true,
) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    val style = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        textAlign = align,
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .padding(horizontal = 8.dp),
                contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = style.copy(color = placeholderColor), maxLines = 1)
                }
                inner()
            }
        },
    )
}
