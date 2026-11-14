package com.tooheavy.app.data

import java.text.Normalizer
import java.util.Locale

/**
 * Matching food names to what's typed in the food search, forgiving about case, accents and punctuation
 * ("Crème fraîche" matches "creme fraiche", "Müsli" matches "musli"), and about plurals ("eggs" finds
 * "Egg, whole", "tomatoes" finds "Tomato", "berries" finds "Berry").
 */
object FoodMatch {
    private val marks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}%]+")

    /** Lower case, without accents, with punctuation as single spaces: "Crème-Fraîche, 30%" -> "creme fraiche 30%". */
    fun normalize(s: String): String {
        val lower = s.lowercase(Locale.ROOT).replace("ß", "ss")
        val plain = marks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        return separators.replace(plain, " ").trim()
    }

    /** The words of [query], each with its singular forms: "Eggs, boiled" -> [{eggs, egg}, {boiled}]. */
    fun words(query: String): List<Set<String>> =
        normalize(query).split(' ').filter { it.isNotEmpty() }.map { w ->
            buildSet {
                add(w)
                if (w.length > 3) {
                    if (w.endsWith("ies")) add(w.dropLast(3) + "y")
                    if (w.endsWith("es")) add(w.dropLast(2))
                    if (w.endsWith("s") && !w.endsWith("ss")) add(w.dropLast(1))
                }
            }
        }

    /** Whether the [normalized] name contains every word (in one of its forms). */
    fun matches(normalized: String, words: List<Set<String>>): Boolean =
        words.isNotEmpty() && words.all { forms -> forms.any { normalized.contains(it) } }

    /** 0 when the name starts with the first word, 1 when a later word of the name does, else 2 (lower ranks first). */
    fun rank(normalized: String, words: List<Set<String>>): Int {
        val first = words.firstOrNull() ?: return 2
        return when {
            first.any { normalized.startsWith(it) } -> 0
            first.any { normalized.contains(" $it") } -> 1
            else -> 2
        }
    }
}
