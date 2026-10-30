package com.tooheavy.app.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.Exercise
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.titleCase
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.ExerciseThumbnail
import com.tooheavy.app.ui.common.ScreenHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(nav: NavController) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ScreenHeader("Library", overline = "${Repo.exercises.size} exercises") },
    ) { pad ->
        ExerciseBrowser(
            selectedIds = null,
            onRowClick = { nav.navigate(Routes.exercise(it.id)) },
            onInfoClick = null,
            modifier = Modifier.padding(pad).fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(nav: NavController, target: String) {
    var selected by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    val n = selected.size

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text("Add Exercises", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Button(
                    onClick = {
                        val ids = selected.toList()
                        if (target == Routes.PICK_FOR_ACTIVE) Repo.addExercisesToActive(ids)
                        else Repo.addExercisesToDraft(ids)
                        nav.popBackStack()
                    },
                    enabled = n > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (n == 1) "Add 1 exercise" else "Add $n exercises")
                }
            }
        },
    ) { pad ->
        ExerciseBrowser(
            selectedIds = selected,
            onRowClick = { ex ->
                val next = ArrayList(selected)
                if (!next.remove(ex.id)) next.add(ex.id)
                selected = next
            },
            onInfoClick = { nav.navigate(Routes.exercise(it.id)) },
            modifier = Modifier.padding(pad).fillMaxSize(),
        )
    }
}

/** Search + muscle/equipment filters + list. selectedIds == null means plain (non-selecting) mode. */
@Composable
private fun ExerciseBrowser(
    selectedIds: List<String>?,
    onRowClick: (Exercise) -> Unit,
    onInfoClick: ((Exercise) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    val muscles = remember { Repo.muscles() }
    val equipments = remember { Repo.equipments() }

    val m = muscle
    val e = equipment
    val filtered = remember(query, m, e) {
        val q = query.trim().lowercase()
        Repo.exercises.filter { ex ->
            (q.isEmpty() || ex.name.lowercase().contains(q)) &&
                (m == null || ex.primaryMuscles.contains(m)) &&
                (e == null || ex.equipment == e)
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = m == null,
                onClick = { muscle = null },
                label = { Text("All muscles") },
            )
            muscles.forEach { name ->
                FilterChip(
                    selected = m == name,
                    onClick = { muscle = if (m == name) null else name },
                    label = { Text(name.titleCase()) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = e == null,
                onClick = { equipment = null },
                label = { Text("All equipment") },
            )
            equipments.forEach { name ->
                FilterChip(
                    selected = e == name,
                    onClick = { equipment = if (e == name) null else name },
                    label = { Text(name.titleCase()) },
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No exercises found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                items(filtered, key = { it.id }) { ex ->
                    val order = selectedIds?.indexOf(ex.id)?.takeIf { it >= 0 }
                    ExerciseListRow(
                        ex = ex,
                        selectable = selectedIds != null,
                        selectedOrder = order,
                        onClick = { onRowClick(ex) },
                        onInfo = onInfoClick?.let { cb -> { cb(ex) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseListRow(
    ex: Exercise,
    selectable: Boolean,
    selectedOrder: Int?,
    onClick: () -> Unit,
    onInfo: (() -> Unit)?,
) {
    val selected = selectedOrder != null
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(ex, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ex.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val muscleLabel = ex.primaryMuscles.firstOrNull()?.titleCase() ?: "Other"
            val equipLabel = ex.equipment?.titleCase() ?: "No equipment"
            Text(
                "$muscleLabel · $equipLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onInfo != null) {
            IconButton(onClick = onInfo) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "Exercise info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selectable) {
            Spacer(Modifier.width(4.dp))
            if (selectedOrder != null) {
                Box(
                    Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${selectedOrder + 1}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Icon(
                    Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
