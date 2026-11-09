package com.tooheavy.app.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tooheavy.app.data.FoodEntry
import com.tooheavy.app.data.Macros
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.quickAddMacros
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** 100.0 -> "100", 1.5 -> "1.5", 12.345 -> "12.35" for editable fields; 0 -> "" so an empty field stays empty. */
private fun numText(v: Double): String =
    if (v == 0.0) "" else BigDecimal(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/**
 * A quick-add form: a name and calories, fat, carbs and protein, either as eaten ("Total") or per 100 g with the
 * amount eaten. Compose-observable.
 */
class QuickAddForm(name: String, per100g: Boolean, kcal: String, fat: String, carbs: String, protein: String, grams: String) {
    var name by mutableStateOf(name)
    var per100g by mutableStateOf(per100g)
    var kcal by mutableStateOf(kcal)
    var fat by mutableStateOf(fat)
    var carbs by mutableStateOf(carbs)
    var protein by mutableStateOf(protein)
    var grams by mutableStateOf(grams)

    /** What was eaten, scaled to the amount when the values are per 100 g; null while not valid. */
    val eaten: Macros? get() = quickAddMacros(parse(kcal), parse(fat), parse(carbs), parse(protein), per100g, parse(grams))

    /** The amount eaten in grams with per-100 g values, else null. */
    val amountGrams: Double? get() = if (per100g) parse(grams)?.takeIf { it > 0 } else null

    /** "150 g" with per-100 g values, else "Quick add". */
    val amountLabel: String get() = amountGrams?.let { "${numText(it)} g" } ?: "Quick add"

    companion object {
        fun empty() = QuickAddForm("Quick add", false, "", "", "", "", "")

        /** An existing entry: per 100 g (with its amount) when it has one, else its totals. */
        fun of(e: FoodEntry): QuickAddForm =
            if (e.grams > 0) {
                val f = 100.0 / e.grams
                QuickAddForm(e.name, true, numText(e.kcal * f), numText(e.fat * f), numText(e.carbs * f), numText(e.protein * f), numText(e.grams))
            } else {
                QuickAddForm(e.name, false, numText(e.kcal), numText(e.fat), numText(e.carbs), numText(e.protein), "")
            }
    }
}

@Composable
fun rememberQuickAddForm(entry: FoodEntry? = null): QuickAddForm = remember(entry?.id) { entry?.let(QuickAddForm::of) ?: QuickAddForm.empty() }

/**
 * The quick-add fields: name, "Total" or "Per 100 g", then calories, fat, carbs and protein, and with per-100 g
 * values the amount eaten and what that comes to.
 */
@Composable
fun QuickAddFields(form: QuickAddForm, spacing: Int = 8) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val per = if (form.per100g) " /100 g" else ""
    @Composable
    fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, isError: Boolean = false) {
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(7)) },
            label = { Text(label, maxLines = 1) },
            singleLine = true,
            isError = isError,
            keyboardOptions = decimal,
            modifier = modifier,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { form.name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !form.per100g, onClick = { form.per100g = false }, label = { Text("Total") })
            FilterChip(selected = form.per100g, onClick = { form.per100g = true }, label = { Text("Per 100 g") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val kcal = parse(form.kcal)
            Field("Calories$per", form.kcal, { form.kcal = it }, Modifier.weight(1f), isError = form.kcal.isNotBlank() && (kcal == null || kcal < 0))
            Field("Fat (g)$per", form.fat, { form.fat = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Carbs (g)$per", form.carbs, { form.carbs = it }, Modifier.weight(1f))
            Field("Protein (g)$per", form.protein, { form.protein = it }, Modifier.weight(1f))
        }
        if (form.per100g) {
            Field("Amount eaten (g)", form.grams, { form.grams = it }, Modifier.fillMaxWidth())
            val eaten = form.eaten
            val grams = form.amountGrams
            Text(
                if (eaten != null && grams != null) {
                    "${formatNutrient(grams)} g: ${eaten.kcal.roundToInt()} kcal · F ${formatNutrient(eaten.fat)} · " +
                        "C ${formatNutrient(eaten.carbs)} · P ${formatNutrient(eaten.protein)}"
                } else {
                    "Enter the values from the label and how much you ate."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
