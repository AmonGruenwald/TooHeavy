package com.tooheavy.app.ui.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.FoodEntry
import com.tooheavy.app.data.Meal
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.today
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.Danger
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ProteinColor = Color(0xFF4FC3F7)
private val CarbsColor = Color(0xFFFFB74D)
private val FatColor = Color(0xFFE57373)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(nav: NavController) {
    val date = NutritionRepo.selectedDate
    val goals = NutritionRepo.goals
    val totals = NutritionRepo.totals(date)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Food", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.NUTRITION_GOALS) }) {
                        Icon(Icons.Filled.Flag, contentDescription = "Goals")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "date") {
                DateSwitcher(date)
            }
            item(key = "coach") {
                CoachSummaryCard { nav.navigate(Routes.COACH) }
            }
            item(key = "summary") {
                SummaryCard(
                    consumed = totals.kcal,
                    goalKcal = goals.kcal,
                    protein = totals.protein,
                    carbs = totals.carbs,
                    fat = totals.fat,
                    goalProtein = goals.protein,
                    goalCarbs = goals.carbs,
                    goalFat = goals.fat,
                )
            }
            items(Meal.all, key = { it }) { meal ->
                MealCard(nav, date, meal)
            }
            item(key = "week") {
                WeekCard(date)
            }
        }
    }
}

@Composable
private fun DateSwitcher(date: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { NutritionRepo.selectedDate = shiftDate(date, -1) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
        }
        Text(
            friendlyDate(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { NutritionRepo.selectedDate = today() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        IconButton(onClick = { NutritionRepo.selectedDate = shiftDate(date, 1) }) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun SummaryCard(
    consumed: Double,
    goalKcal: Int,
    protein: Double,
    carbs: Double,
    fat: Double,
    goalProtein: Int,
    goalCarbs: Int,
    goalFat: Int,
) {
    val remaining = goalKcal - consumed
    val over = remaining < 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalorieRing(consumed = consumed, goal = goalKcal, remaining = remaining)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatLine("Goal", kcalText(goalKcal.toDouble()), MaterialTheme.colorScheme.onSurface)
                    StatLine("Eaten", kcalText(consumed), MaterialTheme.colorScheme.onSurface)
                    StatLine(
                        if (over) "Over" else "Remaining",
                        kcalText(abs(remaining)),
                        if (over) Danger else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroBar("Protein", protein, goalProtein, ProteinColor)
                MacroBar("Carbs", carbs, goalCarbs, CarbsColor)
                MacroBar("Fat", fat, goalFat, FatColor)
            }
        }
    }
}

@Composable
private fun CalorieRing(consumed: Double, goal: Int, remaining: Double) {
    val over = remaining < 0
    val progress = if (goal > 0) (consumed / goal).toFloat().coerceIn(0f, 1f) else 0f
    val ringColor = if (over) Danger else Accent
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeW = 12.dp.toPx()
            val inset = strokeW / 2
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW),
            )
            if (progress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                kcalText(abs(remaining)),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (over) Danger else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                if (over) "kcal over" else "kcal left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun MacroBar(label: String, value: Double, goal: Int, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${formatNutrient(value)} / $goal g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun MealCard(nav: NavController, date: String, meal: String) {
    val entries = NutritionRepo.entriesFor(date, meal)
    val yesterday = shiftDate(date, -1)
    val yesterdayHasEntries = entries.isEmpty() && NutritionRepo.entriesFor(yesterday, meal).isNotEmpty()
    val mealKcal = entries.sumOf { it.kcal }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Meal.label(meal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${kcalText(mealKcal)} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { nav.navigate(Routes.foodSearch(meal)) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add food to ${Meal.label(meal)}", tint = Accent)
                }
            }
            if (entries.isEmpty()) {
                if (yesterdayHasEntries) {
                    TextButton(
                        onClick = { NutritionRepo.copyMeal(yesterday, date, meal) },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Copy from yesterday", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(
                        onClick = { nav.navigate(Routes.foodSearch(meal)) },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            "Add food",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                entries.forEach { e ->
                    EntryRow(
                        entry = e,
                        onClick = { nav.navigate(Routes.editFoodEntry(e.id)) },
                        onEdit = { nav.navigate(Routes.editFoodEntry(e.id)) },
                        onDelete = { NutritionRepo.deleteEntry(e.id) },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = buildString {
        if (entry.amountLabel.isNotBlank()) append(entry.amountLabel).append(" · ")
        append("P ${formatNutrient(entry.protein)} · C ${formatNutrient(entry.carbs)} · F ${formatNutrient(entry.fat)}")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${entry.kcal.roundToInt()}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Danger) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Danger) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekCard(date: String) {
    val days = (6 downTo 0).map { shiftDate(date, -it) }
    val kcals = days.map { NutritionRepo.totals(it).kcal }
    val labels = days.map { weekdayInitial(it) }
    val logged = kcals.filter { it > 0.0 }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BarChart(values = kcals, labels = labels)
            Text(
                if (logged.isEmpty()) "No food logged in the last 7 days"
                else "Avg ${kcalText(logged.average())} kcal/day · ${logged.size} of 7 days logged",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun kcalText(v: Double): String = String.format(Locale.US, "%,d", v.roundToInt())

private fun weekdayInitial(date: String): String {
    val d = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull() ?: return ""
    return SimpleDateFormat("EEE", Locale.US).format(d).take(1)
}
