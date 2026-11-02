package com.tooheavy.app.ui.habits

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Habit
import com.tooheavy.app.data.HabitFrequency
import com.tooheavy.app.data.HabitReminder
import com.tooheavy.app.data.HabitRepo
import com.tooheavy.app.data.weekdayShort
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.OnAccent

/** Editor for a habit; [id] is "new" (optionally filled with [prefillName] and [prefillTarget]) or a Habit.id. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(nav: NavController, id: String, prefillName: String? = null, prefillTarget: Int? = null) {
    val existing = remember(id) { HabitRepo.habit(id) }
    val start = remember(id) {
        existing ?: Habit(name = prefillName.orEmpty(), target = prefillTarget ?: 1, color = HabitRepo.habits.size % HabitColors.size)
    }
    var name by remember { mutableStateOf(start.name) }
    var color by remember { mutableStateOf(start.color) }
    var target by remember { mutableStateOf(start.target) }
    var unit by remember { mutableStateOf(start.unit.ifEmpty { if (prefillName == "Drink water") "glasses" else "" }) }
    var frequency by remember { mutableStateOf(start.frequency) }
    var days by remember { mutableStateOf(start.days.toSet()) }
    var timesPerWeek by remember { mutableStateOf(start.timesPerWeek) }
    var reminders by remember { mutableStateOf(start.reminders) }
    var timeDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun commit() {
        HabitRepo.saveHabit(
            start.copy(
                name = name.trim(),
                color = color,
                target = target,
                unit = if (target > 1) unit.trim() else "",
                frequency = frequency,
                days = days.sorted(),
                timesPerWeek = timesPerWeek,
                reminders = reminders.sortedBy { it.hour * 60 + it.minute },
            ),
        )
        nav.popBackStack()
    }

    // Reminders need the notification permission on Android 13+; the habit is saved either way.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { commit() }

    fun save() {
        when {
            name.isBlank() -> error = "Give the habit a name."
            frequency == HabitFrequency.DAYS && days.isEmpty() -> error = "Pick at least one day."
            reminders.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> commit()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(if (existing == null) "New habit" else "Edit habit") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Habit (e.g. Read, Meditate)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Label("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HabitColors.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(2.dp, if (i == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .clickable { color = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == color) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = OnAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Label("Goal per day")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (target == 1) "Once (done or not)" else "$target times",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { target = (target - 1).coerceAtLeast(1) }, enabled = target > 1) {
                    Icon(Icons.Filled.Remove, contentDescription = "Less")
                }
                IconButton(onClick = { target = (target + 1).coerceAtMost(100) }) {
                    Icon(Icons.Filled.Add, contentDescription = "More")
                }
            }
            if (target > 1) {
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.take(20) },
                    label = { Text("Counted in (optional, e.g. glasses, pages)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Label("How often")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HabitFrequency.DAILY to "Every day", HabitFrequency.DAYS to "Some days", HabitFrequency.WEEKLY to "Times a week").forEach { (f, label) ->
                    FilterChip(selected = frequency == f, onClick = { frequency = f; error = null }, label = { Text(label) })
                }
            }
            when (frequency) {
                HabitFrequency.DAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d; error = null },
                            label = { Text(weekdayShort(d)) },
                        )
                    }
                }
                HabitFrequency.WEEKLY -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (timesPerWeek == 1) "Once a week, any day" else "$timesPerWeek days a week, any days",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { timesPerWeek = (timesPerWeek - 1).coerceAtLeast(1) }, enabled = timesPerWeek > 1) {
                        Icon(Icons.Filled.Remove, contentDescription = "Fewer days")
                    }
                    IconButton(onClick = { timesPerWeek = (timesPerWeek + 1).coerceAtMost(7) }, enabled = timesPerWeek < 7) {
                        Icon(Icons.Filled.Add, contentDescription = "More days")
                    }
                }
            }

            Label("Reminders")
            Text(
                when (frequency) {
                    HabitFrequency.WEEKLY -> "Every day until the week's goal is reached, unless it's already done that day."
                    HabitFrequency.DAYS -> "On the chosen days, unless it's already done."
                    else -> "Every day, unless it's already done."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminders.forEach { r ->
                    InputChip(
                        selected = false,
                        onClick = { reminders = reminders - r },
                        label = { Text(r.label) },
                        leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove reminder", modifier = Modifier.size(16.dp)) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { timeDialog = true },
                    label = { Text("Add reminder") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }

            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger) }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (existing == null) "Create habit" else "Save changes", fontWeight = FontWeight.SemiBold) }

            if (existing != null) {
                TextButton(
                    onClick = {
                        HabitRepo.saveHabit(existing.copy(archived = !existing.archived))
                        nav.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (existing.archived) "Restore habit" else "Archive habit (keeps its history)") }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete habit", color = Danger)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (timeDialog) {
        val state = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text("Reminder time") },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    timeDialog = false
                    val r = HabitReminder(state.hour, state.minute)
                    if (r !in reminders) reminders = reminders + r
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete habit?") },
            text = { Text("\"${existing.name}\" and its whole history will be removed. Archiving keeps the history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    HabitRepo.deleteHabit(existing.id)
                    // Back to the habit list, past the habit's page.
                    if (!nav.popBackStack(Routes.HABITS, inclusive = false)) nav.popBackStack()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
