package com.tooheavy.app.ui.nutrition

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.Extras
import com.tooheavy.app.data.FoodEntry
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.WaterEntry
import com.tooheavy.app.data.clockTime
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.today
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.BigNumber
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.Pill
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.common.ProgressRing
import com.tooheavy.app.ui.common.ScreenHeader
import com.tooheavy.app.ui.common.StackedBarChart
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.ChartColors
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Ember
import com.tooheavy.app.ui.theme.Hairline
import com.tooheavy.app.ui.theme.StatNumber
import com.tooheavy.app.ui.theme.Surface1
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ProteinColor = ChartColors.Protein
private val CarbsColor = ChartColors.Carbs
private val FatColor = ChartColors.Fat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(nav: NavController) {
    val date = NutritionRepo.selectedDate
    val goals = NutritionRepo.goals
    val totals = NutritionRepo.totals(date)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ScreenHeader("Food", overline = "${kcalText(totals.kcal)} kcal eaten") {
                IconButton(onClick = { nav.navigate(Routes.NUTRITION_GOALS) }) {
                    Icon(Icons.Filled.Flag, contentDescription = "Goals")
                }
            }
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
                    extras = NutritionRepo.extras(date),
                )
            }
            item(key = "timeline") {
                TimelineCard(nav, date)
            }
            item(key = "water") {
                WaterCard(date, goals.waterMl)
            }
            item(key = "week") {
                WeekCard(date, goals.kcal)
            }
            item(key = "macro_week") {
                MacroWeekCard(date)
            }
        }
    }
}

@Composable
private fun DateSwitcher(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp)),
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
    extras: Extras,
) {
    val remaining = goalKcal - consumed
    val over = remaining < 0
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
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
            val known = listOfNotNull(
                extras.fiber?.let { "Fiber ${formatNutrient(it)} g" },
                extras.sugar?.let { "Sugar ${formatNutrient(it)} g" },
                extras.salt?.let { "Salt ${formatNutrient(it)} g" },
            )
            if (known.isNotEmpty()) {
                Text(
                    known.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalorieRing(consumed: Double, goal: Int, remaining: Double) {
    val over = remaining < 0
    ProgressRing(if (goal > 0) (consumed / goal).toFloat() else 0f, size = 136.dp, thickness = 13.dp, over = over) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BigNumber(kcalText(abs(remaining)), size = 30, color = if (over) Danger else MaterialTheme.colorScheme.onSurface)
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
        Text(value, style = StatNumber.copy(fontSize = 20.sp), color = valueColor)
    }
}

@Composable
private fun MacroBar(label: String, value: Double, goal: Int, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                "${formatNutrient(value)} / $goal g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Meter(if (goal > 0) (value / goal).toFloat() else 0f, color)
    }
}

/** A food or a glass of water on the day's timeline. */
private sealed interface TimelineItem {
    val time: Long
    val id: String

    data class Eaten(val entry: FoodEntry) : TimelineItem {
        override val time get() = entry.createdAt
        override val id get() = entry.id
    }

    data class Drank(val water: WaterEntry) : TimelineItem {
        override val time get() = water.createdAt
        override val id get() = water.id
    }
}

/** Entries closer together than this share one time label, like a meal. */
private const val TIMELINE_GROUP_MS = 30 * 60_000L

@Composable
private fun TimelineCard(nav: NavController, date: String) {
    val items = (NutritionRepo.entriesFor(date).map { TimelineItem.Eaten(it) } +
        NutritionRepo.waterFor(date).map { TimelineItem.Drank(it) }).sortedBy { it.time }
    val yesterday = shiftDate(date, -1)
    val canCopy = items.none { it is TimelineItem.Eaten } && NutritionRepo.entriesFor(yesterday).isNotEmpty()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Timeline", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.navigate(Routes.foodSearch()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add food", tint = AccentBright)
                }
            }
            if (items.isEmpty() || canCopy) {
                Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                    TextButton(onClick = { nav.navigate(Routes.foodSearch()) }) {
                        Text("Add food", style = MaterialTheme.typography.labelLarge)
                    }
                    if (canCopy) {
                        TextButton(onClick = { NutritionRepo.copyDay(yesterday, date) }) {
                            Text(
                                "Copy yesterday",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            var lastShown: Long? = null
            items.forEachIndexed { i, item ->
                val showTime = lastShown.let { it == null || item.time - it > TIMELINE_GROUP_MS }
                if (showTime) lastShown = item.time
                TimelineRow(
                    item = item,
                    timeLabel = if (showTime) clockTime(item.time) else null,
                    first = i == 0,
                    last = i == items.lastIndex,
                    onClick = {
                        if (item is TimelineItem.Eaten) nav.navigate(Routes.editFoodEntry(item.entry.id))
                    },
                    onDelete = {
                        when (item) {
                            is TimelineItem.Eaten -> NutritionRepo.deleteEntry(item.id)
                            is TimelineItem.Drank -> NutritionRepo.deleteWater(item.id)
                        }
                    },
                )
            }
            if (items.isNotEmpty()) Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    item: TimelineItem,
    timeLabel: String?,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val entry = (item as? TimelineItem.Eaten)?.entry
    val water = (item as? TimelineItem.Drank)?.water
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            timeLabel.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        // Rail: a hairline through the day with a dot per item (blue for water).
        Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight()) {
                Box(Modifier.weight(1f).width(1.dp).background(if (first) Color.Transparent else Hairline))
                Box(Modifier.weight(1f).width(1.dp).background(if (last) Color.Transparent else Hairline))
            }
            Box(
                Modifier
                    .size(if (timeLabel != null) 9.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (water != null) ChartColors.Water else AccentBright),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            if (entry != null) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (entry.amountLabel.isNotBlank()) append(entry.amountLabel).append(" · ")
                        append("P ${formatNutrient(entry.protein)} · C ${formatNutrient(entry.carbs)} · F ${formatNutrient(entry.fat)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (water != null) {
                Text(
                    "${String.format(Locale.US, "%,d", water.ml)} ml water",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entry != null) {
            Spacer(Modifier.width(8.dp))
            Text("${entry.kcal.roundToInt()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry != null) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                }
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
private fun WaterCard(date: String, goalMl: Int) {
    val total = NutritionRepo.waterTotal(date)
    var custom by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardLabel("Hydration", Modifier.weight(1f), icon = Icons.Filled.WaterDrop)
                if (total >= goalMl && goalMl > 0) Pill("Goal reached", ChartColors.Water)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                BigNumber(litres(total), "L", size = 30)
                Spacer(Modifier.width(8.dp))
                Text(
                    "of ${litres(goalMl)} L",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Meter(if (goalMl > 0) total.toFloat() / goalMl else 0f, ChartColors.Water)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(250, 500).forEach { ml ->
                    OutlinedButton(
                        onClick = { NutritionRepo.addWater(ml, date) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) { Text("+$ml ml", maxLines = 1) }
                }
                OutlinedButton(
                    onClick = { custom = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("Other", maxLines = 1) }
                val last = NutritionRepo.waterFor(date).lastOrNull()
                IconButton(onClick = { last?.let { NutritionRepo.deleteWater(it.id) } }, enabled = last != null) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last drink")
                }
            }
        }
    }
    if (custom) {
        var text by remember { mutableStateOf("") }
        val ml = text.trim().toIntOrNull()
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text("Add water") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                    label = { Text("Amount (ml)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = ml != null && ml > 0,
                    onClick = {
                        custom = false
                        NutritionRepo.addWater(ml ?: 0, date)
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } },
        )
    }
}

/** 1250 -> "1.25", 2000 -> "2" */
private fun litres(ml: Int): String =
    java.math.BigDecimal(ml / 1000.0).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

@Composable
private fun WeekCard(date: String, goalKcal: Int) {
    val days = (6 downTo 0).map { shiftDate(date, -it) }
    val kcals = days.map { NutritionRepo.totals(it).kcal }
    val labels = days.map { weekdayInitial(it) }
    val logged = kcals.filter { it > 0.0 }
    ChartCard(
        "Last 7 days",
        value = if (logged.isEmpty()) null else kcalText(logged.average()),
        unit = if (logged.isEmpty()) null else "kcal / day avg",
        caption = "${logged.size} of 7 days logged · goal ${kcalText(goalKcal.toDouble())}",
        icon = Icons.Filled.LocalFireDepartment,
    ) {
        BarChart(
            values = kcals, labels = labels, goal = goalKcal.toDouble(),
            highlight = days.indexOf(date),
            tooltipLabels = days.map { friendlyDate(it) },
        )
    }
}

@Composable
private fun MacroWeekCard(date: String) {
    val days = (6 downTo 0).map { shiftDate(date, -it) }
    val totals = days.map { NutritionRepo.totals(it) }
    ChartCard("Macro split", caption = "Calories from protein, carbs and fat", icon = Icons.Filled.PieChart) {
        StackedBarChart(
            stacks = totals.map { listOf(it.protein * 4, it.carbs * 4, it.fat * 9) },
            labels = days.map { weekdayInitial(it) },
            colors = listOf(ProteinColor, CarbsColor, FatColor),
            names = listOf("Protein", "Carbs", "Fat"),
            goal = NutritionRepo.goals.kcal.toDouble(),
            tooltipLabels = days.map { friendlyDate(it) },
        )
    }
}

private fun kcalText(v: Double): String = String.format(Locale.US, "%,d", v.roundToInt())

private fun weekdayInitial(date: String): String {
    val d = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) }.getOrNull() ?: return ""
    return SimpleDateFormat("EEE", Locale.US).format(d).take(1)
}
