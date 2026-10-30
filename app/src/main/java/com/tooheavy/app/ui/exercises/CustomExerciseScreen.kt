package com.tooheavy.app.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.theme.Danger

/** Categories offered for custom exercises (the library's own categories). */
private val exerciseCategories = listOf("strength", "powerlifting", "olympic weightlifting", "cardio", "plyometrics", "stretching", "strongman")

/** Editor for a user-created exercise; [id] = "new" or a custom Exercise.id. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseScreen(nav: NavController, id: String) {
    val existing = remember(id) { Repo.exercise(id)?.takeIf { it.isCustom } }
    val muscles = remember { Repo.muscles() }
    val equipments = remember { Repo.equipments() }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var primary by remember { mutableStateOf(existing?.primaryMuscles?.firstOrNull()) }
    var secondary by remember { mutableStateOf(existing?.secondaryMuscles.orEmpty().toSet()) }
    var equipment by remember { mutableStateOf(existing?.equipment) }
    var category by remember { mutableStateOf(existing?.category ?: "strength") }
    var instructions by remember { mutableStateOf(existing?.instructions.orEmpty().joinToString("\n")) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val uses = remember(id) { existing?.let { Repo.exerciseUses(it.id) } ?: 0 }

    fun save() {
        val n = name.trim()
        val p = primary
        when {
            n.isEmpty() -> error = "Enter a name."
            p == null -> error = "Pick the main muscle."
            else -> {
                Repo.saveCustomExercise(
                    Exercise(
                        id = existing?.id ?: Exercise.newCustomId(),
                        name = n,
                        equipment = equipment,
                        primaryMuscles = listOf(p),
                        secondaryMuscles = secondary.filter { it != p }.sorted(),
                        category = category,
                        instructions = instructions.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    )
                )
                nav.popBackStack()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(if (existing == null) "New exercise" else "Edit exercise") },
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
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            ExerciseFieldLabel("Main muscle")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                muscles.forEach { m ->
                    FilterChip(selected = primary == m, onClick = { primary = m; error = null }, label = { Text(m.titleCase()) })
                }
            }

            ExerciseFieldLabel("Other muscles worked")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                muscles.filter { it != primary }.forEach { m ->
                    FilterChip(
                        selected = m in secondary,
                        onClick = { secondary = if (m in secondary) secondary - m else secondary + m },
                        label = { Text(m.titleCase()) },
                    )
                }
            }

            ExerciseFieldLabel("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = equipment == null, onClick = { equipment = null }, label = { Text("None") })
                equipments.forEach { e ->
                    FilterChip(selected = equipment == e, onClick = { equipment = e }, label = { Text(e.titleCase()) })
                }
            }

            ExerciseFieldLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exerciseCategories.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.titleCase()) })
                }
            }

            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Instructions (optional, one step per line)") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger) }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (existing == null) "Create exercise" else "Save changes", fontWeight = FontWeight.SemiBold) }

            if (existing != null) {
                if (uses == 0) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete exercise", color = Danger)
                    }
                } else {
                    Text(
                        "Used in $uses workout${if (uses == 1) "" else "s"}, routines or program days, so it can't be deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete exercise?") },
            text = { Text("\"${existing.name}\" will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteCustomExercise(existing.id)
                    // Leave the editor and the (now empty) detail page behind it.
                    nav.popBackStack()
                    nav.popBackStack()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ExerciseFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
