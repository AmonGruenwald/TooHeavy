package com.tooheavy.app.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Food
import com.tooheavy.app.data.FoodEntry
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.Portion
import com.tooheavy.app.data.clockTime
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.timeOn
import com.tooheavy.app.data.toIngredient
import java.util.Calendar
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.theme.Danger
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private val ProteinColor = Color(0xFF4FC3F7)
private val CarbsColor = Color(0xFFFFB74D)
private val FatColor = Color(0xFFE57373)

private fun amountParse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** 100.0 -> "100", 1.5 -> "1.5", 12.345 -> "12.35" (plain text for editable fields and labels). */
private fun amountNumText(v: Double): String =
    BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private class AmountInit(val unitIndex: Int, val quantity: String)

/** unitIndex -1 means grams, otherwise an index into [portions]. */
private fun amountInit(portions: List<Portion>, entry: FoodEntry?): AmountInit {
    if (entry == null) {
        return if (portions.isNotEmpty()) AmountInit(0, "1") else AmountInit(-1, "100")
    }
    val label = entry.amountLabel
    if (label.endsWith(" g")) return AmountInit(-1, amountNumText(entry.grams))
    val whole = portions.indexOfFirst { it.label == label }
    if (whole >= 0) return AmountInit(whole, "1")
    val parts = label.split(" × ", limit = 2)
    if (parts.size == 2) {
        val q = amountParse(parts[0])
        val idx = portions.indexOfFirst { it.label == parts[1] }
        if (q != null && idx >= 0) return AmountInit(idx, amountNumText(q))
    }
    return AmountInit(-1, amountNumText(entry.grams))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountScaffold(
    nav: NavController,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
            )
        },
        content = content,
    )
}

@Composable
private fun AmountMessage(nav: NavController, title: String, loading: Boolean, text: String) {
    AmountScaffold(nav, title) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Time of day an entry was eaten; tapping opens a clock picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatenAtField(date: String, time: Long, onChange: (Long) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmountSectionLabel("Time")
        FilterChip(
            selected = true,
            onClick = { picking = true },
            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text("${clockTime(time)} · ${friendlyDate(date)}") },
        )
    }
    if (picking) {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val state = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Eaten at") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onChange(timeOn(date, state.hour, state.minute))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    picking = false
                    onChange(timeOn(date))
                }) { Text("Now") }
            },
        )
    }
}

@Composable
fun FoodAmountScreen(nav: NavController, foodKey: String?, mode: String, entryId: String?) {
    val loaded = NutritionRepo.foodsLoaded
    val entry = remember(entryId) { entryId?.let { NutritionRepo.entry(it) } }
    val resolvedKey = if (entryId != null) entry?.foodKey else foodKey
    // Re-resolve when stored foods change (e.g. after "Edit food"); equal lists don't trigger a recompute.
    val localFoods = NutritionRepo.customFoods.toList()
    val food = remember(resolvedKey, loaded, localFoods) { resolvedKey?.let { NutritionRepo.food(it) } }
    val title = when {
        entryId != null -> "Edit entry"
        mode == Routes.MODE_INGREDIENT -> "Add ingredient"
        else -> "Add food"
    }

    when {
        entryId != null && entry == null -> AmountMessage(nav, title, loading = false, text = "Entry not found")
        entry != null && resolvedKey == null -> AmountRawEditor(nav, entry)
        food == null && !loaded -> AmountMessage(nav, title, loading = true, text = "Loading food database…")
        food == null && entry != null -> AmountRawEditor(nav, entry)
        food == null -> AmountMessage(nav, title, loading = false, text = "Food not found")
        else -> AmountEditor(nav, food, mode, entry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountEditor(nav: NavController, food: Food, mode: String, entry: FoodEntry?) {
    val portions = remember(food) { food.portions.orEmpty().filter { it.grams > 0 } }
    val init = remember(food.key, entry?.id) { amountInit(portions, entry) }
    var unitIndex by remember { mutableIntStateOf(init.unitIndex) }
    var qtyText by remember { mutableStateOf(init.quantity) }
    val ingredient = mode == Routes.MODE_INGREDIENT && entry == null
    val date = entry?.date ?: NutritionRepo.selectedDate
    var time by remember { mutableLongStateOf(entry?.createdAt ?: timeOn(date)) }

    val unit = portions.getOrNull(unitIndex)
    val qty = amountParse(qtyText)
    val valid = qty != null && qty > 0
    val grams = if (valid) qty!! * (unit?.grams ?: 1.0) else 0.0
    val m = food.macrosFor(grams)
    val isEdit = entry != null

    fun confirm() {
        val q = qty ?: return
        if (q <= 0) return
        val g = q * (unit?.grams ?: 1.0)
        val label = if (unit == null) {
            "${formatNutrient(g)} g"
        } else if (q == 1.0) {
            unit.label
        } else {
            "${amountNumText(q)} × ${unit.label}"
        }
        if (ingredient) {
            NutritionRepo.addIngredient(food.toIngredient(g, label))
            if (!nav.popBackStack(Routes.RECIPE_PATTERN, inclusive = false)) nav.popBackStack()
        } else if (entry == null) {
            NutritionRepo.addEntry(NutritionRepo.makeEntry(food, g, label, date = date, time = time))
            if (!nav.popBackStack(Routes.NUTRITION, inclusive = false)) nav.popBackStack()
        } else {
            NutritionRepo.updateEntry(NutritionRepo.makeEntry(food, g, label, date = entry.date, time = time, id = entry.id))
            nav.popBackStack()
        }
    }

    AmountScaffold(
        nav = nav,
        title = when {
            isEdit -> "Edit entry"
            ingredient -> "Add ingredient"
            else -> "Add food"
        },
        actions = {
            IconButton(onClick = { confirm() }, enabled = valid) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (isEdit) "Save" else "Add",
                    tint = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(food.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                food.brand?.trim()?.takeIf { it.isNotEmpty() }?.let { brand ->
                    Text(
                        brand,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (food.source) {
                            Food.SOURCE_OFF -> "Open Food Facts"
                            Food.SOURCE_CUSTOM -> "Custom food"
                            Food.SOURCE_RECIPE -> "Recipe · ${food.recipe?.ingredients?.size ?: 0} ingredients"
                            else -> "USDA FoodData Central"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (food.isLocal) {
                        TextButton(
                            onClick = { nav.navigate(if (food.isRecipe) Routes.recipe(food.key) else Routes.customFood(food.key)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text(if (food.isRecipe) "Edit recipe" else "Edit food", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }

            // Amount
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountSectionLabel("Amount")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text(if (unit == null) "Grams" else "Quantity") },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(150.dp),
                    )
                    if (unit != null && valid) {
                        Text(
                            "= ${formatNutrient(grams)} g",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = unitIndex < 0,
                        onClick = {
                            unitIndex = -1
                            qtyText = "100"
                        },
                        label = { Text("g") },
                    )
                    portions.forEachIndexed { i, p ->
                        FilterChip(
                            selected = unitIndex == i,
                            onClick = {
                                unitIndex = i
                                qtyText = "1"
                            },
                            label = { Text("${p.label} (${formatNutrient(p.grams)} g)") },
                        )
                    }
                }
            }

            if (!ingredient) EatenAtField(date, time) { time = it }

            // Live nutrition card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            m.kcal.roundToInt().toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "kcal",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    AmountMacroBar(m.protein, m.carbs, m.fat)
                    val pk = m.protein * 4
                    val ck = m.carbs * 4
                    val fk = m.fat * 9
                    val total = pk + ck + fk
                    fun pct(v: Double) = if (total > 0) (v / total * 100).roundToInt() else 0
                    Row(Modifier.fillMaxWidth()) {
                        AmountMacroStat("Protein", m.protein, ProteinColor, pct(pk), Modifier.weight(1f))
                        AmountMacroStat("Carbs", m.carbs, CarbsColor, pct(ck), Modifier.weight(1f))
                        AmountMacroStat("Fat", m.fat, FatColor, pct(fk), Modifier.weight(1f))
                    }
                    val x = food.extrasFor(grams)
                    if (x.fiber != null || x.sugar != null || x.salt != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            x.fiber?.let { AmountDetailRow("Fiber", "${formatNutrient(it)} g") }
                            x.sugar?.let { AmountDetailRow("Sugar", "${formatNutrient(it)} g") }
                            x.salt?.let { AmountDetailRow("Salt", "${formatNutrient(it)} g") }
                        }
                    }
                }
            }

            Button(
                onClick = { confirm() },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    when {
                        isEdit -> "Save changes"
                        ingredient -> "Add to recipe"
                        else -> "Add at ${clockTime(time)}"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (entry != null) {
                TextButton(
                    onClick = {
                        NutritionRepo.deleteEntry(entry.id)
                        nav.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete entry", color = Danger) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Editor for quick-add entries (or entries whose food no longer exists): raw kcal and macros. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountRawEditor(nav: NavController, entry: FoodEntry) {
    var name by remember { mutableStateOf(entry.name) }
    var kcal by remember { mutableStateOf(amountNumText(entry.kcal)) }
    var protein by remember { mutableStateOf(amountNumText(entry.protein)) }
    var carbs by remember { mutableStateOf(amountNumText(entry.carbs)) }
    var fat by remember { mutableStateOf(amountNumText(entry.fat)) }
    var time by remember { mutableLongStateOf(entry.createdAt) }

    val kcalValue = amountParse(kcal)
    val valid = kcalValue != null && kcalValue >= 0
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    fun save() {
        val k = kcalValue ?: return
        NutritionRepo.updateEntry(
            entry.copy(
                name = name.trim().ifBlank { entry.name.ifBlank { "Quick add" } },
                createdAt = time,
                kcal = k,
                protein = amountParse(protein) ?: 0.0,
                carbs = amountParse(carbs) ?: 0.0,
                fat = amountParse(fat) ?: 0.0,
            )
        )
        nav.popBackStack()
    }

    AmountScaffold(
        nav = nav,
        title = "Edit entry",
        actions = {
            IconButton(onClick = { save() }, enabled = valid) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Save",
                    tint = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (entry.foodKey != null) {
                Text(
                    "The original food is no longer available, so the values of this entry are edited directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("Calories") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("Protein (g)") },
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("Carbs (g)") },
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("Fat (g)") },
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            EatenAtField(entry.date, time) { time = it }

            Button(
                onClick = { save() },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Save changes", fontWeight = FontWeight.SemiBold) }

            TextButton(
                onClick = {
                    NutritionRepo.deleteEntry(entry.id)
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete entry", color = Danger) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AmountSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AmountDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Stacked bar of the macro split by kcal share (protein 4, carbs 4, fat 9 kcal/g). */
@Composable
private fun AmountMacroBar(protein: Double, carbs: Double, fat: Double) {
    val pk = protein * 4
    val ck = carbs * 4
    val fk = fat * 9
    val total = pk + ck + fk
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (total > 0) {
            if (pk > 0) Box(Modifier.weight(pk.toFloat()).fillMaxHeight().background(ProteinColor))
            if (ck > 0) Box(Modifier.weight(ck.toFloat()).fillMaxHeight().background(CarbsColor))
            if (fk > 0) Box(Modifier.weight(fk.toFloat()).fillMaxHeight().background(FatColor))
        }
    }
}

@Composable
private fun AmountMacroStat(label: String, grams: Double, color: Color, percent: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${formatNutrient(grams)} g", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("$percent%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
