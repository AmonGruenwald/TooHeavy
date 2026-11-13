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
    fun foodsLoggedAgainAreCopiedToNowInTheirOrder() {
        val breakfast = FoodEntry(id = "a", date = "2026-11-08", name = "Oats", grams = 80.0, amountLabel = "80 g", kcal = 300.0, createdAt = 2_000)
        val coffee = FoodEntry(id = "b", date = "2026-11-08", name = "Coffee", kcal = 20.0, createdAt = 1_000)
        val copies = copiesAt(listOf(breakfast, coffee), "2026-11-10", 5_000_000)
        // In the order they were eaten, a millisecond apart, today, with new ids and the same food.
        assertEquals(listOf("Coffee", "Oats"), copies.map { it.name })
        assertEquals(listOf(5_000_000L, 5_000_001L), copies.map { it.createdAt })
        assertTrue(copies.all { it.date == "2026-11-10" })
        assertTrue(copies.none { it.id == "a" || it.id == "b" })
        assertEquals(breakfast.copy(id = copies[1].id, date = "2026-11-10", createdAt = 5_000_001), copies[1])
        assertTrue(copiesAt(emptyList(), "2026-11-10", 0).isEmpty())
    }

    @Test
    fun entriesWithAGapBetweenThemAreSeparateMeals() {
        val min = 60_000L
        fun food(id: String, at: Long, kcal: Double) = FoodEntry(id = id, date = "2026-11-10", name = id, kcal = kcal, protein = 10.0, createdAt = at * min)
        // Breakfast drawn out over 50 minutes (no gap over 30), then lunch hours later, then a snack.
        val day = listOf(food("oats", 0, 300.0), food("milk", 20, 100.0), food("coffee", 50, 20.0), food("soup", 240, 400.0), food("bar", 271, 200.0))
        val meals = splitIntoMeals(day, { it.createdAt })
        assertEquals(listOf(listOf("oats", "milk", "coffee"), listOf("soup"), listOf("bar")), meals.map { m -> m.map { it.id } })
        // Exactly the gap still counts as the same meal.
        assertEquals(1, splitIntoMeals(listOf(food("a", 0, 1.0), food("b", 30, 1.0)), { it.createdAt }).size)
        assertTrue(splitIntoMeals(emptyList<FoodEntry>(), { it.createdAt }).isEmpty())
        val breakfast = mealTotals(meals[0])
        assertEquals(420.0, breakfast.kcal, 1e-9)
        assertEquals(30.0, breakfast.protein, 1e-9)
    }

    @Test
    fun myFoodsMostLoggedFirst() {
        val bar = Food(customId = "bar", name = "Bar", source = Food.SOURCE_CUSTOM)
        val shake = Food(customId = "shake", name = "Shake", source = Food.SOURCE_CUSTOM)
        val soup = Food(customId = "soup", name = "Soup", source = Food.SOURCE_CUSTOM)
        val wrap = Food(customId = "wrap", name = "Wrap", source = Food.SOURCE_CUSTOM)
        fun ate(food: Food) = FoodEntry(date = "2026-11-10", foodKey = food.key, name = food.name)
        val entries = listOf(ate(shake), ate(soup), ate(shake), ate(soup), ate(shake), ate(wrap), FoodEntry(name = "Quick add"))
        // Shake 3x, soup 2x, wrap 1x; bar never, so it stays last.
        assertEquals(listOf("Shake", "Soup", "Wrap", "Bar"), sortedByUse(listOf(bar, wrap, soup, shake), entries).map { it.name })
        // Equally often: the order they came in.
        assertEquals(listOf("Wrap", "Bar"), sortedByUse(listOf(wrap, bar), emptyList()).map { it.name })
    }

    @Test
    fun selectedFoodsMoveAndCopyTogether() {
        val oats = FoodEntry(id = "a", date = "2026-11-10", name = "Oats", createdAt = timeOn("2026-11-10", 8, 30))
        val milk = FoodEntry(id = "b", date = "2026-11-10", name = "Milk", createdAt = oats.createdAt + 1)
        val coffee = FoodEntry(id = "c", date = "2026-11-10", name = "Coffee", createdAt = timeOn("2026-11-10", 9, 15))
        // To another day and time: the earliest goes there, the others keep their distance (to the millisecond).
        val lunch = timeOn("2026-11-12", 12, 0)
        val moved = entriesAt(listOf(coffee, oats, milk), "2026-11-12", lunch, copy = false)
        assertEquals(listOf("a", "b", "c"), moved.map { it.id })
        assertTrue(moved.all { it.date == "2026-11-12" })
        assertEquals(listOf("12:00", "12:00", "12:45"), moved.map { clockTime(it.createdAt) })
        assertEquals(listOf(lunch, lunch + 1), moved.take(2).map { it.createdAt })
        // Copies get new ids and keep everything else.
        val copied = entriesAt(listOf(oats), "2026-11-10", timeOn("2026-11-10", 19, 0), copy = true).single()
        assertTrue(copied.id != "a")
        assertEquals("19:00", clockTime(copied.createdAt))
        assertEquals(oats.copy(id = copied.id, createdAt = copied.createdAt), copied)
        assertTrue(entriesAt(emptyList(), "2026-11-10", lunch, copy = true).isEmpty())
    }

    @Test
    fun waterInFoodCountsTowardHydration() {
        val milkWithWater = Food(fdcId = 1, name = "Milk", kcal = 61.0, water = 88.0)
        assertEquals(176.0, milkWithWater.waterIn(200.0)!!, 1e-9)
        assertNull(oats.waterIn(80.0))
        // Logged: the entry keeps it; entries logged before, without it, take it from the food.
        val logged = FoodEntry(foodKey = milkWithWater.key, grams = 200.0, water = 176.0)
        val older = FoodEntry(foodKey = milkWithWater.key, grams = 100.0)
        assertEquals(176.0, entryWaterMl(logged, null), 1e-9)
        assertEquals(88.0, entryWaterMl(older, milkWithWater), 1e-9)
        assertEquals(0.0, entryWaterMl(older, oats), 1e-9)
        assertEquals(0.0, entryWaterMl(FoodEntry(name = "Quick add", kcal = 300.0), null), 1e-9)
        // A recipe: its ingredients' water, less what cooking boils off; per 100 g of the dish.
        val pasta = Food(fdcId = 2, name = "Pasta, dry", kcal = 371.0, water = 10.0).toIngredient(100.0, "100 g")
        val water = Food(fdcId = 3, name = "Water", water = 100.0).toIngredient(300.0, "300 ml")
        val recipe = Recipe(ingredients = listOf(pasta, water, Food(customId = "x", name = "Spice").toIngredient(5.0, "5 g")), cookedGrams = 250.0)
        assertEquals(310.0 - 155.0, recipeWater(recipe)!!, 1e-9)
        assertEquals(62.0, recipeFood("Pasta", recipe).water!!, 1e-9)
        assertNull(recipeWater(Recipe(ingredients = listOf(Food(customId = "y").toIngredient(50.0, "50 g")))))
        // Old data without water still reads.
        assertNull(Gson().fromJson("{\"n\":\"Oats\"}", Food::class.java).water)
        assertTrue(Gson().fromJson("{}", NutritionGoals::class.java).foodWater)
    }

    @Test
    fun quickAddPer100gScalesToTheAmount() {
        // As eaten: taken as given, missing macros are 0.
        assertEquals(Macros(250.0, 0.0, 30.0, 10.0), quickAddMacros(250.0, 10.0, 30.0, null, per100g = false, grams = null))
        // Per 100 g: 150 g of a food with 200 kcal, 8 g fat, 20 g carbs and 10 g protein per 100 g.
        val m = quickAddMacros(200.0, 8.0, 20.0, 10.0, per100g = true, grams = 150.0)!!
        assertEquals(300.0, m.kcal, 1e-9)
        assertEquals(12.0, m.fat, 1e-9)
        assertEquals(30.0, m.carbs, 1e-9)
        assertEquals(15.0, m.protein, 1e-9)
        // Per 100 g needs an amount; calories are required and can't be negative.
        assertNull(quickAddMacros(200.0, 8.0, 20.0, 10.0, per100g = true, grams = null))
        assertNull(quickAddMacros(200.0, 8.0, 20.0, 10.0, per100g = true, grams = 0.0))
        assertNull(quickAddMacros(null, 8.0, 20.0, 10.0, per100g = false, grams = null))
        assertNull(quickAddMacros(-5.0, null, null, null, per100g = false, grams = null))
    }

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
