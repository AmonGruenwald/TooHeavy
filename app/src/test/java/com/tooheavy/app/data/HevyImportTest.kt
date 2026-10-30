package com.tooheavy.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HevyImportTest {
    private val library: List<Exercise> by lazy {
        Gson().fromJson(File("src/main/assets/exercises.json").readText(), object : TypeToken<List<Exercise>>() {}.type)
    }

    private val csv = """
        "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
        "Push Day","21 Oct 2024, 18:30","21 Oct 2024, 19:35","Felt strong, ""new"" PR","Bench Press (Barbell)",,"Pause, then press",0,"warmup",60,10,,,
        "Push Day","21 Oct 2024, 18:30","21 Oct 2024, 19:35","Felt strong, ""new"" PR","Bench Press (Barbell)",,"Pause, then press",1,"normal",100,5,,,8
        "Push Day","21 Oct 2024, 18:30","21 Oct 2024, 19:35","Felt strong, ""new"" PR","Triceps Rope Pushdown",,,0,"dropset",25.5,12,,,
        "Push Day","21 Oct 2024, 18:30","21 Oct 2024, 19:35","Felt strong, ""new"" PR","Treadmill",,,0,"normal",,,2.1,900,
        "Pull Day","23 Oct 2024, 07:05","23 Oct 2024, 08:00","","Pull Up",,,0,"normal",,8,,,
        "Pull Day","23 Oct 2024, 07:05","23 Oct 2024, 08:00","","Zercher Carry (Barbell)",,,0,"failure",80,1,,,
        "Pull Day","23 Oct 2024, 07:05","23 Oct 2024, 08:00","","Pull Up",,,1,"normal",,6,,,
    """.trimIndent()

    @Test
    fun parsesWorkoutsExercisesAndSets() {
        val parsed = HevyImport.parse(csv)
        assertEquals(2, parsed.workouts.size)
        assertEquals(1, parsed.skippedSets)
        val push = parsed.workouts[0]
        assertEquals("Push Day", push.title)
        assertEquals("Felt strong, \"new\" PR", push.notes)
        assertEquals(65 * 60_000L, push.end!! - push.start)
        assertEquals(listOf("Bench Press (Barbell)", "Triceps Rope Pushdown"), push.exercises.map { it.title })
        assertEquals("Pause, then press", push.exercises[0].notes)
        assertEquals(
            listOf(HevyImport.ImportSet(SetType.WARMUP, 60.0, 10), HevyImport.ImportSet(SetType.NORMAL, 100.0, 5)),
            push.exercises[0].sets,
        )
        assertEquals(SetType.DROP, push.exercises[1].sets[0].type)
        // The same exercise again later in a workout is a separate entry.
        assertEquals(listOf("Pull Up", "Zercher Carry (Barbell)", "Pull Up"), parsed.workouts[1].exercises.map { it.title })
        assertNull(parsed.workouts[1].exercises[0].sets[0].weightKg)
    }

    @Test
    fun rejectsOtherFiles() {
        val e = runCatching { HevyImport.parse("date,weight\n2024-01-01,80") }.exceptionOrNull()
        assertTrue(e is HevyImport.FormatException)
    }

    @Test
    fun poundsColumnIsConvertedToKilograms() {
        val parsed = HevyImport.parse(
            "title,start_time,end_time,exercise_title,set_type,weight_lbs,reps\nLegs,2024-01-05 10:00:00,,Squat (Barbell),normal,225,5",
        )
        assertEquals(102.06, parsed.workouts[0].exercises[0].sets[0].weightKg!!, 0.01)
    }

    @Test
    fun everyAliasPointsAtALibraryExercise() {
        val titles = listOf(
            "Bench Press (Barbell)", "Squat (Barbell)", "Deadlift (Barbell)", "Overhead Press (Barbell)", "Pull Up",
            "Lat Pulldown (Cable)", "Seated Cable Row - V Grip (Cable)", "Bicep Curl (Dumbbell)", "Triceps Rope Pushdown",
            "Lateral Raise (Dumbbell)", "Leg Press (Machine)", "Romanian Deadlift (Barbell)", "Hip Thrust (Barbell)",
            "Leg Extension (Machine)", "Lying Leg Curl (Machine)", "Plank", "Running",
        )
        titles.forEach { assertNotNull(it, HevyImport.match(it, library)) }
        // All aliases, via reflection, must resolve to real ids.
        val field = HevyImport::class.java.getDeclaredField("aliases").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val aliases = field.get(HevyImport) as Map<String, String>
        val ids = library.map { it.id }.toSet()
        assertEquals(emptyList<String>(), aliases.values.filter { it !in ids })
    }

    @Test
    fun matchesByNameAndEquipment() {
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", HevyImport.match("Bench Press (Barbell)", library)?.id)
        assertEquals("Dumbbell_Shrug", HevyImport.match("Shrug (Dumbbell)", library)?.id)
        assertEquals("Face_Pull", HevyImport.match("Face Pull", library)?.id)
        assertNull(HevyImport.match("Zercher Carry (Barbell)", library))
        // A custom exercise from an earlier import is reused by its exact name.
        val custom = HevyImport.newExercise("Zercher Carry (Barbell)")
        assertEquals(custom.id, HevyImport.match("Zercher Carry (Barbell)", library + custom)?.id)
    }

    @Test
    fun newExercisesGetAGuessedMuscleAndType() {
        val carry = HevyImport.newExercise("Zercher Carry (Barbell)")
        assertTrue(carry.isCustom)
        assertEquals("barbell", carry.equipment)
        assertEquals("abdominals", HevyImport.newExercise("Hanging Knee Raise Crunch").primaryMuscles.single())
        assertEquals("triceps", HevyImport.newExercise("Triceps Kickback (Cable)").primaryMuscles.single())
        assertEquals("glutes", HevyImport.newExercise("Glute Kickback (Machine)").primaryMuscles.single())
        assertEquals("strength", HevyImport.newExercise("Cable Crunch Twist").category)
        assertEquals("cardio", HevyImport.newExercise("Rowing Machine").category)
    }

    @Test
    fun convertsToWorkoutsAndSkipsDuplicates() {
        val parsed = HevyImport.parse(csv)
        val ids = parsed.exerciseTitles.associate { (t, _) -> t to (HevyImport.match(t, library)?.id ?: "custom-x") }
        val workouts = HevyImport.toWorkouts(parsed, ids, "lb", emptyList())
        assertEquals(2, workouts.size)
        val bench = workouts[0].exercises[0]
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", bench.exerciseId)
        assertEquals(220.46, bench.sets[1].weight!!, 0.01)
        assertTrue(bench.sets.all { it.completed })
        assertEquals(SetType.WARMUP, bench.sets[0].type)

        val again = HevyImport.toWorkouts(parsed, ids, "lb", workouts)
        assertTrue(again.isEmpty())
        assertEquals(2, HevyImport.alreadyImported(parsed, workouts))
    }
}
