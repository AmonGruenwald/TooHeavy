package com.tooheavy.app.ui.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Equipment
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.Experience
import com.tooheavy.app.data.Program
import com.tooheavy.app.data.ProgramDay
import com.tooheavy.app.data.ProgramExercise
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.SetTarget
import com.tooheavy.app.data.TrainingGoal
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.isDeload
import com.tooheavy.app.data.mesoWeek
import com.tooheavy.app.data.MuscleGroup
import com.tooheavy.app.data.sessionsPerWeek
import com.tooheavy.app.data.weeklyGroupSets
import com.tooheavy.app.data.estimatedMinutes
import com.tooheavy.app.ui.common.formatSets
import com.tooheavy.app.data.trainingWeeks
import com.tooheavy.app.data.mesocycle
import com.tooheavy.app.data.nextDayIndex
import com.tooheavy.app.data.prescription
import com.tooheavy.app.data.replaceExercise
import com.tooheavy.app.data.swapOptions
import com.tooheavy.app.data.swapTo
import com.tooheavy.app.data.targetRir
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.common.Pill
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.BrandColors
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Surface3
import com.tooheavy.app.ui.theme.Warmup

/** "3 × 8 @ 60 kg", "3 × 8-12 reps" (load not known yet), "3 × 12" (bodyweight). */
internal fun targetText(t: SetTarget, unit: String, bodyweight: Boolean): String = when {
    t.weight != null -> "${t.sets} × ${t.reps} @ ${formatWeight(t.weight)} $unit"
    bodyweight -> "${t.sets} × ${t.reps}"
    else -> "${t.sets} × ${t.repMin}-${t.repMax} reps"
}

/** Starts a program day, or asks first when another workout is in progress. */
internal class PendingProgramStart(val dayIndex: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(nav: NavController) {
    val program = Repo.program
    LaunchedEffect(program == null) {
        if (program == null) nav.popBackStack()
    }
    if (program == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val unit = Repo.settings.unit
    var menuOpen by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingProgramStart?>(null) }
    var swapFor by remember { mutableStateOf<ProgramExercise?>(null) }
    var editFor by remember { mutableStateOf<ProgramExercise?>(null) }

    fun start(dayIndex: Int) {
        if (Repo.activeWorkout != null) {
            pending = PendingProgramStart(dayIndex)
        } else {
            Repo.startProgramWorkout(dayIndex)
            nav.navigate(Routes.ACTIVE)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(program.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("New program") },
                                onClick = {
                                    menuOpen = false
                                    nav.navigate(Routes.PROGRAM_SETUP)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("End program", color = Danger) },
                                onClick = {
                                    menuOpen = false
                                    confirmEnd = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "status") { StatusCard(program) }
            itemsIndexed(program.days, key = { _, d -> d.id }) { i, day ->
                DayCard(
                    program = program,
                    day = day,
                    isNext = i == program.nextDayIndex,
                    unit = unit,
                    onStart = { start(i) },
                    onOpenExercise = { nav.navigate(Routes.exercise(it.exerciseId)) },
                    onSwap = { swapFor = it },
                    onEdit = { editFor = it },
                )
            }
            item(key = "how") { HowItWorksCard() }
        }
    }

    swapFor?.let { pe ->
        SwapDialog(
            program = program,
            pe = pe,
            onPick = { e ->
                Repo.saveProgram(program.replaceExercise(pe.swapTo(e, program.goal, unit, Repo.best1RM(e.id))))
                swapFor = null
            },
            onDismiss = { swapFor = null },
        )
    }

    editFor?.let { pe ->
        EditTargetDialog(
            pe = pe,
            unit = unit,
            onSave = { updated ->
                Repo.saveProgram(program.replaceExercise(updated))
                editFor = null
            },
            onDismiss = { editFor = null },
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("You have a workout in progress") },
            text = { Text("Resume it, or discard it and start ${program.days.getOrNull(p.dayIndex)?.name ?: "this day"}?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        pending = null
                        nav.navigate(Routes.ACTIVE)
                    }) { Text("Resume") }
                    TextButton(onClick = {
                        pending = null
                        Repo.discardWorkout()
                        Repo.startProgramWorkout(p.dayIndex)
                        nav.navigate(Routes.ACTIVE)
                    }) { Text("Discard & start new", color = Danger) }
                    TextButton(onClick = { pending = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End program?") },
            text = { Text("Its targets and progress are removed. Workouts you logged stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    Repo.saveProgram(null)
                }) { Text("End program", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusCard(program: Program) {
    val deload = program.isDeload
    ProgramCard {
        Text(
            if (deload) "Deload week" else "Week ${program.mesoWeek} of ${program.trainingWeeks}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (deload) Warmup else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Mesocycle ${program.mesocycle} · ${program.sessionsCompleted} workouts done · " +
                "${TrainingGoal.label(program.goal)}, ${Experience.label(program.experience).lowercase()}, ${Equipment.label(program.equipment).lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val weeks = program.trainingWeeks + 1
        Meter(program.mesoWeek / weeks.toFloat(), if (deload) Warmup else AccentBright)
        Text(
            if (deload) "Lighter week to shed fatigue: about half the sets at 90 % of the weight. Keep every set easy."
            else "Stop each set with about ${program.targetRir} ${if (program.targetRir == 1) "rep" else "reps"} left in the tank " +
                "and log it in the RIR column. Effort builds through the block, then a deload week.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (program.blockWeeks > 0 && !deload) {
            Text(
                "Fatigue built up, so this block's deload comes after week ${program.trainingWeeks}.",
                style = MaterialTheme.typography.bodySmall,
                color = Warmup,
            )
        }
        val details = listOfNotNull(
            program.focus.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Focus: ") { MuscleGroup.label(it) },
            program.reduce.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Reduced: ") { MuscleGroup.label(it) },
            "${program.sessionsPerWeek} sessions a week".takeIf { program.sessionsPerWeek != program.days.size },
            program.days.maxOfOrNull { it.estimatedMinutes }?.let { m ->
                "up to about $m min per workout" + if (program.minutes > 0) " (limit ${program.minutes})" else ""
            },
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val weekly = program.weeklyGroupSets
        Text(
            "Weekly sets: " + MuscleGroup.all.filter { (weekly[it] ?: 0.0) > 0 }
                .joinToString(" · ") { "${MuscleGroup.label(it)} ${formatSets(weekly.getValue(it))}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCard(
    program: Program,
    day: ProgramDay,
    isNext: Boolean,
    unit: String,
    onStart: () -> Unit,
    onOpenExercise: (ProgramExercise) -> Unit,
    onSwap: (ProgramExercise) -> Unit,
    onEdit: (ProgramExercise) -> Unit,
) {
    ProgramCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (isNext) Pill("Up next", BrandColors[1])
        }
        Column {
            day.exercises.forEachIndexed { i, pe ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExerciseRow(program, pe, unit, onOpenExercise, onSwap, onEdit)
            }
        }
        if (isNext) {
            GradientButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start ${day.name}") }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface3, contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text("Start ${day.name}", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ExerciseRow(
    program: Program,
    pe: ProgramExercise,
    unit: String,
    onOpen: (ProgramExercise) -> Unit,
    onSwap: (ProgramExercise) -> Unit,
    onEdit: (ProgramExercise) -> Unit,
) {
    val ex = Repo.exercise(pe.exerciseId)
    val t = program.prescription(pe, unit, ex?.equipment)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { menuOpen = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(ex, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ex?.name ?: "Unknown exercise",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${targetText(t, unit, pe.sessions > 0 && pe.weight == null)}  ·  ${pe.repMin}-${pe.repMax} rep range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pe.note.isNotBlank()) {
                Text(
                    pe.note,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = AccentBright,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Swap exercise") }, onClick = { menuOpen = false; onSwap(pe) })
                DropdownMenuItem(text = { Text("Edit target") }, onClick = { menuOpen = false; onEdit(pe) })
                DropdownMenuItem(text = { Text("How to do it") }, onClick = { menuOpen = false; onOpen(pe) })
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    ProgramCard {
        Text("How it adapts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        listOf(
            "Log what you actually lift. Targets for next time are set from your results when you finish.",
            "Reps: beat your weakest set by one rep at the same weight until every set reaches the top of the range.",
            "Weight: once every set hits the top of the range, the weight goes up and reps start over at the bottom.",
            "Stalls: three sessions in a row short of the target drop the weight about 10 % to build back up.",
            "Volume: hitting your targets twice in a row adds a set; falling below the range takes one away.",
            "Every few weeks a deload week lets fatigue clear before the next block starts at base volume.",
        ).forEach {
            Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwapDialog(program: Program, pe: ProgramExercise, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    val options = remember(pe.id, pe.exerciseId) { swapOptions(pe, program.equipment, Repo.exercises).take(40) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap ${Repo.exercise(pe.exerciseId)?.name ?: "exercise"}") },
        text = {
            if (options.isEmpty()) {
                Text("No alternatives for your equipment.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options, key = { it.id }) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(e) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseThumbnail(e, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(
                                    listOfNotNull(e.equipment?.titleCase(), e.primaryMuscles.firstOrNull()?.titleCase()).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditTargetDialog(pe: ProgramExercise, unit: String, onSave: (ProgramExercise) -> Unit, onDismiss: () -> Unit) {
    var weightText by remember { mutableStateOf(formatWeight(pe.weight)) }
    var repsText by remember { mutableStateOf(pe.targetReps.toString()) }
    var setsText by remember { mutableStateOf(pe.sets.toString()) }
    val weight = weightText.trim().replace(',', '.').let { if (it.isEmpty()) null else it.toDoubleOrNull() }
    val reps = repsText.toIntOrNull()?.takeIf { it in 1..100 }
    val sets = setsText.toIntOrNull()?.takeIf { it in 1..10 }
    val valid = reps != null && sets != null && (weightText.isBlank() || (weight != null && weight >= 0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Set what you'll aim for next session. The program keeps adapting from there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.take(7) },
                    label = { Text("Weight ($unit)") },
                    placeholder = { Text("Bodyweight / not set") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = setsText,
                        onValueChange = { setsText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        pe.copy(
                            weight = weight?.takeIf { it > 0 },
                            targetReps = reps ?: pe.targetReps,
                            sets = sets ?: pe.sets,
                            stalls = 0,
                            note = "Edited by you",
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ProgramCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
