package com.tooheavy.app.ui.nutrition

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.OpenFoodFacts
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.sortedByUse
import com.tooheavy.app.data.timeOn
import com.tooheavy.app.data.toIngredient
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.SelectAllOutlinedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun searchParseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** ZXing options for scanning EAN/UPC product barcodes, on the scanner screen with tap to focus. */
fun foodScanOptions(prompt: String = "Scan a food barcode"): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
    setCaptureActivity(FoodScanActivity::class.java)
    setPrompt("$prompt · tap to focus")
    setBeepEnabled(false)
    setOrientationLocked(false)
}

/** Logs the food's first portion (or 100 g) straight into the diary, or adds it to the recipe in MODE_INGREDIENT. */
private fun searchQuickLog(context: Context, food: Food, mode: String) {
    val portion = food.portions.orEmpty().firstOrNull { it.grams > 0 }
    val grams = portion?.grams ?: 100.0
    val label = portion?.label?.ifBlank { null } ?: "${formatNutrient(grams)} g"
    if (mode == Routes.MODE_INGREDIENT) {
        NutritionRepo.addIngredient(food.toIngredient(grams, label))
        Toast.makeText(context, "Added to recipe", Toast.LENGTH_SHORT).show()
    } else {
        val entry = NutritionRepo.makeEntry(food, grams, label)
        NutritionRepo.addEntry(entry)
        AddingFoods.added(entry.id)
        Toast.makeText(context, "Added", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(nav: NavController, mode: String) {
    val context = LocalContext.current
    val ingredientMode = mode == Routes.MODE_INGREDIENT
    var query by remember { mutableStateOf("") }
    var showQuickAdd by remember { mutableStateOf(false) }
    // What's been added since the search opened shows at the bottom (not for recipe ingredients).
    if (!ingredientMode) StartAddingFoods()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotEmpty() }?.let { nav.navigate(Routes.barcode(it, mode)) }
    }
    fun startScan() = scanLauncher.launch(foodScanOptions())
    fun editFood(food: Food) = nav.navigate(if (food.isRecipe) Routes.recipe(food.key) else Routes.customFood(food.key))

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

    // Products on Open Food Facts too (what a barcode scan finds), once typing pauses: its search allows only a few
    // calls a minute. Not for barcodes typed in, which go to the barcode lookup.
    var onlineRetry by remember { mutableIntStateOf(0) }
    val online by produceState<OnlineSearch>(OnlineSearch.Idle, trimmed, onlineRetry) {
        if (trimmed.length < 3 || trimmed.all { it.isDigit() }) {
            value = OnlineSearch.Idle
            return@produceState
        }
        delay(700)
        value = OnlineSearch.Loading
        value = when (val r = OpenFoodFacts.search(trimmed)) {
            is OpenFoodFacts.SearchResult.Found -> OnlineSearch.Done(r.foods)
            is OpenFoodFacts.SearchResult.Error -> OnlineSearch.Failed
        }
    }
    // Products already saved show among your foods.
    val savedBarcodes = NutritionRepo.customFoods.mapNotNull { it.barcode }.toSet()
    val onlineFoods = (online as? OnlineSearch.Done)?.foods.orEmpty().filter { it.barcode !in savedBarcodes }

    /** Saves a product from the online search to your foods (as a barcode scan does), then adds it. */
    fun pickOnline(food: Food, quick: Boolean) {
        val saved = food.barcode?.let { NutritionRepo.foodByBarcode(it) } ?: NutritionRepo.saveCustomFood(food)
        if (quick) searchQuickLog(context, saved, mode) else nav.navigate(Routes.foodAmount(saved.key, mode))
    }

    // A recipe can't contain itself.
    val editingKey = if (ingredientMode) NutritionRepo.recipeDraft?.key else null
    val entryCount = NutritionRepo.entries.size
    // Your foods, the ones you log most often first.
    val myFoodsList = NutritionRepo.customFoods.toList()
    val myFoods = remember(myFoodsList, entryCount, editingKey) {
        sortedByUse(myFoodsList.filter { it.key != editingKey }, NutritionRepo.entries.toList())
    }
    val recent = remember(entryCount, loaded, customCount) { NutritionRepo.recentFoods() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(if (ingredientMode) "Add ingredient" else "Add food", maxLines = 1)
                        Text(
                            if (ingredientMode) NutritionRepo.recipeDraft?.name?.ifBlank { null } ?: "New recipe"
                            else friendlyDate(NutritionRepo.selectedDate),
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
        bottomBar = {
            if (!ingredientMode) {
                AddedFoodsPanel(
                    onEdit = { nav.navigate(Routes.editFoodEntry(it)) },
                    onDone = { nav.popBackStack() },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SelectAllOutlinedTextField(
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
                            SearchBarcodeHintRow(trimmed, onClick = { nav.navigate(Routes.barcode(trimmed, mode)) })
                        }
                    }
                    if (shown.isEmpty() && loaded && online != OnlineSearch.Loading && onlineFoods.isEmpty()) {
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
                                    "Try another search term, scan its barcode or create your own food.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { nav.navigate(Routes.customFood("new", mode = mode)) }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Create food")
                                }
                            }
                        }
                    }
                    items(shown.filter { it.key != editingKey }, key = { it.key }) { food ->
                        SearchFoodRow(
                            food = food,
                            onClick = { nav.navigate(Routes.foodAmount(food.key, mode)) },
                            onQuickAdd = { searchQuickLog(context, food, mode) },
                            onEdit = null,
                        )
                    }
                    when (online) {
                        OnlineSearch.Loading -> item(key = "online_loading") {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Searching Open Food Facts…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OnlineSearch.Failed -> item(key = "online_error") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Couldn't search Open Food Facts (offline?)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onlineRetry++ }) { Text("Retry") }
                            }
                        }
                        else -> {}
                    }
                    if (onlineFoods.isNotEmpty()) {
                        item(key = "online_header") { SearchSectionHeader("Products · Open Food Facts") }
                        items(onlineFoods, key = { "off:${it.barcode}" }) { food ->
                            SearchFoodRow(
                                food = food,
                                onClick = { pickOnline(food, quick = false) },
                                onQuickAdd = { pickOnline(food, quick = true) },
                                onEdit = null,
                            )
                        }
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
                                if (!ingredientMode) {
                                    OutlinedButton(
                                        onClick = { showQuickAdd = true },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Icon(Icons.Filled.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Quick add", maxLines = 1)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { nav.navigate(Routes.customFood("new", mode = mode)) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New food", maxLines = 1)
                                }
                                if (!ingredientMode) {
                                    OutlinedButton(
                                        onClick = { nav.navigate(Routes.recipe("new")) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Recipe", maxLines = 1)
                                    }
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
                                    onClick = { nav.navigate(Routes.foodAmount(food.key, mode)) },
                                    onQuickAdd = { searchQuickLog(context, food, mode) },
                                    onEdit = { editFood(food) },
                                )
                            }
                        }
                        if (recent.isNotEmpty()) {
                            item(key = "header_recent") { SearchSectionHeader("Recent") }
                            items(recent.filter { it.key != editingKey }, key = { "recent:${it.key}" }) { food ->
                                SearchFoodRow(
                                    food = food,
                                    onClick = { nav.navigate(Routes.foodAmount(food.key, mode)) },
                                    onQuickAdd = { searchQuickLog(context, food, mode) },
                                    onEdit = null,
                                )
                            }
                        }
                        if (myFoods.isEmpty() && recent.isEmpty()) {
                            item(key = "hint_empty") {
                                Text(
                                    if (ingredientMode) "Search above to find an ingredient in the bundled USDA database, scan one, or create your own food."
                                    else "Search above to find foods in the bundled USDA database, create your own food or recipe, or quick-add calories.",
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
            onConfirm = { form ->
                val eaten = form.eaten
                if (eaten != null) {
                    showQuickAdd = false
                    val entry = FoodEntry(
                        date = NutritionRepo.selectedDate,
                        createdAt = timeOn(NutritionRepo.selectedDate),
                        foodKey = null,
                        name = form.name.trim().ifBlank { "Quick add" },
                        grams = form.amountGrams ?: 0.0,
                        amountLabel = form.amountLabel,
                        kcal = eaten.kcal,
                        protein = eaten.protein,
                        carbs = eaten.carbs,
                        fat = eaten.fat,
                    )
                    NutritionRepo.addEntry(entry)
                    // Stays in the search, with the entry in the list of foods added.
                    AddingFoods.added(entry.id)
                }
            },
        )
    }
}

/** The Open Food Facts part of a search. */
private sealed interface OnlineSearch {
    data object Idle : OnlineSearch
    data object Loading : OnlineSearch
    data class Done(val foods: List<Food>) : OnlineSearch
    data object Failed : OnlineSearch
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
        Food.SOURCE_RECIPE -> "Recipe"
        else -> "USDA"
    }
    val isLocalSource = food.isLocal
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

/** Quick add: calories and macros as eaten, or per 100 g with the amount eaten (see QuickAddFields). */
@Composable
private fun SearchQuickAddDialog(onDismiss: () -> Unit, onConfirm: (QuickAddForm) -> Unit) {
    val form = rememberQuickAddForm()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { QuickAddFields(form) } },
        confirmButton = {
            TextButton(enabled = form.eaten != null, onClick = { onConfirm(form) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
