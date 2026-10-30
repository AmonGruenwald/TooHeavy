package com.tooheavy.app.ui

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.tooheavy.app.ui.common.AppBackground
import com.tooheavy.app.ui.common.GlassCard
import com.tooheavy.app.ui.common.Pill
import com.tooheavy.app.ui.common.UpdateDialog
import com.tooheavy.app.ui.common.WhatsNewDialog
import com.tooheavy.app.ui.home.DashboardScreen
import com.tooheavy.app.ui.theme.BrandColors
import com.tooheavy.app.ui.theme.Ink
import com.tooheavy.app.ui.theme.StatNumber
import com.tooheavy.app.ui.theme.Success
import com.tooheavy.app.ui.theme.Surface2
import com.tooheavy.app.ui.theme.TextMuted
import com.tooheavy.app.ui.theme.TextSecondary
import com.tooheavy.app.ui.exercises.ExerciseDetailScreen
import com.tooheavy.app.ui.exercises.ExerciseListScreen
import com.tooheavy.app.ui.exercises.ExercisePickerScreen
import com.tooheavy.app.ui.history.HistoryScreen
import com.tooheavy.app.ui.history.WorkoutDetailScreen
import com.tooheavy.app.ui.nutrition.BarcodeLookupScreen
import com.tooheavy.app.ui.nutrition.CoachScreen
import com.tooheavy.app.ui.nutrition.CustomFoodScreen
import com.tooheavy.app.ui.nutrition.FoodAmountScreen
import com.tooheavy.app.ui.nutrition.FoodSearchScreen
import com.tooheavy.app.ui.nutrition.NutritionGoalsScreen
import com.tooheavy.app.ui.nutrition.NutritionScreen
import com.tooheavy.app.ui.profile.ProfileScreen
import com.tooheavy.app.ui.program.ProgramScreen
import com.tooheavy.app.ui.program.ProgramSetupScreen
import com.tooheavy.app.ui.workout.RoutineEditScreen
import com.tooheavy.app.ui.workout.WorkoutTabScreen
import kotlinx.coroutines.delay

/** Navigation routes. Use these helpers with nav.navigate(...). */
object Routes {
    /** Customizable dashboard; the start destination. */
    const val HOME = "home"
    const val WORKOUT = "workout"
    const val HISTORY = "history"
    const val EXERCISES = "exercises"
    const val PROFILE = "profile"
    const val ACTIVE = "active"
    const val NUTRITION = "nutrition"
    const val NUTRITION_GOALS = "nutrition_goals"
    /** Adaptive calorie coach: expenditure, weight trend, plan and weekly check-ins. */
    const val COACH = "coach"
    /** Adaptive training program overview. */
    const val PROGRAM = "program"
    /** Questionnaire that generates (or replaces) the training program. */
    const val PROGRAM_SETUP = "program_setup"

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
    Tab(Routes.HOME, "Home", Icons.Filled.SpaceDashboard),
    Tab(Routes.WORKOUT, "Train", Icons.Filled.FitnessCenter),
    Tab(Routes.HISTORY, "History", Icons.Filled.History),
    Tab(Routes.EXERCISES, "Library", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Routes.NUTRITION, "Food", Icons.Filled.Restaurant),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (onTab) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.94f), Ink)))
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (Repo.activeWorkout != null) ActiveWorkoutBanner { nav.navigate(Routes.ACTIVE) }
                        TabBar(route) { nav.navigateTab(it) }
                    }
                }
            },
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(pad).consumeWindowInsets(pad),
                enterTransition = { fadeIn(tween(220, delayMillis = 70)) + scaleIn(tween(260, delayMillis = 70), initialScale = 0.97f) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(220, delayMillis = 70)) },
                popExitTransition = { fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.97f) },
            ) {
                composable(Routes.HOME) { DashboardScreen(nav) }
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
                composable(Routes.COACH) { CoachScreen(nav) }
                composable(Routes.PROGRAM) { ProgramScreen(nav) }
                composable(Routes.PROGRAM_SETUP) { ProgramSetupScreen(nav) }
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
    UpdateDialog()
    WhatsNewDialog()
}

/** Switches to a bottom-bar tab, keeping each tab's back stack and state. */
fun NavController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun TabBar(route: String?, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(shape)
            .background(Surface2.copy(alpha = 0.97f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))), shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            val glow by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "tab")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .drawBehind {
                        if (glow > 0f) {
                            drawRoundRect(
                                Brush.linearGradient(BrandColors.map { it.copy(alpha = 0.9f) }),
                                alpha = glow,
                                cornerRadius = CornerRadius(20.dp.toPx()),
                            )
                        }
                    }
                    .clickable { onSelect(tab.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) Color.White else TextMuted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = if (selected) Color.White else TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
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
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(Success))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Workout in progress", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatClock(now - w.startTime), style = StatNumber.copy(fontSize = 22.sp))
            Spacer(Modifier.width(12.dp))
            Pill("Resume", Color.White)
        }
    }
}
