package com.tooheavy.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.DashboardWidget
import com.tooheavy.app.data.Repo
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.BrandMark
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.IconBubble
import com.tooheavy.app.ui.theme.BrandColors
import com.tooheavy.app.ui.theme.Hairline
import com.tooheavy.app.ui.theme.Overline
import com.tooheavy.app.ui.theme.Surface1
import com.tooheavy.app.ui.theme.Surface2
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Widgets packed into rows: a wide widget alone, half widgets two by two. */
private fun pack(layout: List<DashboardWidget>): List<List<IndexedValue<DashboardWidget>>> {
    val rows = ArrayList<List<IndexedValue<DashboardWidget>>>()
    var i = 0
    val items = layout.withIndex().toList()
    while (i < items.size) {
        val a = items[i]
        val b = items.getOrNull(i + 1)
        if (!a.value.wide && b != null && !b.value.wide) {
            rows.add(listOf(a, b))
            i += 2
        } else {
            rows.add(listOf(a))
            i += 1
        }
    }
    return rows
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(nav: NavController, startEditing: Boolean = false) {
    var editing by rememberSaveable { mutableStateOf(startEditing) }
    var showAdd by remember { mutableStateOf(false) }
    val layout = (Repo.settings.dashboard ?: DashboardWidgets.defaultLayout).filter { DashboardWidgets.def(it.id) != null }

    fun save(list: List<DashboardWidget>) = Repo.updateDashboard(list)
    fun move(index: Int, delta: Int) {
        val j = index + delta
        if (j !in layout.indices) return
        val l = layout.toMutableList()
        l[index] = layout[j].also { l[j] = layout[index] }
        save(l)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()).uppercase(),
                        style = Overline, color = TextMuted,
                    )
                    Text(
                        if (editing) "Customize" else greeting(),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (editing) {
                    GradientButton(onClick = { editing = false }, height = 38.dp, contentPadding = PaddingValues(horizontal = 18.dp)) {
                        Text("Done")
                    }
                    Spacer(Modifier.width(8.dp))
                } else {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Tune, contentDescription = "Customize dashboard") }
                    IconButton(onClick = { nav.navigate(Routes.PROFILE) }) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Surface2).border(1.dp, Hairline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Person, contentDescription = "Profile and settings", modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        },
    ) { pad ->
        val rows = pack(layout)
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                item(key = "hint") {
                    Text(
                        "Move, resize or remove widgets, and add new ones at the bottom. Half-width widgets sit side by side.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(rows, key = { row -> row.joinToString("+") { it.value.id } }) { row ->
                Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (index, w) ->
                        Column(Modifier.weight(1f)) {
                            AnimatedVisibility(editing) {
                                EditBar(
                                    title = DashboardWidgets.def(w.id)?.title.orEmpty(),
                                    wide = w.wide,
                                    canUp = index > 0,
                                    canDown = index < layout.lastIndex,
                                    onUp = { move(index, -1) },
                                    onDown = { move(index, 1) },
                                    onResize = { save(layout.mapIndexed { i, x -> if (i == index) x.copy(wide = !x.wide) else x }) },
                                    onRemove = { save(layout.filterIndexed { i, _ -> i != index }) },
                                )
                            }
                            WidgetBody(w, nav)
                        }
                    }
                    if (row.size == 1 && !row[0].value.wide) Spacer(Modifier.weight(1f))
                }
            }
            item(key = "footer") {
                if (editing) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .border(1.5.dp, androidx.compose.ui.graphics.Brush.horizontalGradient(BrandColors), RoundedCornerShape(22.dp))
                                .clickable { showAdd = true },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add widget", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { Repo.updateDashboard(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset to default layout")
                        }
                    }
                } else {
                    TextButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Customize dashboard")
                    }
                }
            }
        }
    }

    if (showAdd) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val available = DashboardWidgets.all.filter { d -> layout.none { it.id == d.id } }
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = sheet,
            containerColor = Surface1,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Add a widget", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                Text(
                    if (available.isEmpty()) "Every widget is already on your dashboard." else "Tap to add it to the bottom of your dashboard.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    items(available, key = { it.id }) { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    save(layout + DashboardWidget(d.id, wide = d.id !in halfByDefault))
                                    showAdd = false
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBubble(d.icon, size = 42.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.title, style = MaterialTheme.typography.titleMedium)
                                Text(d.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Icon(Icons.Filled.Add, contentDescription = "Add ${d.title}", tint = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** Widgets that read best at half width when first added. */
private val halfByDefault = setOf(
    DashboardWidgets.STREAK, DashboardWidgets.CALORIES, DashboardWidgets.MACROS, DashboardWidgets.EXPENDITURE,
)

@Composable
private fun EditBar(
    title: String,
    wide: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Spacer(Modifier.weight(1f))
        }
        SmallIcon(Icons.Filled.ArrowUpward, "Move up", canUp, onUp)
        SmallIcon(Icons.Filled.ArrowDownward, "Move down", canDown, onDown)
        SmallIcon(if (wide) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull, if (wide) "Make half width" else "Make full width", true, onResize)
        SmallIcon(Icons.Filled.Close, "Remove", true, onRemove)
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    }
}

