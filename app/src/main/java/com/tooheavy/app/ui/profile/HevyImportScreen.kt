package com.tooheavy.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.HevyImport
import com.tooheavy.app.data.Repo
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.Pill
import com.tooheavy.app.ui.navigateTab
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private sealed interface ImportState {
    data object Intro : ImportState
    data object Reading : ImportState
    data class Failed(val message: String) : ImportState
    /** [mapping]: Hevy exercise title → library exercise id, or null to create it as a custom exercise. */
    data class Preview(val parsed: HevyImport.Parsed, val mapping: Map<String, String?>, val duplicates: Int) : ImportState
    data class Done(val workouts: Int, val created: Int) : ImportState
}

/** Imports workout history from a Hevy CSV export: pick the file, review how exercises map, import. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HevyImportScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportState>(ImportState.Intro) }
    var picking by remember { mutableStateOf<String?>(null) }

    fun read(uri: Uri) {
        state = ImportState.Reading
        scope.launch {
            state = try {
                val (parsed, mapping) = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw HevyImport.FormatException("Couldn't open the file.")
                    val parsed = HevyImport.parse(text)
                    val library = Repo.exercises
                    parsed to parsed.exerciseTitles.associate { (title, _) -> title to HevyImport.match(title, library)?.id }
                }
                if (parsed.workouts.isEmpty()) ImportState.Failed("No workouts with weights or reps found in this file.")
                else ImportState.Preview(parsed, mapping, HevyImport.alreadyImported(parsed, Repo.workouts.toList()))
            } catch (e: HevyImport.FormatException) {
                ImportState.Failed(e.message ?: "Couldn't read this file.")
            } catch (e: Exception) {
                ImportState.Failed("Couldn't read this file (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { read(it) } }
    fun pickFile() = launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "*/*"))

    fun import(p: ImportState.Preview) {
        val created = p.mapping.filterValues { it == null }.keys.map { HevyImport.newExercise(it) }
        val ids = p.mapping.mapValues { (title, id) -> id ?: created.first { it.name == title.trim() }.id }
        val workouts = HevyImport.toWorkouts(p.parsed, ids, Repo.settings.unit, Repo.workouts.toList())
        // Only create the exercises the imported workouts actually use.
        val used = workouts.flatMap { w -> w.exercises.map { it.exerciseId } }.toSet()
        val needed = created.filter { it.id in used }
        Repo.importWorkouts(workouts, needed)
        state = ImportState.Done(workouts.size, needed.size)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text("Import from Hevy") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        when (val s = state) {
            ImportState.Intro, is ImportState.Failed -> Column(
                Modifier.padding(pad).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CardLabel("Bring your history")
                        Text(
                            "Imports every workout from a Hevy export, with sets, weights, reps, set types and notes. " +
                                "Exercises are matched to the TooHeavy library; ones it doesn't have are added as your own exercises.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "In Hevy: Profile → Settings → Export & Import Data → Export Workouts. Save the CSV file, then choose it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Importing the same file again skips workouts that are already here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (s is ImportState.Failed) Text(s.message, style = MaterialTheme.typography.bodyMedium, color = Danger)
                GradientButton(onClick = { pickFile() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose CSV file")
                }
            }

            ImportState.Reading -> Column(
                Modifier.padding(pad).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Reading workouts…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is ImportState.Preview -> ImportPreview(
                s,
                modifier = Modifier.padding(pad),
                onPick = { picking = it },
                onImport = { import(s) },
            )

            is ImportState.Done -> Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(
                    if (s.workouts == 1) "1 workout imported" else "${s.workouts} workouts imported",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (s.created > 0) {
                    Text(
                        "${s.created} exercise${if (s.created == 1) " was" else "s were"} added to your library. " +
                            "Check their muscles under Library → My exercises.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GradientButton(onClick = { nav.navigateTab(Routes.HISTORY) }, modifier = Modifier.fillMaxWidth()) {
                    Text("View history")
                }
            }
        }
    }

    val preview = state as? ImportState.Preview
    val title = picking
    if (preview != null && title != null) {
        ExerciseChoiceDialog(
            hevyTitle = title,
            onDismiss = { picking = null },
            onChoose = { id ->
                picking = null
                state = preview.copy(mapping = preview.mapping + (title to id))
            },
        )
    }
}

@Composable
private fun ImportPreview(
    p: ImportState.Preview,
    modifier: Modifier,
    onPick: (String) -> Unit,
    onImport: () -> Unit,
) {
    val parsed = p.parsed
    val fresh = parsed.workouts.size - p.duplicates
    val sets = parsed.workouts.sumOf { w -> w.exercises.sumOf { it.sets.size } }
    val day = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val newCount = p.mapping.count { it.value == null }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardLabel("In this file")
                    Text(
                        "${parsed.workouts.size} workouts · $sets sets",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${day.format(parsed.workouts.first().start)} – ${day.format(parsed.workouts.last().start)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p.duplicates > 0) {
                        Text(
                            "${p.duplicates} already imported, they'll be skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (parsed.skippedSets > 0) {
                        Text(
                            "${parsed.skippedSets} timed or distance-only sets (e.g. cardio) have no weight or reps and won't be imported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "exercises_header") {
            Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text("Exercises", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (newCount == 0) "All matched to the library. Tap one to change it."
                    else "$newCount will be added as your own exercises. Tap one to match it to the library instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(parsed.exerciseTitles, key = { it.first }) { (title, count) ->
            val target = p.mapping[title]?.let { Repo.exercise(it) }
            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = { onPick(title) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$count sets", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (target != null) {
                            Text(target.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Pill("New exercise", AccentBright)
                        }
                    }
                }
            }
        }
        item(key = "import") {
            Spacer(Modifier.height(8.dp))
            GradientButton(onClick = onImport, enabled = fresh > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (fresh == 1) "Import 1 workout" else "Import $fresh workouts")
            }
        }
    }
}

/** Library search for what a Hevy exercise should become; the first row keeps it as a new custom exercise. */
@Composable
private fun ExerciseChoiceDialog(hevyTitle: String, onDismiss: () -> Unit, onChoose: (String?) -> Unit) {
    var query by remember { mutableStateOf(hevyTitle.substringBefore(" (")) }
    val q = query.trim().lowercase()
    val words = q.split(" ").filter { it.isNotEmpty() }
    val results = Repo.exercises.filter { e -> words.all { e.name.lowercase().contains(it) } }.take(60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(hevyTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search the library") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    item(key = "new") {
                        Row(
                            Modifier.fillMaxWidth().clickable { onChoose(null) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = AccentBright)
                            Spacer(Modifier.width(12.dp))
                            Text("Add as a new exercise", color = AccentBright, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    items(results, key = { it.id }) { e: Exercise ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onChoose(e.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseThumbnail(e, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
