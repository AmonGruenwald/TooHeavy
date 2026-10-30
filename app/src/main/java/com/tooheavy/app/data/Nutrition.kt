package com.tooheavy.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// ---------- models ----------

data class Portion(
    @SerializedName("l") val label: String = "",
    @SerializedName("g") val grams: Double = 0.0,
)

/**
 * A food with nutrition per 100 g. Bundled foods come from USDA FoodData Central (public domain):
 * source "sr" = SR Legacy, "fndds" = FNDDS survey foods. User-created foods have source "custom".
 */
data class Food(
    @SerializedName("i") val fdcId: Long = 0,
    @SerializedName("id") val customId: String? = null,
    @SerializedName("n") val name: String = "",
    @SerializedName("src") val source: String = "",
    @SerializedName("k") val kcal: Double = 0.0,
    @SerializedName("p") val protein: Double = 0.0,
    @SerializedName("c") val carbs: Double = 0.0,
    @SerializedName("f") val fat: Double = 0.0,
    @SerializedName("fb") val fiber: Double? = null,
    @SerializedName("s") val sugar: Double? = null,
    /** Household portions, e.g. "1 cup" = 240 g. May be null/empty; grams are always available. */
    @SerializedName("u") val portions: List<Portion>? = null,
    /** EAN/UPC barcode for scanned or user-entered packaged foods. */
    @SerializedName("bc") val barcode: String? = null,
    @SerializedName("b") val brand: String? = null,
) {
    /** Stable key used in routes and FoodEntry.foodKey: "usda:<fdcId>" or "custom:<customId>" (custom and Open Food Facts foods). */
    val key: String get() = if (customId != null) "custom:$customId" else "usda:$fdcId"

    /** True for foods stored on the device (user-created or saved from Open Food Facts); these can be edited/deleted. */
    val isLocal: Boolean get() = customId != null

    fun macrosFor(grams: Double): Macros {
        val f = grams / 100.0
        return Macros(kcal * f, protein * f, carbs * f, fat * f)
    }

    companion object {
        const val SOURCE_CUSTOM = "custom"
        /** Saved from an Open Food Facts barcode lookup. */
        const val SOURCE_OFF = "off"
    }
}

data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
}

object Meal {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"
    val all = listOf(BREAKFAST, LUNCH, DINNER, SNACK)
    fun label(meal: String) = when (meal) {
        BREAKFAST -> "Breakfast"
        LUNCH -> "Lunch"
        DINNER -> "Dinner"
        else -> "Snacks"
    }
}

/** One logged food. Nutrition is a snapshot, so history survives deleting a custom food. */
data class FoodEntry(
    val id: String = newId(),
    /** Local date "yyyy-MM-dd". */
    val date: String = "",
    val meal: String = Meal.BREAKFAST,
    /** Food.key, or null for a quick-add entry. */
    val foodKey: String? = null,
    val name: String = "",
    /** Total grams eaten (0 for quick-add). */
    val grams: Double = 0.0,
    /** Human-readable amount, e.g. "2 × 1 cup" or "150 g". */
    val amountLabel: String = "",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val macros: Macros get() = Macros(kcal, protein, carbs, fat)
}

data class NutritionGoals(
    val kcal: Int = 2200,
    val protein: Int = 150,
    val carbs: Int = 230,
    val fat: Int = 70,
)

data class BodyProfile(
    /** "male" or "female" */
    val sex: String = "male",
    val age: Int = 30,
    val heightCm: Double = 178.0,
    val weightKg: Double = 80.0,
    /** Activity multiplier: 1.2 sedentary, 1.375 light, 1.55 moderate, 1.725 very active, 1.9 extra active. */
    val activity: Double = 1.55,
    /** "lose", "maintain" or "gain" */
    val goal: String = "maintain",
)

data class WeightEntry(val date: String = "", val kg: Double = 0.0)

data class NutritionData(
    val entries: List<FoodEntry> = emptyList(),
    val customFoods: List<Food> = emptyList(),
    val goals: NutritionGoals = NutritionGoals(),
    val profile: BodyProfile = BodyProfile(),
    val weights: List<WeightEntry> = emptyList(),
)

// ---------- dates ----------

private val dayFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** Today's local date as "yyyy-MM-dd". */
fun today(): String = dayFormat.format(Calendar.getInstance().time)

/** Date string shifted by [days], e.g. shiftDate("2026-10-30", -1) == "2026-10-29". */
fun shiftDate(date: String, days: Int): String {
    val c = Calendar.getInstance()
    c.time = dayFormat.parse(date) ?: return date
    c.add(Calendar.DAY_OF_YEAR, days)
    return dayFormat.format(c.time)
}

/** "Today", "Yesterday", "Tomorrow" or e.g. "Mon, Oct 27". */
fun friendlyDate(date: String): String = when (date) {
    today() -> "Today"
    shiftDate(today(), -1) -> "Yesterday"
    shiftDate(today(), 1) -> "Tomorrow"
    else -> dayFormat.parse(date)?.let { SimpleDateFormat("EEE, MMM d", Locale.US).format(it) } ?: date
}

/** 12.345 -> "12", 0.46 -> "0.5" (1 decimal below 10, integer above). */
fun formatNutrient(v: Double): String =
    if (v >= 10 || v == Math.floor(v)) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v)

// ---------- calculator ----------

/** Mifflin-St Jeor BMR x activity. */
fun BodyProfile.tdee(): Int {
    val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == "female") -161 else 5
    return (bmr * activity).roundToInt()
}

/** Goals from the profile: -500 kcal to lose, +300 to gain; protein 2 g/kg, fat 25 % of kcal, rest carbs. */
fun BodyProfile.suggestedGoals(): NutritionGoals {
    val kcal = tdee() + when (goal) { "lose" -> -500; "gain" -> 300; else -> 0 }
    val protein = (weightKg * 2.0).roundToInt()
    val fat = (kcal * 0.25 / 9).roundToInt()
    val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
    return NutritionGoals(kcal, protein, carbs, fat)
}

// ---------- store ----------

/** Calorie tracker store. Compose-observable; every mutation persists to filesDir/nutrition.json. Main thread only. */
object NutritionRepo {
    private val gson = Gson()
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var file: File

    /** Bundled USDA foods (loaded in the background; check foodsLoaded). */
    var foods: List<Food> = emptyList()
        private set
    private var lowerNames: List<String> = emptyList()
    private var usdaById: Map<Long, Food> = emptyMap()
    var foodsLoaded by mutableStateOf(false)
        private set

    val customFoods = mutableStateListOf<Food>()
    val entries = mutableStateListOf<FoodEntry>()
    /** Body-weight log, sorted by date ascending, at most one per date. */
    val weights = mutableStateListOf<WeightEntry>()
    var goals by mutableStateOf(NutritionGoals())
        private set
    var profile by mutableStateOf(BodyProfile())
        private set

    /** Diary date currently shown ("yyyy-MM-dd"); food screens log into this date. */
    var selectedDate by mutableStateOf(today())

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "nutrition.json")
        if (file.exists()) {
            runCatching { gson.fromJson(file.readText(), NutritionData::class.java) }.getOrNull()?.let { d ->
                entries.addAll(d.entries.orEmpty())
                customFoods.addAll(d.customFoods.orEmpty())
                weights.addAll(d.weights.orEmpty().sortedBy { it.date })
                goals = d.goals ?: NutritionGoals()
                profile = d.profile ?: BodyProfile()
            }
        }
        val app = context.applicationContext
        Thread {
            val list: List<Food> = runCatching {
                app.assets.open("foods.json").bufferedReader().use {
                    gson.fromJson<List<Food>>(it, object : TypeToken<List<Food>>() {}.type)
                }
            }.getOrDefault(emptyList())
            val names = list.map { it.name.lowercase(Locale.US) }
            val byId = list.associateBy { it.fdcId }
            Handler(Looper.getMainLooper()).post {
                foods = list
                lowerNames = names
                usdaById = byId
                foodsLoaded = true
            }
        }.start()
    }

    private fun save() {
        val snapshot = gson.toJson(NutritionData(entries.toList(), customFoods.toList(), goals, profile, weights.toList()))
        io.execute {
            val tmp = File(file.parentFile, "nutrition.json.tmp")
            tmp.writeText(snapshot)
            if (!tmp.renameTo(file)) {
                file.delete(); tmp.renameTo(file)
            }
        }
    }

    // ---------- foods ----------

    fun food(key: String): Food? = when {
        key.startsWith("custom:") -> customFoods.firstOrNull { it.key == key }
        key.startsWith("usda:") -> key.removePrefix("usda:").toLongOrNull()?.let { usdaById[it] }
        else -> null
    }

    /**
     * Ranked search over custom + bundled foods. Every whitespace-separated token must appear in the name.
     * Custom foods first, then names starting with the query, then word-start matches, then shorter names.
     */
    fun search(query: String, limit: Int = 100): List<Food> {
        val tokens = query.lowercase(Locale.US).split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val first = tokens.first()
        fun score(name: String): Int = when {
            name.startsWith(first) -> 0
            name.contains(" $first") || name.contains(",$first") -> 1
            else -> 2
        }
        val q = query.trim()
        val custom = customFoods.filter { f ->
            val n = (f.name + " " + f.brand.orEmpty()).lowercase(Locale.US)
            f.barcode == q || tokens.all { n.contains(it) }
        }
        val hits = ArrayList<Pair<Int, Int>>()
        for (i in lowerNames.indices) {
            val n = lowerNames[i]
            if (tokens.all { n.contains(it) }) hits.add(i to score(n))
        }
        hits.sortWith(compareBy<Pair<Int, Int>> { it.second }.thenBy { lowerNames[it.first].length })
        return (custom + hits.take(limit).map { foods[it.first] }).take(limit)
    }

    /** Distinct foods from the most recent entries (newest first), resolved to current Food objects. */
    fun recentFoods(limit: Int = 25): List<Food> =
        entries.sortedByDescending { it.createdAt }
            .mapNotNull { it.foodKey }
            .distinct()
            .mapNotNull { food(it) }
            .take(limit)

    fun saveCustomFood(f: Food): Food {
        val food = f.copy(
            source = if (f.source == Food.SOURCE_OFF) Food.SOURCE_OFF else Food.SOURCE_CUSTOM,
            customId = f.customId ?: newId(),
            barcode = f.barcode?.trim()?.takeIf { it.isNotEmpty() },
        )
        val i = customFoods.indexOfFirst { it.key == food.key }
        if (i >= 0) customFoods[i] = food else customFoods.add(0, food)
        save()
        return food
    }

    /** Locally stored food (custom or saved from Open Food Facts) with this barcode, if any. */
    fun foodByBarcode(barcode: String): Food? = customFoods.firstOrNull { it.barcode == barcode.trim() }

    fun deleteCustomFood(key: String) {
        customFoods.removeAll { it.key == key }
        save()
    }

    // ---------- diary ----------

    fun entriesFor(date: String): List<FoodEntry> = entries.filter { it.date == date }.sortedBy { it.createdAt }

    fun entriesFor(date: String, meal: String): List<FoodEntry> = entriesFor(date).filter { it.meal == meal }

    fun totals(date: String): Macros = entries.filter { it.date == date }.fold(Macros()) { acc, e -> acc + e.macros }

    fun entry(id: String): FoodEntry? = entries.firstOrNull { it.id == id }

    /** Builds an entry for [grams] of [food]; caller passes it to addEntry/updateEntry. */
    fun makeEntry(food: Food, grams: Double, amountLabel: String, meal: String, date: String = selectedDate, id: String = newId()): FoodEntry {
        val m = food.macrosFor(grams)
        return FoodEntry(
            id = id, date = date, meal = meal, foodKey = food.key, name = food.name, grams = grams,
            amountLabel = amountLabel, kcal = m.kcal, protein = m.protein, carbs = m.carbs, fat = m.fat,
        )
    }

    fun addEntry(e: FoodEntry) {
        entries.add(e)
        save()
    }

    fun updateEntry(e: FoodEntry) {
        val i = entries.indexOfFirst { it.id == e.id }
        if (i >= 0) entries[i] = e else entries.add(e)
        save()
    }

    fun deleteEntry(id: String) {
        entries.removeAll { it.id == id }
        save()
    }

    /** Copies all entries of [meal] on [fromDate] into the same meal on [toDate]. */
    fun copyMeal(fromDate: String, toDate: String, meal: String) {
        entriesFor(fromDate, meal).forEach { entries.add(it.copy(id = newId(), date = toDate, createdAt = System.currentTimeMillis())) }
        save()
    }

    // ---------- goals / profile / weight ----------

    fun updateGoals(g: NutritionGoals) {
        goals = g
        save()
    }

    fun updateProfile(p: BodyProfile) {
        profile = p
        save()
    }

    fun logWeight(date: String, kg: Double) {
        weights.removeAll { it.date == date }
        weights.add(WeightEntry(date, kg))
        weights.sortBy { it.date }
        if (date >= (weights.lastOrNull()?.date ?: date)) profile = profile.copy(weightKg = kg)
        save()
    }

    fun deleteWeight(date: String) {
        weights.removeAll { it.date == date }
        save()
    }
}
