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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tooheavy.app.data.DayMark
import com.tooheavy.app.data.Extras
import com.tooheavy.app.data.FoodEntry
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.WaterEntry
import com.tooheavy.app.data.clockTime
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.mealTotals
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.splitIntoMeals
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
import com.tooheavy.app.ui.common.SelectAllOutlinedTextField
import com.tooheavy.app.ui.common.StackedBarChart
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Accent
import com.tooheavy.app.ui.theme.OnAccent
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
                    goalKcal = NutritionRepo.dayKcalGoal(date),
                    baseKcal = NutritionRepo.kcalGoalOn(date),
                    carried = NutritionRepo.carriedKcal(date),
                    protein = totals.protein,
                    carbs = totals.carbs,
                    fat = totals.fat,
                    goalProtein = goals.protein,
                    goalCarbs = goals.carbs,
                    goalFat = goals.fat,
                    extras = NutritionRepo.extras(date),
                    mark = NutritionRepo.dayMark(date),
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
    /** The goal before carry-over, and the calories carried into the day ([goalKcal] may hold some back). */
    baseKcal: Int,
    carried: Int,
    protein: Double,
    carbs: Double,
    fat: Double,
    goalProtein: Int,
    goalCarbs: Int,
    goalFat: Int,
    extras: Extras,
    /** DayMark of the day, null for a normal day. */
    mark: String?,
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
            if (mark != null) DayMarkNote(mark)
            if (carried != 0) {
                Text(
                    carryOverNote(goalKcal, baseKcal, carried),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/** What a marked day counts as. */
@Composable
private fun DayMarkNote(mark: String) {
    val untracked = mark == DayMark.UNTRACKED
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (untracked) Icons.Filled.DoNotDisturbOn else Icons.Filled.EventBusy,
            contentDescription = null,
            tint = Ember,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (untracked) {
                "Not properly tracked: this day doesn't count for the carry-over, the coach or your averages."
            } else {
                "Left empty on purpose: this day counts as 0 kcal eaten" +
                    (if (NutritionRepo.carryOver) ", so its goal carries over to the next days." else ".")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "300 kcal less than your 2,000 kcal goal, for eating over it on the days before." */
private fun carryOverNote(goalKcal: Int, baseKcal: Int, carried: Int): String {
    fun kcal(v: Int) = kcalText(v.toDouble())
    val shift = goalKcal - baseKcal
    // Above 0 when the minimum goal holds back part of what was eaten over.
    val held = shift - carried
    val base = kcal(baseKcal)
    return when {
        shift > 0 -> "${kcal(shift)} kcal more than your $base kcal goal, for eating under it on the days before."
        held <= 0 -> "${kcal(-shift)} kcal less than your $base kcal goal, for eating over it on the days before."
        shift < 0 -> "${kcal(-shift)} kcal less than your $base kcal goal, for eating over it on the days before. " +
            "The other ${kcal(held)} kcal carry on to the next days, since the goal doesn't go below ${kcal(goalKcal)} kcal."
        else -> "You ate ${kcal(held)} kcal over your goal on the days before. They carry on to the next days, " +
            "since the goal doesn't go below $base kcal."
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

@Composable
private fun TimelineCard(nav: NavController, date: String) {
    val items = (NutritionRepo.entriesFor(date).map { TimelineItem.Eaten(it) } +
        NutritionRepo.waterFor(date).map { TimelineItem.Drank(it) }).sortedBy { it.time }
    val yesterday = shiftDate(date, -1)
    val noFood = items.none { it is TimelineItem.Eaten }
    val canCopy = noFood && NutritionRepo.entriesFor(yesterday).isNotEmpty()
    val mark = NutritionRepo.dayMark(date)
    // Days after today can't be marked: they haven't happened yet.
    val canMark = date <= today()
    val canMarkEmpty = canMark && noFood && mark == null
    // Foods picked to log again (long-press one to start); cleared when the day changes.
    var selected by remember(date) { mutableStateOf(emptySet<String>()) }
    val context = LocalContext.current

    /** Logs [ids] again today at the current time and says so. */
    fun logAgain(ids: Set<String>) {
        val n = NutritionRepo.logAgainNow(ids)
        selected = emptySet()
        if (n > 0) {
            val what = if (n == 1) "1 food" else "$n foods"
            Toast.makeText(context, "Added $what to today at ${clockTime(System.currentTimeMillis())}", Toast.LENGTH_SHORT).show()
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (selected.isNotEmpty()) {
                // Selecting foods: how many, and log them again now.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                    }
                    Text(
                        "${selected.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { logAgain(selected) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add again now")
                    }
                }
                Text(
                    "Tap foods, or a meal's total, to add or remove them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            } else Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Timeline", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                when (mark) {
                    DayMark.UNTRACKED -> Pill("Not tracked", Ember)
                    DayMark.EMPTY -> Pill("Left empty", Ember)
                }
                IconButton(onClick = { nav.navigate(Routes.foodSearch()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add food", tint = AccentBright)
                }
                if (canMark) DayMarkMenu(date, mark, noFood)
            }
            if (selected.isEmpty() && (items.isEmpty() || canCopy || canMarkEmpty)) {
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
                    if (canMarkEmpty) {
                        TextButton(onClick = { NutritionRepo.setDayMark(date, DayMark.EMPTY) }) {
                            Text(
                                "Left empty",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Meals: entries with no long gap between them. A meal of several foods gets a line with its total,
            // which carries the meal's time.
            val meals = splitIntoMeals(items, { it.time })
            meals.forEachIndexed { m, meal ->
                val foods = meal.mapNotNull { (it as? TimelineItem.Eaten)?.entry }
                val withTotal = foods.size >= 2
                if (withTotal) {
                    val ids = foods.map { it.id }.toSet()
                    MealTotalRow(
                        time = meal.first().time,
                        foods = foods,
                        first = m == 0,
                        selected = selected.containsAll(ids),
                        selecting = selected.isNotEmpty(),
                        // Picks the whole meal, or drops it when it is all picked already.
                        onToggle = { selected = if (selected.containsAll(ids)) selected - ids else selected + ids },
                    )
                }
                meal.forEachIndexed { i, item ->
                    TimelineRow(
                        item = item,
                        timeLabel = if (i == 0 && !withTotal) clockTime(item.time) else null,
                        first = m == 0 && i == 0 && !withTotal,
                        last = m == meals.lastIndex && i == meal.lastIndex,
                        selected = item.id in selected,
                        selecting = selected.isNotEmpty(),
                        onToggle = { selected = if (item.id in selected) selected - item.id else selected + item.id },
                        onLogAgain = { logAgain(setOf(item.id)) },
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
            }
            if (items.isNotEmpty()) Spacer(Modifier.height(6.dp))
        }
    }
}

/** Marks the day as not properly tracked or as left empty on purpose; picking the current mark clears it. */
@Composable
private fun DayMarkMenu(date: String, mark: String?, noFood: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Day options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun Item(value: String, label: String, hint: String, enabled: Boolean = true) {
                val selected = mark == value
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label)
                            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Filled.Check else if (value == DayMark.UNTRACKED) Icons.Filled.DoNotDisturbOn else Icons.Filled.EventBusy,
                            contentDescription = null,
                            tint = if (selected) AccentBright else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    enabled = enabled || selected,
                    onClick = {
                        open = false
                        NutritionRepo.setDayMark(date, if (selected) null else value)
                    },
                )
            }
            Item(DayMark.UNTRACKED, "Not properly tracked", "Not used for the carry-over, the coach or averages")
            Item(
                DayMark.EMPTY, "Left empty on purpose",
                if (noFood) "Counts as 0 kcal eaten" else "Only for days without food logged",
                enabled = noFood,
            )
        }
    }
}

/**
 * The line above a meal of several foods: its time and what it adds up to. Long-press it (or tap it while picking
 * foods) to pick the whole meal, e.g. to log it again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealTotalRow(
    time: Long,
    foods: List<FoodEntry>,
    first: Boolean,
    /** All the meal's foods are picked; [selecting] while any food is. */
    selected: Boolean,
    selecting: Boolean,
    onToggle: () -> Unit,
) {
    val total = mealTotals(foods)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (selected) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .combinedClickable(onClick = { if (selecting) onToggle() }, onLongClick = onToggle)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            clockTime(time),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        // Rail, as on the food rows, with the meal's dot.
        Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight()) {
                Box(Modifier.weight(1f).width(1.dp).background(if (first) Color.Transparent else Hairline))
                Box(Modifier.weight(1f).width(1.dp).background(Hairline))
            }
            if (selected) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(AccentBright), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = OnAccent, modifier = Modifier.size(12.dp))
                }
            } else {
                Box(Modifier.size(9.dp).clip(CircleShape).background(AccentBright))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(
                "Meal · ${foods.size} foods",
                style = MaterialTheme.typography.labelLarge,
                color = AccentBright,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "P ${formatNutrient(total.protein)} · C ${formatNutrient(total.carbs)} · F ${formatNutrient(total.fat)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${total.kcal.roundToInt()}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = AccentBright,
        )
        // Where the food rows have their menu button, so the calories line up.
        Spacer(Modifier.width(40.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    item: TimelineItem,
    timeLabel: String?,
    first: Boolean,
    last: Boolean,
    /** Picked to log again; [selecting] while any food is. */
    selected: Boolean,
    selecting: Boolean,
    onToggle: () -> Unit,
    onLogAgain: () -> Unit,
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
            .background(if (selected) Accent.copy(alpha = 0.16f) else Color.Transparent)
            // Long-press a food to start picking foods to log again; while picking, a tap adds or removes one.
            .combinedClickable(
                onClick = { if (selecting) { if (entry != null) onToggle() } else onClick() },
                onLongClick = { if (entry != null) onToggle() else menuOpen = true },
            )
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
            if (selected) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(AccentBright), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = OnAccent, modifier = Modifier.size(12.dp))
                }
            } else {
                Box(
                    Modifier
                        .size(if (timeLabel != null) 9.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (water != null) ChartColors.Water else AccentBright),
                )
            }
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
                    DropdownMenuItem(
                        text = { Text("Add again now") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onLogAgain()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Select") },
                        leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onToggle()
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

/** Water drunk on [date]; [goalMl] of 0 means no daily goal: just the amount, no target or meter. */
@Composable
private fun WaterCard(date: String, goalMl: Int) {
    val total = NutritionRepo.waterTotal(date)
    var custom by remember { mutableStateOf(false) }
    var goalDialog by remember { mutableStateOf(false) }
    val hasGoal = goalMl > 0
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardLabel("Hydration", Modifier.weight(1f), icon = Icons.Filled.WaterDrop)
                if (hasGoal && total >= goalMl) Pill("Goal reached", ChartColors.Water)
                TextButton(onClick = { goalDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (hasGoal) "Goal" else "Set goal", style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                BigNumber(litres(total), "L", size = 30)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hasGoal) "of ${litres(goalMl)} L" else "today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            if (hasGoal) Meter(total.toFloat() / goalMl, ChartColors.Water)
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
    if (goalDialog) WaterGoalDialog(goalMl) { goalDialog = false }
    if (custom) {
        var text by remember { mutableStateOf("") }
        val ml = text.trim().toIntOrNull()
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text("Add water") },
            text = {
                SelectAllOutlinedTextField(
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

/** Turns the daily water goal on or off and sets it, in litres. */
@Composable
private fun WaterGoalDialog(goalMl: Int, onDismiss: () -> Unit) {
    var on by remember { mutableStateOf(goalMl > 0) }
    var text by remember { mutableStateOf(litres(if (goalMl > 0) goalMl else 2500)) }
    val ml = text.replace(',', '.').toDoubleOrNull()?.let { (it * 1000).roundToInt() }?.takeIf { it in 100..20_000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Water goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily goal", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (on) "Shows how far you are each day" else "Just log what you drink",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = on, onCheckedChange = { on = it })
                }
                if (on) {
                    SelectAllOutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                        label = { Text("Litres a day") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !on || ml != null,
                onClick = {
                    NutritionRepo.updateWaterGoal(if (on) ml ?: 0 else 0)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** 1250 -> "1.25", 2000 -> "2" */
private fun litres(ml: Int): String =
    java.math.BigDecimal(ml / 1000.0).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

@Composable
private fun WeekCard(date: String, goalKcal: Int) {
    val days = (6 downTo 0).map { shiftDate(date, -it) }
    val kcals = days.map { NutritionRepo.totals(it).kcal }
    val labels = days.map { weekdayInitial(it) }
    // Average over the days that count: not the ones marked not properly tracked, but the ones left empty on purpose.
    val logged = days.mapNotNull { NutritionRepo.countedKcalOn(it) }
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
