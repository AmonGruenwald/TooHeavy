package com.tooheavy.app.data

/**
 * Exercise search that understands the names people use at the gym. The library names many exercises its own
 * way ("Butterfly" for the pec deck, "Leverage Chest Press" for the chest press machine), so each name is
 * extended with common synonyms, and every word of the query only has to appear somewhere in the result.
 */
object ExerciseSearch {
    /** Library name fragment (lowercase) to extra words it should be found by. */
    private val synonyms: List<Pair<String, String>> = listOf(
        "butterfly" to "pec deck machine fly chest fly",
        "reverse machine flyes" to "reverse pec deck rear delt machine fly",
        "leverage" to "machine",
        "machine bench press" to "chest press",
        "military press" to "overhead press ohp shoulder press",
        "pullups" to "pull up pull-up",
        "chin-up" to "chin up",
        "skullcrusher" to "skull crusher lying triceps extension",
        "romanian deadlift" to "rdl",
        "stiff-legged" to "romanian deadlift rdl",
        "side bridge" to "side plank",
        "ab roller" to "ab wheel rollout",
        "ab crunch machine" to "machine crunch",
        "leverage iso row" to "machine row",
        "leverage high row" to "machine high row",
        "bicycling" to "bike cycling exercise bike",
        "running, treadmill" to "treadmill run",
        "walking, treadmill" to "treadmill incline walk",
        "rowing, stationary" to "rowing machine erg rower",
        "thigh abductor" to "hip abduction machine",
        "thigh adductor" to "hip adduction machine",
        "kettlebell swings" to "kettlebell swing",
        "dumbbell incline row" to "chest supported row",
        "lying t-bar row" to "chest supported t-bar row",
        "rear lunge" to "reverse lunge",
        "cable crossover" to "cable fly chest fly",
        "flat bench cable flyes" to "cable fly chest fly",
        "dip machine" to "seated dip machine assisted dip",
        "hyperextensions" to "back extension 45",
        "rope attachment" to "rope",
        "front dumbbell raise" to "front raise",
        "dumbbell bicep curl" to "dumbbell curl biceps",
        "barbell curl" to "biceps",
        "leg curls" to "hamstring curl",
        "leg curl" to "hamstring curl",
        "pushups" to "push up push-up press up",
        "barbell squat" to "back squat",
        "barbell full squat" to "back squat",
        "bench press - medium grip" to "bench press flat",
        "triceps pushdown" to "tricep pushdown",
        "tricep" to "triceps",
        "glute ham raise" to "ghr",
        "smith machine" to "smith",
        "one arm lat pulldown" to "single arm lat pulldown",
        "hack squat" to "hack machine",
        "seated cable rows" to "seated cable row",
        "close-grip front lat pulldown" to "close grip lat pulldown",
        "v-bar pulldown" to "neutral grip lat pulldown",
        "shrug" to "traps",
        "calf press" to "leg press calf raise",
    )

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private val cache = HashMap<String, String>()

    /** The text an exercise is found by: its name, synonyms for it, and its equipment. */
    private fun text(e: Exercise): String = cache.getOrPut(e.id + "|" + e.name) {
        val name = e.name.lowercase()
        val extra = synonyms.filter { (fragment, _) -> fragment in name }.joinToString(" ") { it.second }
        normalize("$name $extra ${e.equipment.orEmpty()}")
    }

    /** Whether every word of [query] appears in the exercise's name, synonyms or equipment. */
    fun matches(e: Exercise, query: String): Boolean {
        val words = normalize(query).split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val t = text(e)
        return words.all { it in t }
    }
}
