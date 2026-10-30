package com.tooheavy.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tooheavy.app.data.Repo
import com.tooheavy.app.data.formatClock
import com.tooheavy.app.ui.active.ActiveWorkoutScreen
import com.tooheavy.app.ui.exercises.ExerciseDetailScreen
import com.tooheavy.app.ui.exercises.ExerciseListScreen
import com.tooheavy.app.ui.exercises.ExercisePickerScreen
import com.tooheavy.app.ui.history.HistoryScreen
import com.tooheavy.app.ui.history.WorkoutDetailScreen
import com.tooheavy.app.ui.nutrition.BarcodeLookupScreen
import com.tooheavy.app.ui.nutrition.CustomFoodScreen
import com.tooheavy.app.ui.nutrition.FoodAmountScreen
import com.tooheavy.app.ui.nutrition.FoodSearchScreen
import com.tooheavy.app.ui.nutrition.NutritionGoalsScreen
import com.tooheavy.app.ui.nutrition.NutritionScreen
import com.tooheavy.app.ui.profile.ProfileScreen
import com.tooheavy.app.ui.workout.RoutineEditScreen
import com.tooheavy.app.ui.workout.WorkoutTabScreen
import kotlinx.coroutines.delay

/** Navigation routes. Use these helpers with nav.navigate(...). */
object Routes {
    const val WORKOUT = "workout"
    const val HISTORY = "history"
    const val EXERCISES = "exercises"
    const val PROFILE = "profile"
    const val ACTIVE = "active"
    const val NUTRITION = "nutrition"
    const val NUTRITION_GOALS = "nutrition_goals"

    /** Exercise library detail page. */
    fun exercise(id: String) = "exercise/${Uri.encode(id)}"

    /** Finished workout detail page. */
    fun workoutDetail(id: String) = "workout_detail/$id"

    /** Routine editor; id = "new" for a new routine. */
    fun routineEdit(id: String) = "routine_edit/$id"

    /** Exercise picker; target = PICK_FOR_ACTIVE or PICK_FOR_ROUTINE. */
    fun picker(target: String) = "picker/$target"
    const val PICK_FOR_ACTIVE = "active"
    const val PICK_FOR_ROUTINE = "routine"

    /** Food search for a meal (Meal.BREAKFAST etc.); logs into NutritionRepo.selectedDate. */
    fun foodSearch(meal: String) = "food_search/$meal"

    /** Amount picker for a food (Food.key) to add to [meal]. */
    fun foodAmount(foodKey: String, meal: String) = "food_amount?food=${Uri.encode(foodKey)}&meal=$meal"

    /** Amount picker editing an existing FoodEntry. */
    fun editFoodEntry(entryId: String) = "food_amount?entry=$entryId"

    /**
     * Custom food editor; key = "new" or a local Food.key. Optional prefill: [barcode], [name], [brand].
     * When [meal] is set, saving continues to the amount screen to log the food into that meal.
     */
    fun customFood(key: String, barcode: String? = null, meal: String? = null, name: String? = null, brand: String? = null): String {
        val q = listOfNotNull(
            barcode?.let { "barcode=${Uri.encode(it)}" },
            meal?.let { "meal=$it" },
            name?.let { "name=${Uri.encode(it)}" },
            brand?.let { "brand=${Uri.encode(it)}" },
        ).joinToString("&")
        return "custom_food/${Uri.encode(key)}" + if (q.isEmpty()) "" else "?$q"
    }

    /** Route pattern of the custom food editor (for popUpTo). */
    const val CUSTOM_FOOD_PATTERN = "custom_food/{key}?barcode={barcode}&meal={meal}&name={name}&brand={brand}"

    /** Barcode lookup (local foods first, then Open Food Facts) that ends on the amount screen or the custom food editor. */
    fun barcode(code: String, meal: String) = "barcode/${Uri.encode(code)}/$meal"

    /** Route pattern of the barcode lookup screen (for popUpTo). */
    const val BARCODE_PATTERN = "barcode/{code}/{meal}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.WORKOUT, "Workout", Icons.Filled.FitnessCenter),
    Tab(Routes.HISTORY, "History", Icons.Filled.History),
    Tab(Routes.EXERCISES, "Exercises", Icons.Filled.MenuBook),
    Tab(Routes.NUTRITION, "Food", Icons.Filled.Restaurant),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTab) {
                Column {
                    if (Repo.activeWorkout != null) ActiveWorkoutBanner { nav.navigate(Routes.ACTIVE) }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { nav.navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.WORKOUT,
            modifier = Modifier.padding(pad).consumeWindowInsets(pad),
        ) {
            composable(Routes.WORKOUT) { WorkoutTabScreen(nav) }
            composable(Routes.HISTORY) { HistoryScreen(nav) }
            composable(Routes.EXERCISES) { ExerciseListScreen(nav) }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.ACTIVE) { ActiveWorkoutScreen(nav) }
            composable("exercise/{id}") { ExerciseDetailScreen(nav, it.arguments?.getString("id").orEmpty()) }
            composable("workout_detail/{id}") { WorkoutDetailScreen(nav, it.arguments?.getString("id").orEmpty()) }
            composable("routine_edit/{id}") { RoutineEditScreen(nav, it.arguments?.getString("id").orEmpty()) }
            composable("picker/{target}") { ExercisePickerScreen(nav, it.arguments?.getString("target").orEmpty()) }
            composable(Routes.NUTRITION) { NutritionScreen(nav) }
            composable(Routes.NUTRITION_GOALS) { NutritionGoalsScreen(nav) }
            composable("food_search/{meal}") { FoodSearchScreen(nav, it.arguments?.getString("meal").orEmpty()) }
            composable(
                "food_amount?food={food}&meal={meal}&entry={entry}",
                arguments = listOf(
                    navArgument("food") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("meal") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("entry") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                FoodAmountScreen(
                    nav,
                    foodKey = it.arguments?.getString("food"),
                    meal = it.arguments?.getString("meal") ?: com.tooheavy.app.data.Meal.SNACK,
                    entryId = it.arguments?.getString("entry"),
                )
            }
            composable(
                Routes.CUSTOM_FOOD_PATTERN,
                arguments = listOf("barcode", "meal", "name", "brand").map { n ->
                    navArgument(n) { type = NavType.StringType; nullable = true; defaultValue = null }
                },
            ) {
                val a = it.arguments
                CustomFoodScreen(
                    nav,
                    key = a?.getString("key").orEmpty(),
                    barcode = a?.getString("barcode"),
                    meal = a?.getString("meal"),
                    prefillName = a?.getString("name"),
                    prefillBrand = a?.getString("brand"),
                )
            }
            composable(Routes.BARCODE_PATTERN) {
                BarcodeLookupScreen(nav, it.arguments?.getString("code").orEmpty(), it.arguments?.getString("meal").orEmpty())
            }
        }
    }
}

private fun NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun ActiveWorkoutBanner(onClick: () -> Unit) {
    val w = Repo.activeWorkout ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Workout in progress", fontWeight = FontWeight.SemiBold)
            Text(w.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatClock(now - w.startTime), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.padding(start = 8.dp))
        Text("Resume", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}
