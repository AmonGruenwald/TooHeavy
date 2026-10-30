package com.tooheavy.app.ui.nutrition

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.BodyProfile
import com.tooheavy.app.data.CheckInProposal
import com.tooheavy.app.data.CoachSettings
import com.tooheavy.app.data.DietStyle
import com.tooheavy.app.data.Expenditure
import com.tooheavy.app.data.KCAL_PER_KG
import com.tooheavy.app.data.NutritionGoals
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.TrendPoint
import com.tooheavy.app.data.formatWeight
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.nextCheckInDate
import com.tooheavy.app.data.shiftDate
import com.tooheavy.app.data.today
import com.tooheavy.app.data.weeklyChangeKg
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.AreaChart
import com.tooheavy.app.ui.common.BarChart
import com.tooheavy.app.ui.common.ChartCard
import com.tooheavy.app.ui.common.ChartPoint
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.GradientButton
import com.tooheavy.app.ui.common.IconBubble
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.common.TrendChart
import com.tooheavy.app.ui.common.shortDateLabel
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.Ember
import com.tooheavy.app.ui.theme.Success
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val loseRates = listOf(0.25 to "Gentle", 0.5 to "Moderate", 0.75 to "Fast", 1.0 to "Aggressive")
private val gainRates = listOf(0.1 to "Lean", 0.25 to "Moderate", 0.5 to "Fast")
private val proteinOptions = listOf(1.6 to "Moderate", 2.0 to "High", 2.4 to "Very high")
private val weekdays = listOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat", Calendar.SUNDAY to "Sun",
)

private fun activityLabel(factor: Double) = when {
    factor < 1.3 -> "Sedentary"
    factor < 1.46 -> "Light activity"
    factor < 1.64 -> "Moderate activity"
    factor < 1.8 -> "Very active"
    else -> "Extra active"
}

/** 81.27 -> "81.3" */
private fun kg1(v: Double) = formatWeight(Math.round(v * 10) / 10.0)

/** -0.42 -> "-0.42 kg", 0.1 -> "+0.10 kg" */
private fun signedKg(v: Double) = String.format(Locale.US, "%+.2f kg", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(nav: NavController) {
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val coach = NutritionRepo.coach
    val profile = NutritionRepo.profile
    val day = today()
    // Reads the food log and weigh-ins, so the estimate recomputes whenever they change.
    val proposal = NutritionRepo.proposeCheckIn(day)
    val trend = NutritionRepo.trend()
    var showCheckIn by remember { mutableStateOf(false) }
    var showWeighIn by remember { mutableStateOf(false) }

    /** Saves plan changes; with coaching on, today's targets follow right away. */
    fun updatePlan(s: CoachSettings, p: BodyProfile = profile) {
        if (p != profile) NutritionRepo.updateProfile(p)
        NutritionRepo.updateCoach(s)
        if (s.enabled) NutritionRepo.applyCheckIn(NutritionRepo.proposeCheckIn(day))
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text("Coach") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!coach.enabled) {
                HighlightCard(
                    title = "Adaptive calorie coach",
                    body = "Works out how much you really burn from what you eat and how your weight trend moves, " +
                        "instead of trusting a formula. Once a week it checks in and sets new calorie and macro " +
                        "targets for the rate you pick below. Log your food and weigh in most mornings; missed days are fine.",
                    action = "Start coaching",
                    onAction = {
                        updatePlan(coach.copy(enabled = true))
                        toast("Coaching started, targets updated")
                    },
                )
            } else if (NutritionRepo.checkInDue()) {
                HighlightCard(
                    title = "Weekly check-in ready",
                    body = "See how your week went and get targets that match your latest expenditure.",
                    action = "Check in",
                    onAction = { showCheckIn = true },
                )
            }

            ExpenditureCard(proposal.expenditure)

            IntakeCard(NutritionRepo.goals.kcal)

            SectionCard("Weight trend") {
                val last = trend.lastOrNull()
                if (last != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${kg1(last.trendKg)} kg", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Trend weight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        proposal.weeklyChangeKg?.let { change ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(signedKg(change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = changeColor(change, profile.goal))
                                Text("Last 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TrendChart(trend.filter { it.date >= shiftDate(day, -90) })
                Text(
                    "Dots are weigh-ins, the line is the trend. Day-to-day swings are mostly water, so the coach follows the line.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showWeighIn = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (NutritionRepo.weights.any { it.date == day }) "Update today's weight" else "Log today's weight")
                }
            }

            PlanCard(
                nav = nav,
                coach = coach,
                profile = profile,
                trendKg = trend.lastOrNull()?.trendKg ?: profile.weightKg,
                expenditure = proposal.expenditure.kcal,
                onChange = { s, p -> updatePlan(s, p) },
            )

            if (coach.enabled) {
                SectionCard("Current targets") {
                    GoalsLine(NutritionRepo.goals)
                    Text(
                        "Next check-in: ${friendlyDate(nextCheckInDate(coach, NutritionRepo.lastCheckIn()?.date, day))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCheckIn = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Check in now") }
                        TextButton(onClick = {
                            NutritionRepo.updateCoach(coach.copy(enabled = false))
                            toast("Coaching off, your goals stay as they are")
                        }) { Text("Stop coaching", color = Danger) }
                    }
                }
            }

            val checkIns = NutritionRepo.checkIns.toList()
            if (checkIns.size >= 2) {
                ChartCard(
                    "Expenditure history",
                    caption = "Estimated daily burn at each check-in",
                    icon = Icons.Filled.Speed,
                ) {
                    AreaChart(
                        checkIns.mapIndexed { i, c -> ChartPoint(i.toFloat(), c.expenditure.toDouble(), shortDateLabel(c.date)) },
                        valueFormat = { intText(it.toInt()) },
                    )
                }
            }
            val history = checkIns.asReversed()
            if (history.isNotEmpty()) {
                SectionCard("Check-ins") {
                    Column {
                        history.take(12).forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(friendlyDate(c.date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        buildString {
                                            c.trendKg?.let { append("${kg1(it)} kg") }
                                            c.weeklyChangeKg?.let { append(" (").append(signedKg(it)).append(")") }
                                            if (isNotEmpty()) append(" · ")
                                            append("burn ${intText(c.expenditure)}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("${intText(c.goals.kcal)} kcal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCheckIn) {
        CheckInDialog(
            proposal = proposal,
            current = NutritionRepo.goals,
            goal = profile.goal,
            onApply = {
                NutritionRepo.applyCheckIn(proposal)
                showCheckIn = false
                toast("Targets updated")
            },
            onDismiss = { showCheckIn = false },
        )
    }
    if (showWeighIn) WeighInDialog { showWeighIn = false }
}

@Composable
private fun changeColor(change: Double, goal: String): Color = when {
    abs(change) < 0.05 -> MaterialTheme.colorScheme.onSurface
    (goal == "lose" && change < 0) || (goal == "gain" && change > 0) -> Success
    goal == "maintain" -> MaterialTheme.colorScheme.onSurface
    else -> Danger
}

@Composable
private fun HighlightCard(title: String, body: String, action: String, onAction: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            GradientButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(action)
            }
        }
    }
}

@Composable
private fun ExpenditureCard(e: Expenditure) {
    SectionCard("Expenditure") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${intText(e.kcal)} kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                " per day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        val data = e.dataKcal
        if (data == null) {
            Text(
                "Starting estimate from your profile. It begins adapting once there are 7 days of food logs and 3 weigh-ins " +
                    "in the last 3 weeks (now ${e.loggedDays} days and ${e.weighIns} weigh-ins).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Your data says ${intText(data)} kcal, the profile formula ${intText(e.formulaKcal)} kcal.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Meter(e.confidence.toFloat(), Ember)
            Text(
                "${(e.confidence * 100).roundToInt()} % data-driven, from ${e.loggedDays} logged days and ${e.weighIns} weigh-ins " +
                    "over the last ${e.spanDays} days. More consistent logging moves it toward 100 %.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanCard(
    nav: NavController,
    coach: CoachSettings,
    profile: BodyProfile,
    trendKg: Double,
    expenditure: Int,
    onChange: (CoachSettings, BodyProfile) -> Unit,
) {
    SectionCard("Plan") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${profile.sex.replaceFirstChar { it.uppercase() }} · ${profile.age} y · ${formatWeight(profile.heightCm)} cm · " +
                    activityLabel(profile.activity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { nav.navigate(Routes.NUTRITION_GOALS) }) { Text("Edit") }
        }

        Label("Goal")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("lose" to "Lose", "maintain" to "Maintain", "gain" to "Gain").forEach { (g, label) ->
                FilterChip(
                    selected = profile.goal == g,
                    onClick = {
                        // A new direction starts at its moderate rate; 0.5 % is moderate for losing but fast for gaining.
                        val rate = if (g == profile.goal) coach.weeklyRatePct else (if (g == "gain") gainRates else loseRates)[1].first
                        onChange(coach.copy(weeklyRatePct = rate), profile.copy(goal = g))
                    },
                    label = { Text(label) },
                )
            }
        }

        if (profile.goal != "maintain") {
            Label("Rate per week")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (profile.goal == "gain") gainRates else loseRates).forEach { (pct, label) ->
                    FilterChip(
                        selected = coach.weeklyRatePct == pct,
                        onClick = { onChange(coach.copy(weeklyRatePct = pct), profile) },
                        label = { Text("$label ${formatWeight(pct)} %") },
                    )
                }
            }
            val perWeek = coach.weeklyChangeKg(profile, trendKg)
            val perDay = (perWeek * KCAL_PER_KG / 7).roundToInt()
            Text(
                "About ${String.format(Locale.US, "%.2f", abs(perWeek))} kg a week, " +
                    "${intText(abs(perDay))} kcal a day ${if (perDay < 0) "below" else "above"} your expenditure of ${intText(expenditure)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Label("Protein")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            proteinOptions.forEach { (perKg, label) ->
                FilterChip(
                    selected = coach.proteinPerKg == perKg,
                    onClick = { onChange(coach.copy(proteinPerKg = perKg), profile) },
                    label = { Text("$label ${formatWeight(perKg)} g/kg") },
                )
            }
        }

        Label("Diet style")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DietStyle.all.forEach { style ->
                FilterChip(
                    selected = coach.dietStyle == style,
                    onClick = { onChange(coach.copy(dietStyle = style), profile) },
                    label = { Text(DietStyle.label(style)) },
                )
            }
        }
        Text(DietStyle.hint(coach.dietStyle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Label("Check-in day")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekdays.forEach { (d, label) ->
                FilterChip(
                    selected = coach.checkInDay == d,
                    onClick = { NutritionRepo.updateCoach(coach.copy(checkInDay = d)) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GoalsLine(g: NutritionGoals) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${intText(g.kcal)} kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Protein ${g.protein} g  ·  Carbs ${g.carbs} g  ·  Fat ${g.fat} g",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckInDialog(
    proposal: CheckInProposal,
    current: NutritionGoals,
    goal: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly check-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                proposal.trendKg?.let { DialogLine("Trend weight", "${kg1(it)} kg") }
                proposal.weeklyChangeKg?.let { DialogLine("Last 7 days", signedKg(it), changeColor(it, goal)) }
                DialogLine("Expenditure", "${intText(proposal.expenditure.kcal)} kcal")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val diff = proposal.goals.kcal - current.kcal
                DialogLine(
                    "New calories",
                    "${intText(proposal.goals.kcal)} kcal" + if (diff != 0) String.format(Locale.US, " (%+d)", diff) else "",
                )
                DialogLine("Protein", "${proposal.goals.protein} g")
                DialogLine("Carbs", "${proposal.goals.carbs} g")
                DialogLine("Fat", "${proposal.goals.fat} g")
                if (proposal.expenditure.dataKcal == null) {
                    Text(
                        "Not enough logs yet, so this still uses the profile estimate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

@Composable
private fun DialogLine(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/** Logs today's body weight. */
@Composable
internal fun WeighInDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember {
        mutableStateOf(formatWeight(NutritionRepo.weights.lastOrNull()?.kg ?: NutritionRepo.profile.weightKg))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Today's weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Weight", text, { text = it }, decimal = true, suffix = "kg", modifier = Modifier.fillMaxWidth())
                Text(
                    "Weigh in each morning after the bathroom and before eating for the steadiest trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = parseNum(text)?.takeIf { it in 20.0..500.0 }
                if (kg == null) {
                    Toast.makeText(context, "Enter a valid weight", Toast.LENGTH_SHORT).show()
                } else {
                    NutritionRepo.logWeight(today(), kg)
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Coach entry on the Food tab: check-in prompt, or expenditure and trend at a glance, plus a quick weigh-in. */
@Composable
internal fun CoachSummaryCard(onOpen: () -> Unit) {
    val coach = NutritionRepo.coach
    val day = today()
    val due = coach.enabled && NutritionRepo.checkInDue()
    val weighedToday = NutritionRepo.weights.any { it.date == day }
    var weighIn by remember { mutableStateOf(false) }

    GlassCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (due) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Filled.Insights, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val (title, subtitle) = when {
                    !coach.enabled -> "Adaptive coach" to "Calorie targets that follow your real metabolism"
                    due -> "Weekly check-in ready" to "Review your week and update your targets"
                    else -> {
                        val trend: TrendPoint? = NutritionRepo.trend().lastOrNull()
                        val next = nextCheckInDate(coach, NutritionRepo.lastCheckIn()?.date, day)
                        "Expenditure ${intText(NutritionRepo.expenditure(day).kcal)} kcal" to
                            listOfNotNull(trend?.let { "Trend ${kg1(it.trendKg)} kg" }, "check-in ${friendlyDate(next)}").joinToString(" · ")
                    }
                }
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (coach.enabled && !weighedToday) {
                TextButton(onClick = { weighIn = true }) { Text("Weigh in") }
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (weighIn) WeighInDialog { weighIn = false }
}

@Composable
private fun IntakeCard(goal: Int) {
    val day = today()
    val days = (13 downTo 0).map { shiftDate(day, -it) }
    val kcals = days.map { NutritionRepo.totals(it).kcal }
    val logged = kcals.filter { it > 0 }
    ChartCard(
        "Intake · last 14 days",
        value = if (logged.isEmpty()) null else intText(logged.average().roundToInt()),
        unit = if (logged.isEmpty()) null else "kcal / day avg",
        caption = "Logged calories against your ${intText(goal)} kcal target",
        icon = Icons.Filled.LocalFireDepartment,
    ) {
        BarChart(
            kcals,
            days.map { it.takeLast(2).trimStart('0') },
            height = 150.dp,
            goal = goal.toDouble(),
            tooltipLabels = days.map { friendlyDate(it) },
        )
    }
}
