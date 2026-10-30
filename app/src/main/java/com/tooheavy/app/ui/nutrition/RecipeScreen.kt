package com.tooheavy.app.ui.nutrition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Macros
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.RecipeDraft
import com.tooheavy.app.data.RecipeIngredient
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.CardLabel
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.theme.Danger
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private fun recipeParse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun recipeNumText(v: Double): String =
    BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** The same ingredient at a different weight; the nutrition snapshot scales with it. */
private fun RecipeIngredient.scaledTo(g: Double): RecipeIngredient {
    val f = if (grams > 0) g / grams else 0.0
    return copy(
        grams = g, amountLabel = "${formatNutrient(g)} g",
        kcal = kcal * f, protein = protein * f, carbs = carbs * f, fat = fat * f,
        fiber = fiber?.times(f), sugar = sugar?.times(f), salt = salt?.times(f),
    )
}

/** Recipe editor: name, servings, ingredients (picked through the food search) and the resulting nutrition. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(nav: NavController, key: String) {
    remember(key) { NutritionRepo.beginRecipeEdit(key); true }
    val draft = NutritionRepo.recipeDraft ?: RecipeDraft()
    val recipe = draft.recipe
    var servingsText by remember(key) { mutableStateOf(recipeNumText(recipe.servings)) }
    var cookedText by remember(key) { mutableStateOf(recipe.cookedGrams?.let { recipeNumText(it) }.orEmpty()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(d: RecipeDraft) {
        NutritionRepo.recipeDraft = d
        error = null
    }

    fun leave() {
        NutritionRepo.recipeDraft = null
        nav.popBackStack()
    }

    fun save() {
        when {
            draft.name.isBlank() -> error = "Enter a name."
            recipe.ingredients.isEmpty() -> error = "Add at least one ingredient."
            recipe.totalGrams <= 0 -> error = "The ingredients need a weight."
            else -> {
                NutritionRepo.saveRecipe(draft)
                nav.popBackStack()
            }
        }
    }

    BackHandler { leave() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text(if (draft.key == null) "New recipe" else "Edit recipe") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { update(draft.copy(name = it)) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = servingsText,
                    onValueChange = { v ->
                        servingsText = v
                        recipeParse(v)?.takeIf { it > 0 }?.let { update(draft.copy(recipe = recipe.copy(servings = it))) }
                    },
                    label = { Text("Servings") },
                    singleLine = true,
                    isError = recipeParse(servingsText)?.let { it > 0 } != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = cookedText,
                    onValueChange = { v ->
                        cookedText = v
                        update(draft.copy(recipe = recipe.copy(cookedGrams = recipeParse(v)?.takeIf { it > 0 })))
                    },
                    label = { Text("Cooked weight (g)") },
                    placeholder = { Text(formatNutrient(recipe.rawGrams)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Weigh the finished dish if cooking changes its weight (water boils off, pasta soaks it up), " +
                    "so logging by the gram stays accurate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    CardLabel("Ingredients", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (recipe.ingredients.isEmpty()) {
                        Text(
                            "No ingredients yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    recipe.ingredients.forEachIndexed { i, ing ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = i }.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ing.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${ing.amountLabel} · ${ing.kcal.roundToInt()} kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                update(draft.copy(recipe = recipe.copy(ingredients = recipe.ingredients.filterIndexed { j, _ -> j != i })))
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove ${ing.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.foodSearch(Routes.MODE_INGREDIENT)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add ingredient")
                    }
                }
            }

            if (recipe.ingredients.isNotEmpty()) {
                val total = recipe.ingredients.fold(Macros()) { acc, i -> acc + i.macros }
                val servings = recipe.servings.takeIf { it > 0 } ?: 1.0
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardLabel("Nutrition")
                        RecipeTotalRow("Whole recipe · ${formatNutrient(recipe.totalGrams)} g", total)
                        RecipeTotalRow("Per serving · ${formatNutrient(recipe.totalGrams / servings)} g", scale(total, 1 / servings))
                        if (recipe.totalGrams > 0) RecipeTotalRow("Per 100 g", scale(total, 100 / recipe.totalGrams))
                    }
                }
            }

            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger) }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (draft.key == null) "Save recipe" else "Save changes", fontWeight = FontWeight.SemiBold) }

            if (draft.key != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete recipe", color = Danger)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { i ->
        val ing = recipe.ingredients.getOrNull(i)
        if (ing == null) {
            editing = null
        } else {
            var gramsText by remember(i) { mutableStateOf(recipeNumText(ing.grams)) }
            val g = recipeParse(gramsText)?.takeIf { it > 0 }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(ing.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        label = { Text("Amount (g)") },
                        singleLine = true,
                        isError = g == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                },
                confirmButton = {
                    TextButton(enabled = g != null, onClick = {
                        editing = null
                        if (g != null) {
                            update(draft.copy(recipe = recipe.copy(ingredients = recipe.ingredients.mapIndexed { j, x -> if (j == i) x.scaledTo(g) else x })))
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete recipe?") },
            text = { Text("\"${draft.name}\" will be removed from your foods. Entries already logged keep their values.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    draft.key?.let { NutritionRepo.deleteCustomFood(it) }
                    leave()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun scale(m: Macros, f: Double) = Macros(m.kcal * f, m.protein * f, m.carbs * f, m.fat * f)

@Composable
private fun RecipeTotalRow(label: String, m: Macros) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "P ${formatNutrient(m.protein)} · C ${formatNutrient(m.carbs)} · F ${formatNutrient(m.fat)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${m.kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
