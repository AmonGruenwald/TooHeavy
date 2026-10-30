package com.tooheavy.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Barcode lookup against the Open Food Facts API (open database, ODbL). */
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
        "product_name,product_name_de,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity,product_quantity"

    suspend fun lookup(barcode: String): Result = withContext(Dispatchers.IO) {
        val code = barcode.trim()
        try {
            val conn = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "TooHeavy/1.0 (Android; personal use)")
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
        val name = p.str("product_name_de") ?: p.str("product_name") ?: p.str("product_name_en") ?: p.str("generic_name") ?: ""
        val brand = p.str("brands")?.split(",")?.firstOrNull()?.trim()
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 }
        val portions = buildList {
            val sq = p.num("serving_quantity")
            if (sq != null && sq > 0) add(Portion(p.str("serving_size") ?: "1 serving", sq))
            val pq = p.num("product_quantity")
            if (pq != null && pq > 0) add(Portion("Whole package", pq))
        }
        val food = Food(
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
        return Result.Found(food, complete = kcal != null && name.isNotEmpty())
    }
}
