package com.tooheavy.app.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.formatNutrient
import com.tooheavy.app.data.friendlyDate
import com.tooheavy.app.data.mealTotals
import kotlin.math.roundToInt

/**
 * The foods added since the food search was opened, so the search can show what you're adding, e.g. the parts of
 * a meal. They are in the diary already; this only keeps track of which ones. Starts empty with each new search.
 */
object AddingFoods {
    /** Ids of the diary entries added, in order. */
    val ids = mutableStateListOf<String>()

    fun added(entryId: String) {
        if (entryId !in ids) ids.add(entryId)
    }

    fun clear() = ids.clear()
}

/** Clears [AddingFoods] when a food search opens (not when coming back to it from a food's amount). */
@Composable
fun StartAddingFoods() {
    var started by rememberSaveable { mutableStateOf(false) }
    if (!started) {
        started = true
        AddingFoods.clear()
    }
}

/**
 * The foods added in this search, at the bottom of it: how many and what they come to, each with its amount and
 * calories (tap to change it, ✕ to take it out again), and Done to go back to the diary.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddedFoodsPanel(onEdit: (String) -> Unit, onDone: () -> Unit) {
    val entries = AddingFoods.ids.mapNotNull { id -> NutritionRepo.entries.firstOrNull { it.id == id } }
    if (entries.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(true) }
    val total = mealTotals(entries)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val n = entries.size
                    Text(
                        "${if (n == 1) "1 food" else "$n foods"} · ${total.kcal.roundToInt()} kcal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Added to ${friendlyDate(entries.first().date)} · P ${formatNutrient(total.protein)} · " +
                            "C ${formatNutrient(total.carbs)} · F ${formatNutrient(total.fat)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (open) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (open) "Hide foods" else "Show foods",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDone) { Text("Done") }
            }
            // While typing, only the summary line, to leave room for the search results.
            if (open && !WindowInsets.isImeVisible) {
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                    items(entries, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onEdit(e.id) }.padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (e.amountLabel.isNotBlank()) {
                                    Text(
                                        e.amountLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Text("${e.kcal.roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = {
                                NutritionRepo.deleteEntry(e.id)
                                AddingFoods.ids.remove(e.id)
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Take ${e.name} out",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
