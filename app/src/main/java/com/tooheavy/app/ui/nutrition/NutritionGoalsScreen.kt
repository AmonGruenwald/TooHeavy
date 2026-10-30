package com.tooheavy.app.ui.nutrition

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.BodyProfile
import com.tooheavy.app.data.NutritionGoals
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.suggestedGoals
import com.tooheavy.app.data.tdee
import com.tooheavy.app.data.today
import com.tooheavy.app.ui.common.LineChart
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.Warmup
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private class GoalsActivityLevel(val label: String, val hint: String, val factor: Double)

private val activityLevels = listOf(
    GoalsActivityLevel("Sedentary", "Desk job, little or no exercise", 1.2),
    GoalsActivityLevel("Light", "Light exercise 1-3 days a week", 1.375),
    GoalsActivityLevel("Moderate", "Moderate exercise 3-5 days a week", 1.55),
    GoalsActivityLevel("Very active", "Hard exercise 6-7 days a week", 1.725),
    GoalsActivityLevel("Extra active", "Physical job or training twice a day", 1.9),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionGoalsScreen(nav: NavController) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val savedGoals = NutritionRepo.goals
    val savedProfile = NutritionRepo.profile

    // Daily goals fields
    var kcalText by remember { mutableStateOf(savedGoals.kcal.toString()) }
    var proteinText by remember { mutableStateOf(savedGoals.protein.toString()) }
    var carbsText by remember { mutableStateOf(savedGoals.carbs.toString()) }
    var fatText by remember { mutableStateOf(savedGoals.fat.toString()) }

    // Calculator fields
    var sex by remember { mutableStateOf(savedProfile.sex) }
    var ageText by remember { mutableStateOf(savedProfile.age.toString()) }
    var heightText by remember { mutableStateOf(formatWeight(savedProfile.heightCm)) }
    var weightText by remember { mutableStateOf(formatWeight(savedProfile.weightKg)) }
    var activity by remember { mutableStateOf(savedProfile.activity) }
    var goal by remember { mutableStateOf(savedProfile.goal) }

    // Body weight field
    var logText by remember {
        mutableStateOf(formatWeight(NutritionRepo.weights.lastOrNull()?.kg ?: savedProfile.weightKg))
    }

    // Keep the calculator weight in sync when a weigh-in changes the profile weight.
    LaunchedEffect(savedProfile.weightKg) { weightText = formatWeight(savedProfile.weightKg) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Goals & Body", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Daily goals ----------
            SectionCard("Daily goals") {
                NumberField("Calories", kcalText, { kcalText = it }, decimal = false, suffix = "kcal", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Protein", proteinText, { proteinText = it }, decimal = false, suffix = "g", modifier = Modifier.weight(1f))
                    NumberField("Carbs", carbsText, { carbsText = it }, decimal = false, suffix = "g", modifier = Modifier.weight(1f))
                    NumberField("Fat", fatText, { fatText = it }, decimal = false, suffix = "g", modifier = Modifier.weight(1f))
                }

                val p = parseNum(proteinText) ?: 0.0
                val c = parseNum(carbsText) ?: 0.0
                val f = parseNum(fatText) ?: 0.0
                val implied = (p * 4 + c * 4 + f * 9).roundToInt()
                val entered = parseNum(kcalText)?.roundToInt()
                val diff = entered?.let { implied - it }
                val close = entered != null && entered > 0 && abs(implied - entered) <= entered * 0.05
                Text(
                    buildString {
                        append("Macros add up to ").append(intText(implied)).append(" kcal")
                        if (diff != null) {
                            append(" (").append(if (diff >= 0) "+" else "-").append(intText(abs(diff)))
                            append(" vs. calories)")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diff == null) MaterialTheme.colorScheme.onSurfaceVariant else if (close) Success else Warmup,
                )

                Button(
                    onClick = {
                        val kcal = parseNum(kcalText)?.roundToInt()
                        val pr = parseNum(proteinText)?.roundToInt()
                        val ca = parseNum(carbsText)?.roundToInt()
                        val fa = parseNum(fatText)?.roundToInt()
                        if (kcal == null || kcal <= 0 || pr == null || pr < 0 || ca == null || ca < 0 || fa == null || fa < 0) {
                            toast("Enter valid numbers")
                        } else {
                            NutritionRepo.updateGoals(NutritionGoals(kcal, pr, ca, fa))
                            focus.clearFocus()
                            toast("Goals saved")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Save goals", fontWeight = FontWeight.SemiBold)
                }
            }

            // ---------- Calculator ----------
            SectionCard("Calculator") {
                Text("Sex", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sex == "male", onClick = { sex = "male" }, label = { Text("Male") })
                    FilterChip(selected = sex == "female", onClick = { sex = "female" }, label = { Text("Female") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Age", ageText, { ageText = it }, decimal = false, suffix = "yr", modifier = Modifier.weight(1f))
                    NumberField("Height", heightText, { heightText = it }, decimal = true, suffix = "cm", modifier = Modifier.weight(1f))
                    NumberField("Weight", weightText, { weightText = it }, decimal = true, suffix = "kg", modifier = Modifier.weight(1f))
                }

                Text("Activity level", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    activityLevels.forEach { level ->
                        val selected = abs(activity - level.factor) < 0.001
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { activity = level.factor })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(level.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    level.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "x${level.factor}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Text("Goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = goal == "lose", onClick = { goal = "lose" }, label = { Text("Lose") })
                    FilterChip(selected = goal == "maintain", onClick = { goal = "maintain" }, label = { Text("Maintain") })
                    FilterChip(selected = goal == "gain", onClick = { goal = "gain" }, label = { Text("Gain") })
                }

                val ageYears = parseNum(ageText)?.roundToInt()?.takeIf { it in 10..100 }
                val heightCm = parseNum(heightText)?.takeIf { it in 100.0..250.0 }
                val weightKg = parseNum(weightText)?.takeIf { it in 30.0..300.0 }
                val draft: BodyProfile? =
                    if (ageYears != null && heightCm != null && weightKg != null) {
                        BodyProfile(sex = sex, age = ageYears, heightCm = heightCm, weightKg = weightKg, activity = activity, goal = goal)
                    } else null

                if (draft == null) {
                    Text(
                        "Enter a valid age, height and weight to see your numbers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                    )
                } else {
                    val maintenance = draft.tdee()
                    val suggested = draft.suggestedGoals()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Maintenance: ${intText(maintenance)} kcal",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Suggested goals",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${intText(suggested.kcal)} kcal  ·  P ${suggested.protein} g  ·  C ${suggested.carbs} g  ·  F ${suggested.fat} g",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Button(
                        onClick = {
                            NutritionRepo.updateProfile(draft)
                            NutritionRepo.updateGoals(suggested)
                            kcalText = suggested.kcal.toString()
                            proteinText = suggested.protein.toString()
                            carbsText = suggested.carbs.toString()
                            fatText = suggested.fat.toString()
                            focus.clearFocus()
                            toast("Goals updated")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Use these goals", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ---------- Body weight ----------
            SectionCard("Body weight") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberField("Weight", logText, { logText = it }, decimal = true, suffix = "kg", modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val kg = parseNum(logText)?.takeIf { it in 20.0..500.0 }
                            if (kg == null) {
                                toast("Enter a valid weight")
                            } else {
                                NutritionRepo.logWeight(today(), kg)
                                focus.clearFocus()
                                toast("Weight logged")
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Log for today", fontWeight = FontWeight.SemiBold)
                    }
                }

                val weights = NutritionRepo.weights.toList()
                LineChart(values = weights.takeLast(90).map { it.kg })

                if (weights.isNotEmpty()) {
                    val recent = weights.takeLast(10).reversed()
                    Column {
                        recent.forEachIndexed { i, w ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    friendlyDate(w.date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${formatWeight(w.kg)} kg",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                IconButton(onClick = { NutritionRepo.deleteWeight(w.date) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete weigh-in",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Food data: USDA FoodData Central (public domain) — SR Legacy & FNDDS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    decimal: Boolean,
    suffix: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = value.isNotBlank() && parseNum(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}

/** Accepts both "." and "," as the decimal separator. */
private fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private fun intText(v: Int): String = String.format(Locale.US, "%,d", v)
