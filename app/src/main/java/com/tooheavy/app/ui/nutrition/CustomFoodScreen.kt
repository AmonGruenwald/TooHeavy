package com.tooheavy.app.ui.nutrition

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Food
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.Portion
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.theme.Danger
import java.math.BigDecimal
import java.math.RoundingMode

private fun customParse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** 52.0 -> "52", 0.26 -> "0.26" (plain text for editable fields). */
private fun customNumText(v: Double): String =
    BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun customRound(v: Double): Double = Math.round(v * 100.0) / 100.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFoodScaffold(
    nav: NavController,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
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
fun CustomFoodScreen(
    nav: NavController,
    key: String,
    barcode: String? = null,
    meal: String? = null,
    prefillName: String? = null,
    prefillBrand: String? = null,
) {
    val isNew = key == "new"
    // Edit mode works for any food stored on the phone (custom or saved from Open Food Facts), not bundled USDA foods.
    val existing = remember(key) { if (isNew) null else NutritionRepo.food(key)?.takeIf { it.isLocal } }
    if (!isNew && existing == null) {
        CustomFoodScaffold(nav, "Edit food") { pad ->
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    "Food not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        CustomFoodForm(
            nav = nav,
            existing = existing,
            barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
            meal = meal?.takeIf { it.isNotBlank() },
            prefillName = prefillName,
            prefillBrand = prefillBrand,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFoodForm(
    nav: NavController,
    existing: Food?,
    barcode: String?,
    meal: String?,
    prefillName: String?,
    prefillBrand: String?,
) {
    val firstPortion = existing?.portions?.firstOrNull()
    var name by remember { mutableStateOf(existing?.name ?: prefillName.orEmpty()) }
    var brand by remember { mutableStateOf(existing?.brand ?: prefillBrand.orEmpty()) }
    var barcodeText by remember { mutableStateOf(existing?.barcode ?: barcode.orEmpty()) }
    var perServing by remember { mutableStateOf(false) }
    var servingName by remember { mutableStateOf(firstPortion?.label.orEmpty()) }
    var servingGrams by remember { mutableStateOf(firstPortion?.grams?.let { customNumText(it) }.orEmpty()) }
    var kcal by remember { mutableStateOf(existing?.kcal?.let { customNumText(it) }.orEmpty()) }
    var protein by remember { mutableStateOf(existing?.protein?.let { customNumText(it) }.orEmpty()) }
    var carbs by remember { mutableStateOf(existing?.carbs?.let { customNumText(it) }.orEmpty()) }
    var fat by remember { mutableStateOf(existing?.fat?.let { customNumText(it) }.orEmpty()) }
    var fiber by remember { mutableStateOf(existing?.fiber?.let { customNumText(it) }.orEmpty()) }
    var sugar by remember { mutableStateOf(existing?.sugar?.let { customNumText(it) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val barcodeClean = barcodeText.trim()
    // Another stored food that already uses this barcode (scanning it would be ambiguous).
    val duplicate =
        if (barcodeClean.isEmpty()) null else NutritionRepo.foodByBarcode(barcodeClean)?.takeIf { it.key != existing?.key }

    fun save() {
        val n = name.trim()
        if (n.isEmpty()) {
            error = "Enter a name."
            return
        }
        if (duplicate != null) {
            error = "\"${duplicate.name}\" already uses this barcode."
            return
        }
        val k = customParse(kcal)
        if (k == null || k < 0) {
            error = "Enter the calories."
            return
        }
        val servingGiven = servingGrams.isNotBlank()
        val servingNeeded = perServing || servingGiven
        val sg = customParse(servingGrams)
        if (servingNeeded && (sg == null || sg <= 0.0)) {
            error = "Enter a serving size in grams."
            return
        }
        val p = if (protein.isBlank()) 0.0 else customParse(protein)
        val c = if (carbs.isBlank()) 0.0 else customParse(carbs)
        val f = if (fat.isBlank()) 0.0 else customParse(fat)
        if (p == null || c == null || f == null || p < 0 || c < 0 || f < 0) {
            error = "Protein, carbs and fat must be numbers."
            return
        }
        val fiberValue = customParse(fiber)
        val sugarValue = customParse(sugar)
        if ((fiber.isNotBlank() && (fiberValue == null || fiberValue < 0)) ||
            (sugar.isNotBlank() && (sugarValue == null || sugarValue < 0))
        ) {
            error = "Fiber and sugar must be numbers."
            return
        }
        val servingValue = if (servingNeeded) (sg ?: 0.0) else 0.0
        val factor = if (perServing) 100.0 / servingValue else 1.0
        // The form edits the first portion only; keep any further ones (Open Food Facts foods also have "Whole package").
        val restPortions = existing?.portions.orEmpty().drop(1)
        val editedPortion =
            if (servingNeeded) listOf(Portion(servingName.trim().ifBlank { "1 serving" }, servingValue)) else emptyList()
        val portions: List<Portion>? = (editedPortion + restPortions).ifEmpty { null }

        val saved = NutritionRepo.saveCustomFood(
            Food(
                customId = existing?.customId,
                name = n,
                brand = brand.trim().ifEmpty { null },
                barcode = barcodeClean.ifEmpty { null },
                source = if (existing?.source == Food.SOURCE_OFF) Food.SOURCE_OFF else Food.SOURCE_CUSTOM,
                kcal = customRound(k * factor),
                protein = customRound(p * factor),
                carbs = customRound(c * factor),
                fat = customRound(f * factor),
                fiber = if (fiber.isBlank()) null else fiberValue?.let { customRound(it * factor) },
                sugar = if (sugar.isBlank()) null else sugarValue?.let { customRound(it * factor) },
                portions = portions,
            )
        )
        if (meal != null) {
            nav.navigate(Routes.foodAmount(saved.key, meal)) {
                popUpTo(Routes.CUSTOM_FOOD_PATTERN) { inclusive = true }
            }
        } else {
            nav.popBackStack()
        }
    }

    CustomFoodScaffold(
        nav = nav,
        title = if (existing == null) "New food" else "Edit food",
        actions = {
            IconButton(onClick = { save() }) {
                Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (existing == null && barcode != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (prefillName.isNullOrBlank()) {
                                "Barcode $barcode isn't in Open Food Facts yet. Enter the values from the package label — " +
                                    "it will be saved on your phone, so scanning it next time finds it instantly."
                            } else {
                                "Barcode $barcode is in Open Food Facts but has no calorie data. Enter the values from the " +
                                    "package label — it will be saved on your phone, so scanning it next time finds it instantly."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Text(
                "Copy the values from the nutrition label (Nährwerte pro 100 g).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text("Brand (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = barcodeText,
                onValueChange = { v ->
                    barcodeText = v.filter { it.isDigit() }
                    error = null
                },
                label = { Text("Barcode (optional)") },
                singleLine = true,
                isError = duplicate != null,
                supportingText = if (duplicate != null) {
                    { Text("Already used by \"${duplicate.name}\".") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSectionLabel("Values per")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !perServing,
                        onClick = { perServing = false },
                        label = { Text("100 g") },
                    )
                    FilterChip(
                        selected = perServing,
                        onClick = { perServing = true },
                        label = { Text("Serving") },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSectionLabel(if (perServing) "Serving" else "Serving (optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = servingName,
                        onValueChange = { servingName = it },
                        label = { Text("Name, e.g. 1 bar") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = servingGrams,
                        onValueChange = { servingGrams = it; error = null },
                        label = { Text("Size (g)") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSectionLabel(if (perServing) "Nutrition per serving" else "Nutrition per 100 g")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = kcal,
                        onValueChange = { kcal = it; error = null },
                        label = { Text("Calories (kcal)") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it; error = null },
                        label = { Text("Protein (g)") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it; error = null },
                        label = { Text("Carbs (g)") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it; error = null },
                        label = { Text("Fat (g)") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSectionLabel("Optional")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = fiber,
                        onValueChange = { fiber = it; error = null },
                        label = { Text("Fiber (g)") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = sugar,
                        onValueChange = { sugar = it; error = null },
                        label = { Text("Sugar (g)") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger)
            }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (existing == null) "Save food" else "Save changes", fontWeight = FontWeight.SemiBold)
            }

            if (existing != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete food", color = Danger) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete food?") },
            text = {
                Text("\"${existing.name}\" will be removed from your foods. Entries already logged keep their values.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    NutritionRepo.deleteCustomFood(existing.key)
                    nav.popBackStack()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CustomSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
