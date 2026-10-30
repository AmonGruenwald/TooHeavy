package com.tooheavy.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Equipment
import com.tooheavy.app.data.Experience
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.TrainingGoal
import com.tooheavy.app.data.generateProgram
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.theme.Danger

private fun splitDescription(days: Int) = when (days) {
    2 -> "Full body twice a week: two alternating sessions that each train everything."
    3 -> "Full body three times a week: every muscle trained three times, with a different emphasis each day."
    4 -> "Upper/lower split: each half of the body trained twice a week."
    5 -> "Upper and lower days plus push, pull and legs: every muscle about twice a week."
    else -> "Push/pull/legs twice a week: high frequency and volume for experienced lifters."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramSetupScreen(nav: NavController) {
    val existing = Repo.program
    var goal by remember { mutableStateOf(existing?.goal ?: TrainingGoal.HYPERTROPHY) }
    var level by remember { mutableStateOf(existing?.experience ?: Experience.BEGINNER) }
    var days by remember { mutableIntStateOf(existing?.days?.size ?: 3) }
    var equipment by remember { mutableStateOf(existing?.equipment ?: Equipment.GYM) }
    var confirmReplace by remember { mutableStateOf(false) }

    fun create() {
        val p = generateProgram(goal, level, days, equipment, Repo.settings.unit, Repo::exercise, Repo::best1RM)
        Repo.saveProgram(p)
        nav.navigate(Routes.PROGRAM) {
            popUpTo(Routes.PROGRAM_SETUP) { inclusive = true }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New program", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Answer four questions and TooHeavy builds your plan. After every workout it sets the next session's " +
                    "weights, reps and sets from what you actually lifted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ProgramCard {
                Text("Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TrainingGoal.all.forEach { g ->
                    OptionRow(TrainingGoal.label(g), TrainingGoal.hint(g), goal == g) { goal = g }
                }
            }

            ProgramCard {
                Text("Experience", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Experience.all.forEach { e ->
                    OptionRow(Experience.label(e), Experience.hint(e), level == e) { level = e }
                }
            }

            ProgramCard {
                Text("Days per week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..6).forEach { n ->
                        FilterChip(selected = days == n, onClick = { days = n }, label = { Text("$n") })
                    }
                }
                Text(splitDescription(days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            ProgramCard {
                Text("Equipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Equipment.all.forEach { e ->
                    OptionRow(Equipment.label(e), Equipment.hint(e), equipment == e) { equipment = e }
                }
            }

            Text(
                "Your first session at each exercise calibrates the weights: pick a load you could lift for 2 to 3 more reps. " +
                    "Exercises you've logged before start from your history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { if (existing != null) confirmReplace = true else create() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (existing != null) "Replace program" else "Create program", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your program?") },
            text = { Text("The current program's targets and progress are replaced. Workouts you logged stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    create()
                }) { Text("Replace", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OptionRow(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
