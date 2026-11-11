package com.tooheavy.app.ui.nutrition

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.clockTime
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.timeOn
import com.tooheavy.app.ui.theme.Danger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** "1 food", "3 foods". */
private fun foods(n: Int): String = if (n == 1) "1 food" else "$n foods"

/** Logs the entries [ids] again today at the current time and says so. */
fun logAgainNow(context: Context, ids: Set<String>) {
    val n = NutritionRepo.logAgainNow(ids)
    if (n > 0) Toast.makeText(context, "Added ${foods(n)} to today at ${clockTime(System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
}

// The date picker works in UTC midnights.
private fun utcDayFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private fun utcMillisOf(date: String): Long? = runCatching { utcDayFormat().parse(date)?.time }.getOrNull()
private fun dateOfUtcMillis(ms: Long): String = utcDayFormat().format(Date(ms))

private enum class SelectionDialog { DELETE, TIME, COPY, MOVE }

/**
 * The top of the timeline while foods of [date] are picked: how many, pick all or none, and what to do with
 * them: add them again now, change their time, copy or move them to another day, or delete them.
 * [onChange] sets the picked foods; an empty set ends picking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineSelectionBar(date: String, selected: Set<String>, allFoods: Set<String>, onChange: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<SelectionDialog?>(null) }
    val n = selected.size

    fun done(message: String) {
        onChange(emptySet())
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onChange(emptySet()) }) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
            Text("$n selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selected.containsAll(allFoods)) {
                TextButton(onClick = { onChange(emptySet()) }) { Text("Select none") }
            } else {
                TextButton(onClick = { onChange(allFoods) }) { Text("Select all") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Composable
            fun Action(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
                AssistChip(
                    onClick = onClick,
                    label = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = if (danger) {
                        AssistChipDefaults.assistChipColors(labelColor = Danger, leadingIconContentColor = Danger)
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
            Action("Add again now", Icons.Filled.ContentCopy) {
                logAgainNow(context, selected)
                onChange(emptySet())
            }
            Action("Change time", Icons.Filled.Schedule) { dialog = SelectionDialog.TIME }
            Action("Copy to day", Icons.Filled.CalendarMonth) { dialog = SelectionDialog.COPY }
            Action("Move to day", Icons.Filled.Event) { dialog = SelectionDialog.MOVE }
            Action("Delete", Icons.Filled.Delete, danger = true) { dialog = SelectionDialog.DELETE }
        }
        Text(
            "Tap foods, or a meal's total, to pick or drop them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
        )
    }

    when (dialog) {
        SelectionDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${foods(n)}?") },
            text = { Text("They are removed from ${friendlyDate(date).lowercaseIfRelative()}.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    val deleted = NutritionRepo.deleteEntries(selected)
                    done("Deleted ${foods(deleted)}")
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        SelectionDialog.TIME -> {
            val first = NutritionRepo.entries.filter { it.id in selected }.minOfOrNull { it.createdAt } ?: System.currentTimeMillis()
            val c = Calendar.getInstance().apply { timeInMillis = first }
            val state = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), is24Hour = true)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Eaten at") },
                text = {
                    Column {
                        if (n > 1) {
                            Text(
                                "The first one goes to this time; the others keep their distance to it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        TimePicker(state)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        val moved = NutritionRepo.moveEntriesToTime(selected, timeOn(date, state.hour, state.minute))
                        done("Moved ${foods(moved)} to ${String.format(Locale.US, "%02d:%02d", state.hour, state.minute)}")
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        SelectionDialog.COPY, SelectionDialog.MOVE -> {
            val copy = dialog == SelectionDialog.COPY
            val state = rememberDatePickerState(initialSelectedDateMillis = utcMillisOf(date))
            DatePickerDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(
                        enabled = state.selectedDateMillis != null,
                        onClick = {
                            state.selectedDateMillis?.let(::dateOfUtcMillis)?.let { day ->
                                dialog = null
                                val where = friendlyDate(day).lowercaseIfRelative()
                                if (copy) {
                                    done("Copied ${foods(NutritionRepo.copyEntriesTo(selected, day))} to $where")
                                } else if (day != date) {
                                    done("Moved ${foods(NutritionRepo.moveEntriesTo(selected, day))} to $where")
                                }
                            }
                        },
                    ) { Text(if (copy) "Copy" else "Move") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            ) {
                DatePicker(
                    state = state,
                    title = {
                        Text(
                            if (copy) "Copy ${foods(n)} to" else "Move ${foods(n)} to",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                        )
                    },
                )
            }
        }
        null -> {}
    }
}

/** "today" for "Today" (and yesterday, tomorrow) so it reads in a sentence; dates stay as they are. */
private fun String.lowercaseIfRelative(): String =
    if (this == "Today" || this == "Yesterday" || this == "Tomorrow") lowercase(Locale.US) else this
