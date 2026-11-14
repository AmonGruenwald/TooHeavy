package com.tooheavy.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Barcode lookup and product search against the Open Food Facts API (open database, ODbL). */
object OpenFoodFacts {
    sealed interface Result {
        /** [food] has source SOURCE_OFF and the barcode set, but no customId yet (save it with NutritionRepo.saveCustomFood).
         *  [complete] is false when the product exists but has no calorie data — let the user fill it in. */
        data class Found(val food: Food, val complete: Boolean) : Result
        data object NotFound : Result
        /** Network problem (offline, timeout, server error). */
        data class Error(val message: String) : Result
    }

    private const val FIELDS =
        "code,product_name,product_name_de,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity,product_quantity"
    private const val USER_AGENT = "TooHeavy/1.0 (Android; personal use)"

    sealed interface SearchResult {
        /** Products with a name and calories, best matches first; not saved yet (no customId). */
        data class Found(val foods: List<Food>) : SearchResult
        data class Error(val message: String) : SearchResult
    }

    /** Searches seen this session, so going back and forth doesn't ask again (the search API allows few calls a minute). */
    private val searchCache = object : LinkedHashMap<String, List<Food>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Food>>?) = size > 30
    }

    /** Products whose name or brand matches [query]; only products with a name and calories are returned. */
    suspend fun search(query: String, pageSize: Int = 20): SearchResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        val key = FoodMatch.normalize(q)
        synchronized(searchCache) { searchCache[key] }?.let { return@withContext SearchResult.Found(it) }
        try {
            val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=${URLEncoder.encode(q, "UTF-8")}" +
                "&search_simple=1&action=process&json=1&page_size=$pageSize&fields=$FIELDS"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val status = conn.responseCode
            if (status !in 200..299) return@withContext SearchResult.Error("Server error ($status)")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val products = json.optJSONArray("products")
            val foods = buildList {
                if (products != null) for (i in 0 until products.length()) {
                    val p = products.optJSONObject(i) ?: continue
                    val code = p.str("code") ?: continue
                    val food = parseFood(code, p)
                    if (food.name.isNotEmpty() && food.kcal > 0) add(food)
                }
            }.distinctBy { it.barcode }
            synchronized(searchCache) { searchCache[key] = foods }
            SearchResult.Found(foods)
        } catch (e: Exception) {
            SearchResult.Error(e.message ?: "No connection")
        }
    }

    suspend fun lookup(barcode: String): Result = withContext(Dispatchers.IO) {
        val code = barcode.trim()
        try {
            val conn = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val status = conn.responseCode
            if (status == 404) return@withContext Result.NotFound
            if (status !in 200..299) return@withContext Result.Error("Server error ($status)")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val p = json.optJSONObject("product")
            if (json.optInt("status", 0) != 1 || p == null) return@withContext Result.NotFound
            parse(code, p)
        } catch (e: Exception) {
            Result.Error(e.message ?: "No connection")
        }
    }

    private fun JSONObject.str(name: String): String? = optString(name, "").trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.num(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name).takeIf { !it.isNaN() } else null

    private fun parse(code: String, p: JSONObject): Result {
        val food = parseFood(code, p)
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        val hasKcal = n.num("energy-kcal_100g") != null || n.num("energy_100g") != null
        return Result.Found(food, complete = hasKcal && food.name.isNotEmpty())
    }

    /** The product as a food (per 100 g); kcal 0 and an empty name when the product doesn't give them. */
    private fun parseFood(code: String, p: JSONObject): Food {
        val name = p.str("product_name_de") ?: p.str("product_name") ?: p.str("product_name_en") ?: p.str("generic_name") ?: ""
        // A comma-separated string here, a list in some API versions.
        val brand = (p.optJSONArray("brands")?.optString(0) ?: p.str("brands"))?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 }
        val portions = buildList {
            val sq = p.num("serving_quantity")
            if (sq != null && sq > 0) add(Portion(p.str("serving_size") ?: "1 serving", sq))
            val pq = p.num("product_quantity")
            if (pq != null && pq > 0) add(Portion("Whole package", pq))
        }
        return Food(
            name = name,
            brand = brand,
            source = Food.SOURCE_OFF,
            barcode = code,
            kcal = kcal ?: 0.0,
            protein = n.num("proteins_100g") ?: 0.0,
            carbs = n.num("carbohydrates_100g") ?: 0.0,
            fat = n.num("fat_100g") ?: 0.0,
            fiber = n.num("fiber_100g"),
            sugar = n.num("sugars_100g"),
            salt = n.num("salt_100g") ?: n.num("sodium_100g")?.let { it * 2.5 },
            portions = portions.ifEmpty { null },
        )
    }
}
