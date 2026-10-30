package com.tooheavy.app.ui.nutrition

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tooheavy.app.data.Food
import com.tooheavy.app.data.FoodEntry
import com.tooheavy.app.data.Meal
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun searchParseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Logs the food's first portion (or 100 g) straight into the diary. */
private fun searchQuickLog(context: Context, food: Food, meal: String) {
    val portion = food.portions.orEmpty().firstOrNull { it.grams > 0 }
    val grams = portion?.grams ?: 100.0
    val label = portion?.label?.ifBlank { null } ?: "${formatNutrient(grams)} g"
    NutritionRepo.addEntry(NutritionRepo.makeEntry(food, grams, label, meal))
    Toast.makeText(context, "Added", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(nav: NavController, meal: String) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showQuickAdd by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { nav.navigate(Routes.barcode(it, meal)) }
    }
    fun startScan() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
        options.setPrompt("Scan a food barcode")
        options.setBeepEnabled(false)
        options.setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    val loaded = NutritionRepo.foodsLoaded
    val customCount = NutritionRepo.customFoods.size
    val trimmed = query.trim()
    val searching = trimmed.length >= 2

    val results by produceState(initialValue = emptyList<Food>(), trimmed, loaded, customCount) {
        if (trimmed.length < 2) {
            value = emptyList()
            return@produceState
        }
        delay(150)
        value = withContext(Dispatchers.Default) { NutritionRepo.search(trimmed) }
    }

    val myFoods = NutritionRepo.customFoods.toList()
    val entryCount = NutritionRepo.entries.size
    val recent = remember(entryCount, loaded, customCount) { NutritionRepo.recentFoods() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text("Add to " + Meal.label(meal), maxLines = 1)
                        Text(
                            friendlyDate(NutritionRepo.selectedDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { startScan() }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search foods") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            if (!loaded) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Loading food database…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searching) {
                    val shown = results.distinctBy { it.key }
                    if (shown.isEmpty() && trimmed.length >= 8 && trimmed.all { it.isDigit() }) {
                        item(key = "barcode_hint") {
                            SearchBarcodeHintRow(trimmed, onClick = { nav.navigate(Routes.barcode(trimmed, meal)) })
                        }
                    }
                    if (shown.isEmpty() && loaded) {
                        item(key = "no_results") {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "No foods found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Try another search term or create your own food.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { nav.navigate(Routes.customFood("new")) }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create food")
                                }
                            }
                        }
                    }
                    items(shown, key = { it.key }) { food ->
                        SearchFoodRow(
                            food = food,
                            onClick = { nav.navigate(Routes.foodAmount(food.key, meal)) },
                            onQuickAdd = { searchQuickLog(context, food, meal) },
                            onEdit = null,
                        )
                    }
                } else {
                    item(key = "actions") {
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { startScan() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Scan barcode", fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showQuickAdd = true },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Filled.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Quick add")
                                }
                                OutlinedButton(
                                    onClick = { nav.navigate(Routes.customFood("new")) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create food")
                                }
                            }
                        }
                    }
                    if (trimmed.isNotEmpty()) {
                        item(key = "hint_short") {
                            Text(
                                "Type at least 2 characters to search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    } else {
                        if (myFoods.isNotEmpty()) {
                            item(key = "header_mine") { SearchSectionHeader("My foods") }
                            items(myFoods, key = { "mine:${it.key}" }) { food ->
                                SearchFoodRow(
                                    food = food,
                                    onClick = { nav.navigate(Routes.foodAmount(food.key, meal)) },
                                    onQuickAdd = { searchQuickLog(context, food, meal) },
                                    onEdit = { nav.navigate(Routes.customFood(food.key)) },
                                )
                            }
                        }
                        if (recent.isNotEmpty()) {
                            item(key = "header_recent") { SearchSectionHeader("Recent") }
                            items(recent, key = { "recent:${it.key}" }) { food ->
                                SearchFoodRow(
                                    food = food,
                                    onClick = { nav.navigate(Routes.foodAmount(food.key, meal)) },
                                    onQuickAdd = { searchQuickLog(context, food, meal) },
                                    onEdit = null,
                                )
                            }
                        }
                        if (myFoods.isEmpty() && recent.isEmpty()) {
                            item(key = "hint_empty") {
                                Text(
                                    "Search above to find foods in the bundled USDA database, or create your own food or quick-add calories.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        SearchQuickAddDialog(
            onDismiss = { showQuickAdd = false },
            onConfirm = { name, kcal, protein, carbs, fat ->
                showQuickAdd = false
                NutritionRepo.addEntry(
                    FoodEntry(
                        date = NutritionRepo.selectedDate,
                        meal = meal,
                        foodKey = null,
                        name = name,
                        amountLabel = "Quick add",
                        kcal = kcal,
                        protein = protein,
                        carbs = carbs,
                        fat = fat,
                    )
                )
                nav.popBackStack()
            },
        )
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Shown when the query looks like a barcode but matches no stored food. */
@Composable
private fun SearchBarcodeHintRow(code: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(12.dp))
        Text(
            "Look up barcode $code",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchFoodRow(
    food: Food,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(14.dp)
    val sourceLabel = when (food.source) {
        Food.SOURCE_OFF -> "Open Food Facts"
        Food.SOURCE_CUSTOM -> "Custom"
        else -> "USDA"
    }
    val isLocalSource = food.source == Food.SOURCE_OFF || food.source == Food.SOURCE_CUSTOM
    val brand = food.brand?.trim()?.takeIf { it.isNotEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                food.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Source tag and brand share a line so the macro subtitle below keeps the full width.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLocalSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                if (brand != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Text(
                "per 100 g · P ${formatNutrient(food.protein)} · C ${formatNutrient(food.carbs)} · F ${formatNutrient(food.fat)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatNutrient(food.kcal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "kcal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit food",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(onClick = onQuickAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Quick log", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SearchQuickAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, kcal: Double, protein: Double, carbs: Double, fat: Double) -> Unit,
) {
    var name by remember { mutableStateOf("Quick add") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val kcalValue = searchParseNumber(kcal)
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kcal,
                        onValueChange = { kcal = it },
                        label = { Text("Calories") },
                        singleLine = true,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = kcalValue != null && kcalValue >= 0,
                onClick = {
                    onConfirm(
                        name.trim().ifBlank { "Quick add" },
                        kcalValue ?: 0.0,
                        searchParseNumber(protein) ?: 0.0,
                        searchParseNumber(carbs) ?: 0.0,
                        searchParseNumber(fat) ?: 0.0,
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
