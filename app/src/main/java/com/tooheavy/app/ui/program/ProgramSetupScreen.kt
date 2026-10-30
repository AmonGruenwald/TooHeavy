package com.tooheavy.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.Equipment
import com.tooheavy.app.data.Experience
import com.tooheavy.app.data.MuscleGroup
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.Split
import com.tooheavy.app.data.TrainingGoal
import com.tooheavy.app.data.estimatedMinutes
import com.tooheavy.app.data.generateProgram
import com.tooheavy.app.data.sessionsPerWeek
import com.tooheavy.app.data.weeklyGroupSets
import com.tooheavy.app.data.weeklySetRange
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.Meter
import com.tooheavy.app.ui.theme.AccentBright
import com.tooheavy.app.ui.theme.Danger
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.Warmup
import java.util.Locale

/** Beyond this many focus groups, prioritising stops meaning much. */
private const val FOCUS_HINT = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgramSetupScreen(nav: NavController) {
    val existing = Repo.program
    var goal by remember { mutableStateOf(existing?.goal ?: TrainingGoal.HYPERTROPHY) }
    var level by remember { mutableStateOf(existing?.experience ?: Experience.BEGINNER) }
    var days by remember { mutableIntStateOf(existing?.sessionsPerWeek ?: 3) }
    var equipment by remember { mutableStateOf(existing?.equipment ?: Equipment.GYM) }
    var split by remember { mutableStateOf(existing?.split ?: Split.AUTO) }
    var focus by remember { mutableStateOf(existing?.focus.orEmpty()) }
    var reduce by remember { mutableStateOf(existing?.reduce.orEmpty()) }
    var minutes by remember { mutableIntStateOf(existing?.minutes ?: 60) }
    var confirmReplace by remember { mutableStateOf(false) }

    val unit = Repo.settings.unit
    val preview = remember(goal, level, days, equipment, split, focus, reduce, minutes, unit) {
        generateProgram(goal, level, days, equipment, unit, Repo::exercise, split, focus, reduce, minutes, Repo::best1RM)
    }

    fun create() {
        Repo.saveProgram(preview)
        nav.navigate(Routes.PROGRAM) {
            popUpTo(Routes.PROGRAM_SETUP) { inclusive = true }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text("New program") },
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
            Text(
                "Answer a few questions and TooHeavy builds your plan. After every workout it sets the next session's " +
                    "weights, reps and sets from what you actually lifted, and after every block it adjusts your volume.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ProgramCard {
                Text("Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TrainingGoal.all.forEach { g ->
                    OptionRow(TrainingGoal.label(g), TrainingGoal.hint(g), goal == g) { goal = g }
                }
            }

            ProgramCard {
                Text("Experience", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Experience.all.forEach { e ->
                    OptionRow(Experience.label(e), Experience.hint(e), level == e) { level = e }
                }
            }

            ProgramCard {
                Text("Days per week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..6).forEach { n ->
                        FilterChip(selected = days == n, onClick = { days = n }, label = { Text("$n") })
                    }
                }
            }

            ProgramCard {
                Text("Time per workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 45, 60, 75, 90, 0).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text(if (m == 0) "No limit" else "$m min") },
                        )
                    }
                }
                Text(
                    when {
                        minutes == 0 -> "Sessions are sized by sets alone, which can take well over an hour."
                        minutes <= 45 -> "Short sessions: slightly shorter rests, fewer sets and fewer exercises, main lifts kept. " +
                            "Weekly volume may land below the usual range; more days a week make up for it."
                        else -> "Sets and exercises are trimmed until each session fits, including warm-ups and rests."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ProgramCard {
                Text("Split", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Split.choices.forEach { s ->
                    OptionRow(Split.label(s), Split.hint(s, days), split == s) { split = s }
                }
            }

            ProgramCard {
                Text("Equipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Equipment.all.forEach { e ->
                    OptionRow(Equipment.label(e), Equipment.hint(e), equipment == e) { equipment = e }
                }
            }

            ProgramCard {
                Text("Focus on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "These muscle groups get about 4 more sets a week, an extra exercise and an early spot in each session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.all.forEach { g ->
                        val selected = g in focus
                        FilterChip(
                            selected = selected,
                            onClick = {
                                focus = if (selected) focus - g else focus + g
                                reduce = reduce - g
                            },
                            label = { Text(MuscleGroup.label(g)) },
                        )
                    }
                }
                if (focus.size > FOCUS_HINT) {
                    Text(
                        "With ${focus.size} focus groups, sessions get longer and each one stands out less. " +
                            "The session limit keeps the extra sets in check.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Warmup,
                    )
                }
            }

            ProgramCard {
                Text("Reduce", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "For an injury, or muscles you train elsewhere. They're kept at maintenance, about 3 to 6 sets a week: " +
                        "one exercise in your plan, added to another day if needed, or none when other lifts already work them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.all.forEach { g ->
                        val selected = g in reduce
                        FilterChip(
                            selected = selected,
                            onClick = {
                                reduce = if (selected) reduce - g else reduce + g
                                focus = focus - g
                            },
                            label = { Text(MuscleGroup.label(g)) },
                        )
                    }
                }
            }

            PreviewCard(preview, goal, level, focus)

            Text(
                "Your first session at each exercise calibrates the weights: pick a load you could lift for 2 to 3 more reps. " +
                    "Exercises you've logged before start from your history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { if (existing != null) confirmReplace = true else create() },
                enabled = preview.days.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (existing != null) "Replace program" else "Create program", fontWeight = FontWeight.SemiBold)
            }

            HowItWorksCard()
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your program?") },
            text = { Text("The current program's targets and progress are replaced. Workouts you logged stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    create()
                }) { Text("Replace", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

/** The plan these answers produce: its days and the weekly sets each muscle group gets. */
@Composable
private fun PreviewCard(p: com.tooheavy.app.data.Program, goal: String, level: String, focus: List<String>) {
    ProgramCard {
        Text("Your plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (p.days.isEmpty()) {
            Text("Nothing left to train. Reduce fewer muscle groups.", color = Danger, style = MaterialTheme.typography.bodyMedium)
            return@ProgramCard
        }
        Text(
            "${p.name} · ${p.days.size}-day rotation, ${p.sessionsPerWeek} sessions a week",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        p.days.forEach { d ->
            Column {
                Text(
                    "${d.name} · ${d.exercises.size} exercises · ${d.exercises.sumOf { it.sets }} sets · about ${d.estimatedMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    d.exercises.joinToString(" · ") { Repo.exercise(it.exerciseId)?.name ?: "Exercise" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Weekly sets per muscle group", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Indirect work counts as half a set: bench press also trains triceps. The marker shows the range research suggests for you.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        val weekly = p.weeklyGroupSets
        MuscleGroup.all.forEach { g ->
            val v = weekly[g] ?: 0.0
            val range = weeklySetRange(goal, level, g in focus, g, g in p.reduce)
            val color = when {
                v < range.start - 0.01 -> Warmup
                v > range.endInclusive + 0.01 -> Danger
                else -> AccentBright
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        MuscleGroup.label(g) + when (g) {
                            in focus -> " · focus"
                            in p.reduce -> " · reduced"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${oneDecimal(v)} sets · aim ${range.start.toInt()}-${range.endInclusive.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Meter((v / (range.endInclusive * 1.25)).toFloat().coerceIn(0f, 1f), color, height = 5.dp)
            }
        }
    }
}

private fun oneDecimal(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")

/** What the plan is built on and how it adapts, in plain words. */
@Composable
private fun HowItWorksCard() {
    ProgramCard {
        Text("How your plan works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        listOf(
            "Volume" to "Muscles grow best on about 10 to 20 hard sets a week, with less needed when you're new to " +
                "lifting. Every muscle group is balanced into a range for your experience, and sessions are kept short " +
                "enough that the last sets still count.",
            "Frequency" to "Each muscle is trained about twice a week, which beats once a week for the same total volume.",
            "Effort" to "Sets should end 1 to 3 reps short of failure. Each block starts easier and gets harder week by " +
                "week. Log your reps in reserve (RIR) and the plan speeds up when sets felt easy and holds back when " +
                "they went to failure.",
            "Progression" to "Reps climb through the range at the same weight; once every set reaches the top, the " +
                "weight goes up. Three stalled sessions bring the weight down about 10 % to build back up.",
            "Recovery" to "Every block ends in a deload week. If many lifts stall at once, the deload comes early.",
            "Time" to "Session length counts every set with its rest, warm-ups and moving between exercises. To fit your " +
                "time, sets come off first, then whole exercises from the muscles with the most work to spare, never " +
                "the main lifts. Short sessions rest a little less between sets.",
            "Reduced muscles" to "About a third of the usual volume, even once a week, is enough to keep muscle and " +
                "strength, so reduced groups get 3 to 6 sets a week.",
            "Adapting to you" to "After each block, exercises that earned extra sets keep one and exercises that " +
                "needed fewer start lower. Blocks get a week longer when you progress steadily (up to 6 weeks) and " +
                "shorter after an early deload.",
        ).forEach { (title, text) ->
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Based on meta-analyses and reviews by Schoenfeld et al. (volume, frequency, loading, rest), Pelland et al. " +
                "2024 (volume dose-response), Refalo et al. 2023 (proximity to failure), Helms et al. 2016 (RIR) and " +
                "Bell et al. 2023 (deloading), Bickel et al. 2011 and Spiering et al. 2021 (maintenance), " +
                "Iversen et al. 2021 (time-efficient training).",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun OptionRow(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
