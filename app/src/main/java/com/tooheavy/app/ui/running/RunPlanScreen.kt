package com.tooheavy.app.ui.running

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.PlannedRun
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.RunPlan
import com.tooheavy.app.data.RunWeek
import com.tooheavy.app.data.distanceUnit
import com.tooheavy.app.data.formatClock
import com.tooheavy.app.data.formatDistanceValue
import com.tooheavy.app.data.kmFrom
import com.tooheavy.app.data.mondayOf
import com.tooheavy.app.data.newId
import com.tooheavy.app.data.nextRun
import com.tooheavy.app.data.parseRunPlan
import com.tooheavy.app.data.parseSetTime
import com.tooheavy.app.data.progress
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.today
import com.tooheavy.app.data.updateRun
import com.tooheavy.app.data.weekIndexOn
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.common.Pill
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.Warmup
import java.text.SimpleDateFormat
import java.util.Locale

/** Example shown in the plan text field. */
private const val PLAN_EXAMPLE = "Week 1\nEasy run 3 km\nRun 1 min, walk 90 s × 8\nLong run 5 km\n\nWeek 2: Easy run 4 km; Tempo 20 min; Long run 6 km"

/** "Mon, Nov 2" */
private fun dayLabel(date: String): String =
    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull()
        ?.let { SimpleDateFormat("EEE, MMM d", Locale.US).format(it) } ?: date

/**
 * Starts the workout for a planned run, or asks first when another workout is in progress. Returns the
 * dialog state to show, if any; callers render [StartRunDialog] for it.
 */
private fun startRun(nav: NavController, runId: String): String? {
    if (Repo.activeWorkout != null) return runId
    Repo.startPlannedRun(runId)
    nav.navigate(Routes.ACTIVE)
    return null
}

/** Asks what to do with the workout in progress before starting planned run [runId]. */
@Composable
fun StartRunDialog(nav: NavController, runId: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("You have a workout in progress") },
        text = { Text("Resume it, or discard it and start this run?") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    onDismiss()
                    nav.navigate(Routes.ACTIVE)
                }) { Text("Resume") }
                TextButton(onClick = {
                    onDismiss()
                    Repo.discardWorkout()
                    Repo.startPlannedRun(runId)
                    nav.navigate(Routes.ACTIVE)
                }) { Text("Discard & start run", color = Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** The running plan: write or paste one, then follow it week by week. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunPlanScreen(nav: NavController) {
    val plan = Repo.runPlan
    var menuOpen by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(plan?.name ?: "New running plan", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (plan != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Plan options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Name and start") },
                                    onClick = {
                                        menuOpen = false
                                        settings = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add weeks from text") },
                                    onClick = {
                                        menuOpen = false
                                        addText = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete plan", color = Danger) },
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
        if (plan == null) {
            PlanSetup(Modifier.padding(pad))
        } else {
            PlanOverview(nav, plan, Modifier.padding(pad))
        }
    }

    if (plan != null && settings) {
        PlanSettingsDialog(plan) { settings = false }
    }
    if (plan != null && addText) {
        AddWeeksDialog { addText = false }
    }
    if (plan != null && confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete running plan?") },
            text = { Text("\"${plan.name}\" and its progress will be removed. Logged runs stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.saveRunPlan(null)
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------- setup ----------

@Composable
private fun PlanSetup(modifier: Modifier) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(mondayOf(today())) }
    var text by remember { mutableStateOf("") }
    val weeks = remember(text) { parseRunPlan(text) }
    val runs = weeks.sumOf { it.runs.size }

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Follow your own running plan week by week. Paste it below or write it line by line: \"Week 1\" starts a " +
                "week and every other line is a run. Distances (5 km, 10k, 3 miles) and times (30 min) become the " +
                "run's targets. You can change everything afterwards.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (e.g. Half marathon)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        StartPicker(start) { start = it }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Plan") },
            placeholder = { Text(PLAN_EXAMPLE) },
            minLines = 8,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (runs == 0) "No runs yet." else "${weeks.size} week${if (weeks.size == 1) "" else "s"}, $runs run${if (runs == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientButton(
            onClick = {
                Repo.saveRunPlan(
                    RunPlan(
                        name = name.trim().ifEmpty { "Running plan" },
                        startDate = start,
                        weeks = weeks.filter { it.runs.isNotEmpty() }.ifEmpty { listOf(RunWeek()) },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (runs == 0) "Start with an empty week" else "Create plan") }
        Spacer(Modifier.height(16.dp))
    }
}

/** Week 1's Monday, moved a week at a time. */
@Composable
private fun StartPicker(start: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Week 1 starts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dayLabel(start), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onChange(shiftDate(start, -7)) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "A week earlier")
        }
        IconButton(onClick = { onChange(shiftDate(start, 7)) }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "A week later")
        }
    }
}

// ---------- overview ----------

@Composable
private fun PlanOverview(nav: NavController, plan: RunPlan, modifier: Modifier) {
    val day = today()
    val current = plan.weekIndexOn(day)
    val unit = distanceUnit(Repo.settings.unit)
    var startDialog by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Pair<Int, PlannedRun>?>(null) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            PlanSummaryCard(plan, unit) { startDialog = startRun(nav, it) }
        }
        itemsIndexed(plan.weeks, key = { _, w -> w.id }) { i, week ->
            WeekCard(
                index = i,
                week = week,
                plan = plan,
                current = current,
                unit = unit,
                onStart = { startDialog = startRun(nav, it) },
                onEdit = { editing = i to it },
                onOpenWorkout = { nav.navigate(Routes.workoutDetail(it)) },
            )
        }
        item(key = "add_week") {
            OutlinedButton(
                onClick = { Repo.updateRunPlan { it.copy(weeks = it.weeks + RunWeek()) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add week")
            }
        }
    }

    startDialog?.let { id -> StartRunDialog(nav, id) { startDialog = null } }
    editing?.let { (weekIndex, run) ->
        RunEditDialog(run, unit, onDismiss = { editing = null }) { edited ->
            editing = null
            Repo.updateRunPlan { p ->
                if (p.weeks.any { w -> w.runs.any { it.id == edited.id } }) p.updateRun(edited.id) { edited }
                else p.copy(weeks = p.weeks.mapIndexed { i, w -> if (i == weekIndex) w.copy(runs = w.runs + edited) else w })
            }
        }
    }
}

@Composable
private fun PlanSummaryCard(plan: RunPlan, unit: String, onStart: (String) -> Unit) {
    val day = today()
    val current = plan.weekIndexOn(day)
    val (done, total) = plan.progress()
    val next = plan.nextRun(day)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardLabel(
                when {
                    current < 0 -> "Starts ${dayLabel(plan.startDate)}"
                    current >= plan.weeks.size -> "Plan finished"
                    else -> "Week ${current + 1} of ${plan.weeks.size}"
                },
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
            )
            Text("$done of $total runs done", style = MaterialTheme.typography.bodyMedium)
            Meter(if (total > 0) done.toFloat() / total else 0f, Accent)
            if (next != null) {
                val (w, run) = next
                Text("Next: ${run.name}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val target = run.targetText(unit)
                Text(
                    listOf("Week ${w + 1}", target).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GradientButton(onClick = { onStart(run.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start run")
                }
            }
        }
    }
}

@Composable
private fun WeekCard(
    index: Int,
    week: RunWeek,
    plan: RunPlan,
    current: Int,
    unit: String,
    onStart: (String) -> Unit,
    onEdit: (PlannedRun) -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val monday = shiftDate(plan.startDate, index * 7)
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Week ${index + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${dayLabel(monday)} – ${dayLabel(shiftDate(monday, 6))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index == current) Pill("This week", AccentBright)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Week options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Add run") },
                            onClick = {
                                menuOpen = false
                                onEdit(PlannedRun(name = ""))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate week") },
                            onClick = {
                                menuOpen = false
                                val copy = RunWeek(runs = week.runs.map { it.copy(id = newId(), doneWorkoutId = null, skipped = false) })
                                Repo.updateRunPlan { p ->
                                    p.copy(weeks = p.weeks.take(index + 1) + copy + p.weeks.drop(index + 1))
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete week", color = Danger) },
                            onClick = {
                                menuOpen = false
                                Repo.updateRunPlan { p -> p.copy(weeks = p.weeks.filterIndexed { i, _ -> i != index }) }
                            },
                        )
                    }
                }
            }
            if (week.runs.isEmpty()) {
                TextButton(onClick = { onEdit(PlannedRun(name = "")) }) { Text("Add a run") }
            }
            week.runs.forEach { run ->
                key(run.id) {
                    RunRow(run, missed = index < current && run.open, unit, onStart, onEdit, onOpenWorkout)
                }
            }
        }
    }
}

@Composable
private fun RunRow(
    run: PlannedRun,
    missed: Boolean,
    unit: String,
    onStart: (String) -> Unit,
    onEdit: (PlannedRun) -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { menuOpen = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                run.done -> Icons.Filled.CheckCircle
                run.skipped -> Icons.Filled.SkipNext
                else -> Icons.Filled.RadioButtonUnchecked
            },
            contentDescription = when {
                run.done -> "Done"
                run.skipped -> "Skipped"
                else -> "Open"
            },
            tint = when {
                run.done -> Success
                missed -> Warmup
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                run.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (run.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                run.targetText(unit).takeIf { it.isNotEmpty() },
                when {
                    run.skipped -> "skipped"
                    missed -> "missed"
                    else -> null
                },
                run.notes.takeIf { it.isNotBlank() },
            )
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missed) Warmup else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Run options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!run.done) {
                    DropdownMenuItem(
                        text = { Text("Start run") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onStart(run.id)
                        },
                    )
                }
                val workoutId = run.doneWorkoutId?.takeIf { it != PlannedRun.DONE_BY_HAND && Repo.workout(it) != null }
                if (workoutId != null) {
                    DropdownMenuItem(
                        text = { Text("View workout") },
                        onClick = {
                            menuOpen = false
                            onOpenWorkout(workoutId)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (run.done) "Mark not done" else "Mark done") },
                    onClick = {
                        menuOpen = false
                        Repo.updateRunPlan { p ->
                            p.updateRun(run.id) {
                                it.copy(doneWorkoutId = if (run.done) null else PlannedRun.DONE_BY_HAND, skipped = false)
                            }
                        }
                    },
                )
                if (!run.done) {
                    DropdownMenuItem(
                        text = { Text(if (run.skipped) "Don't skip" else "Skip") },
                        onClick = {
                            menuOpen = false
                            Repo.updateRunPlan { p -> p.updateRun(run.id) { it.copy(skipped = !run.skipped) } }
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menuOpen = false
                        onEdit(run)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Danger) },
                    onClick = {
                        menuOpen = false
                        Repo.updateRunPlan { p ->
                            p.copy(weeks = p.weeks.map { w -> w.copy(runs = w.runs.filter { it.id != run.id }) })
                        }
                    },
                )
            }
        }
    }
}

// ---------- dialogs ----------

/** Edits a run (or a new one, with an empty name): what to do, notes and targets. */
@Composable
private fun RunEditDialog(run: PlannedRun, unit: String, onDismiss: () -> Unit, onSave: (PlannedRun) -> Unit) {
    var name by remember { mutableStateOf(run.name) }
    var notes by remember { mutableStateOf(run.notes) }
    var dist by remember { mutableStateOf(run.distanceKm?.let { formatDistanceValue(it, unit) }.orEmpty()) }
    var time by remember { mutableStateOf(run.seconds?.let { formatClock(it * 1000L) }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (run.name.isEmpty()) "New run" else "Edit run") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Run (e.g. Easy run, 6 × 400 m)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dist,
                        onValueChange = { s -> if (s.length <= 6 && s.all { it.isDigit() || it == '.' } && s.count { it == '.' } <= 1) dist = s },
                        label = { Text("Distance ($unit)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { s -> if (s.length <= 8 && s.all { it.isDigit() || it == ':' }) time = s },
                        label = { Text("Time (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        run.copy(
                            name = name.trim(),
                            notes = notes.trim(),
                            distanceKm = dist.toDoubleOrNull()?.takeIf { it > 0 }?.let { kmFrom(it, unit) },
                            seconds = parseSetTime(time),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlanSettingsDialog(plan: RunPlan, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(plan.name) }
    var start by remember { mutableStateOf(plan.startDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name and start") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                StartPicker(start) { start = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                Repo.updateRunPlan { it.copy(name = name.trim().ifEmpty { it.name }, startDate = start) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Appends weeks written as text (same format as when creating the plan). */
@Composable
private fun AddWeeksDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val weeks = remember(text) { parseRunPlan(text).filter { it.runs.isNotEmpty() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add weeks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "They go after the last week. \"Week N\" starts a week; every other line is a run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(PLAN_EXAMPLE) },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = weeks.isNotEmpty(), onClick = {
                onDismiss()
                Repo.updateRunPlan { it.copy(weeks = it.weeks + weeks) }
            }) { Text(if (weeks.isEmpty()) "Add" else "Add ${weeks.size} week${if (weeks.size == 1) "" else "s"}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
