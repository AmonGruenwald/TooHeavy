package com.tooheavy.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recipes, optional nutrients, water and custom exercises (1.5.0). */
class NutritionFeaturesTest {
    private val gson = Gson()
    private val oats = Food(fdcId = 1, name = "Oats", kcal = 380.0, protein = 13.0, carbs = 60.0, fat = 7.0, fiber = 10.0)
    private val milk = Food(customId = "m", name = "Milk", source = Food.SOURCE_CUSTOM, kcal = 64.0, protein = 3.4, carbs = 4.8, fat = 3.5, salt = 0.1)

    @Test
    fun recipeNutritionIsPer100gOfTheFinishedDish() {
        val recipe = Recipe(listOf(oats.toIngredient(100.0, "100 g"), milk.toIngredient(300.0, "300 g")), servings = 2.0)
        val food = recipeFood("Porridge", recipe)
        // 400 g in total: 380 + 192 kcal.
        assertEquals(143.0, food.kcal, 0.01)
        assertEquals((13.0 + 10.2) / 4, food.protein, 0.01)
        assertEquals(Food.SOURCE_RECIPE, food.source)
        assertEquals(listOf(Portion("1 serving", 200.0), Portion("Whole recipe", 400.0)), food.portions)
        // Fiber comes only from the oats and salt only from the milk; both are known for the recipe.
        assertEquals(2.5, food.fiber!!, 0.01)
        assertEquals(0.075, food.salt!!, 0.01)
        assertNull(food.sugar)
    }

    @Test
    fun cookedWeightConcentratesTheRecipe() {
        val recipe = Recipe(listOf(oats.toIngredient(100.0, "100 g"), milk.toIngredient(300.0, "300 g")), cookedGrams = 200.0)
        val food = recipeFood("Porridge", recipe)
        assertEquals(286.0, food.kcal, 0.01)
        assertEquals(listOf(Portion("1 serving", 200.0)), food.portions)
    }

    @Test
    fun extrasSumOnlyKnownValues() {
        val sum = Extras(fiber = 2.0) + Extras(salt = 1.0) + Extras(fiber = 1.5)
        assertEquals(Extras(fiber = 3.5, salt = 1.0), sum)
        assertEquals(Extras(), Extras() + Extras())
    }

    @Test
    fun timeOnKeepsTheTimeOfDay() {
        val morning = timeOn("2026-10-29", 7, 45)
        assertEquals("07:45", clockTime(timeOn("2026-10-30", morning)))
        assertEquals(1, daysBetween("2026-10-29", "2026-10-30"))
        assertEquals(24 * 3600_000L, timeOn("2026-10-30", morning) - morning)
    }

    @Test
    fun newNutritionFieldsRoundTripAndOldFilesLoad() {
        val recipe = recipeFood("Porridge", Recipe(listOf(oats.toIngredient(50.0, "50 g"))), customId = "r")
        val data = NutritionData(
            customFoods = listOf(recipe),
            entries = listOf(FoodEntry(id = "e", date = "2026-10-30", name = "Porridge", kcal = 190.0, fiber = 5.0, salt = 0.2)),
            goals = NutritionGoals(waterMl = 3000),
            water = listOf(WaterEntry("w", "2026-10-30", 500, 1L)),
        )
        assertEquals(data, gson.fromJson(gson.toJson(data), NutritionData::class.java))

        val old = gson.fromJson(
            """{"entries":[{"id":"a","date":"2026-10-01","meal":"lunch","name":"Soup","kcal":200.0,"createdAt":5}],
               "goals":{"kcal":2000,"protein":150,"carbs":200,"fat":60}}""",
            NutritionData::class.java,
        )
        assertEquals("lunch", old.entries[0].meal)
        assertNull(old.entries[0].salt)
        assertEquals(2500, old.goals.waterMl)
        assertTrue(old.water.isEmpty())
    }

    @Test
    fun customExercisesRoundTrip() {
        val e = Exercise(id = Exercise.newCustomId(), name = "Landmine Press", equipment = "barbell", primaryMuscles = listOf("shoulders"))
        assertTrue(e.isCustom)
        val data = AppData(customExercises = listOf(e))
        assertEquals(data, gson.fromJson(gson.toJson(data), AppData::class.java))
        assertTrue(gson.fromJson("""{"workouts":[]}""", AppData::class.java).customExercises.isEmpty())
    }
}
