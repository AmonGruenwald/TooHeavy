package com.tooheavy.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrainingTest {
    companion object {
        private val library: List<Exercise> by lazy {
            val dir = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.exists() }
            listOf("exercises.json", "exercises_extra.json").flatMap {
                Gson().fromJson<List<Exercise>>(File(dir, it).readText(), object : TypeToken<List<Exercise>>() {}.type)
            }
        }
        private val byId: Map<String, Exercise> by lazy { library.associateBy { it.id } }
    }

    private val lookup: (String) -> Exercise? = { byId[it] }

    private fun we(id: String, vararg weights: Double?, superset: String? = null) = WorkoutExercise(
        exerciseId = id, sets = weights.map { SetEntry(weight = it, reps = 8) }, supersetId = superset,
    )

    // ---------- warm-ups ----------

    @Test
    fun warmupRampsTowardTheWorkingWeight() {
        val sets = WarmUps.sets(100.0, byId["Barbell_Squat"], "kg")
        assertEquals(listOf(50.0, 70.0, 85.0), sets.map { it.weight })
        assertEquals(listOf(8, 5, 2), sets.map { it.reps })
        assertTrue(sets.all { it.type == SetType.WARMUP })
        // Never below the empty bar, and nothing at or above the working weight.
        assertEquals(listOf(20.0, 25.0), WarmUps.sets(30.0, byId["Barbell_Squat"], "kg").map { it.weight })
        // Isolation: one lighter set.
        assertEquals(listOf(10.0), WarmUps.sets(20.0, byId["Dumbbell_Bicep_Curl"], "kg").map { it.weight })
        // Unknown working weight: one light set to fill in.
        val unknown = WarmUps.sets(null, byId["Barbell_Squat"], "kg").single()
        assertNull(unknown.weight)
    }

    @Test
    fun onlyTheFirstExerciseForAMuscleGroupWarmsUp() {
        val w = Workout(
            exercises = listOf(
                we("Barbell_Bench_Press_-_Medium_Grip", 80.0, 80.0),
                we("Incline_Dumbbell_Press", 30.0), // chest already warm
                we("Barbell_Squat", 100.0), // legs not yet
                we("Pushups", null), // bodyweight: no warm-up
            ),
        )
        val out = WarmUps.apply(w, "kg", lookup, { null })
        val warmups = out.exercises.map { e -> e.sets.count { it.type == SetType.WARMUP } }
        assertEquals(listOf(3, 0, 3, 0), warmups)
        // Applying again adds nothing.
        assertEquals(out, WarmUps.apply(out, "kg", lookup, { null }))
        // Without a filled-in weight, the last logged one is used.
        val fromHistory = WarmUps.apply(Workout(exercises = listOf(we("Barbell_Squat", null))), "kg", lookup, { 60.0 })
        assertEquals(listOf(30.0, 42.5, 50.0), fromHistory.exercises[0].sets.filter { it.type == SetType.WARMUP }.map { it.weight })
    }

    // ---------- rest ----------

    @Test
    fun restTimesFollowTheKindOfExercise() {
        assertEquals(180, RestTimes.auto(byId["Barbell_Squat"]))
        assertEquals(120, RestTimes.auto(byId["Dumbbell_Bench_Press"]))
        assertEquals(90, RestTimes.auto(byId["Dumbbell_Bicep_Curl"]))
        assertEquals(60, RestTimes.auto(byId["Standing_Calf_Raises"]))
    }

    @Test
    fun restSkipsSupersetPartnersAndDropSets() {
        val a = we("Dumbbell_Bicep_Curl", 12.0, 12.0, superset = "s").copy(restSeconds = 60)
        val b = we("Triceps_Pushdown", 20.0, superset = "s").copy(restSeconds = 90)
        val c = we("Barbell_Squat", 60.0, 100.0).let { e ->
            e.copy(restSeconds = 180, sets = listOf(e.sets[0].copy(type = SetType.WARMUP), e.sets[1], SetEntry(type = SetType.DROP)))
        }
        val w = Workout(exercises = listOf(a, b, c))
        assertEquals(0, RestTimes.after(w, 0, a.sets[0].id))
        assertEquals(90, RestTimes.after(w, 1, b.sets[0].id))
        assertEquals(60, RestTimes.after(w, 2, c.sets[0].id))
        assertEquals(0, RestTimes.after(w, 2, c.sets[1].id))
        assertEquals(180, RestTimes.after(w, 2, c.sets[2].id))
    }

    // ---------- supersets ----------

    @Test
    fun supersetsLinkSplitAndLetter() {
        val list = listOf(we("a"), we("b"), we("c"), we("d"))
        val id: (WorkoutExercise) -> String? = { it.supersetId }
        val set: (WorkoutExercise, String?) -> WorkoutExercise = { e, s -> e.copy(supersetId = s) }
        var l = list.linkWithNext(0, id, set).linkWithNext(1, id, set)
        assertEquals(listOf("A", "A", "A", null), supersetLetters(l.map(id)))
        assertEquals(0..2, supersetRange(l, 1))
        l = l.unlink(0, id, set)
        assertEquals(listOf(null, "A", "A", null), supersetLetters(l.map(id)))
        l = l.linkWithNext(2, id, set)
        assertEquals(1..3, supersetRange(l, 3))
        // Removing an exercise leaves a lone partner, which loses its superset.
        val lone = listOf(l[0], l[1]).normalizeSupersets(id) { it.copy(supersetId = null) }
        assertTrue(lone.all { it.supersetId == null })
        assertEquals(listOf("A", "A", "B", "B"), supersetLetters(listOf("x", "x", "y", "y")))
    }

    // ---------- exercise order ----------

    @Test
    fun sessionsRunBigLiftsFirstAndAlternateMuscles() {
        val ids = listOf(
            "Triceps_Pushdown", "Cable_Crunch", "Barbell_Bench_Press_-_Medium_Grip", "Standing_Calf_Raises",
            "Side_Lateral_Raise", "Barbell_Squat", "Dumbbell_Bench_Press", "Seated_Cable_Rows",
        )
        val ordered = orderWorkout(ids.map { WorkoutExercise(exerciseId = it) }, lookup).map { it.exerciseId }
        // Heavy barbell lifts first, other compounds next, isolation after, core and calves last.
        assertEquals(listOf("Barbell_Bench_Press_-_Medium_Grip", "Barbell_Squat"), ordered.take(2))
        assertEquals(setOf("Dumbbell_Bench_Press", "Seated_Cable_Rows"), ordered.subList(2, 4).toSet())
        assertEquals(setOf("Cable_Crunch", "Standing_Calf_Raises"), ordered.takeLast(2).toSet())
        // After the squat comes the row, not a second chest press.
        assertEquals("Seated_Cable_Rows", ordered[2])
        assertTrue(ordered.indexOf("Triceps_Pushdown") > ordered.indexOf("Dumbbell_Bench_Press"))

        // Exercises already started keep their place at the top, and superset partners stay together.
        val started = WorkoutExercise(exerciseId = "Side_Lateral_Raise", sets = listOf(SetEntry(completed = true)))
        val workout = listOf(
            started, WorkoutExercise(exerciseId = "Dumbbell_Bicep_Curl", supersetId = "s"),
            WorkoutExercise(exerciseId = "Triceps_Pushdown", supersetId = "s"), WorkoutExercise(exerciseId = "Barbell_Squat"),
        )
        val again = orderWorkout(workout, lookup).map { it.exerciseId }
        assertEquals(listOf("Side_Lateral_Raise", "Barbell_Squat", "Dumbbell_Bicep_Curl", "Triceps_Pushdown"), again)
    }

    @Test
    fun programDaysAlternateAndPutFocusFirst() {
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup)
        for (d in p.days) {
            val roles = d.exercises.map { it.role }
            assertEquals(d.name, roles.sortedBy { listOf(Role.PRIMARY, Role.SECONDARY, Role.ACCESSORY).indexOf(it) }, roles)
            // Core and calves close the session.
            val core = d.exercises.indexOfFirst { it.pattern == Pattern.CORE || it.pattern == Pattern.CALVES }
            if (core >= 0) assertTrue(d.name, d.exercises.drop(core).all { it.pattern == Pattern.CORE || it.pattern == Pattern.CALVES })
        }
        // Ordering an already ordered program changes nothing.
        assertEquals(p.days.map { d -> d.exercises.map { it.id } }, p.ordered().days.map { d -> d.exercises.map { it.id } })
    }

    // ---------- library ----------

    @Test
    fun extraExercisesAreValidAndFindable() {
        val extra = library.filter { it.images.isEmpty() && !it.isCustom }
        assertTrue(extra.size >= 25)
        assertEquals(library.size, library.map { it.id }.toSet().size)
        val bundledMuscles = library.filter { it.images.isNotEmpty() }.flatMap { it.primaryMuscles + it.secondaryMuscles }.toSet()
        assertTrue(extra.flatMap { it.primaryMuscles + it.secondaryMuscles }.all { it in bundledMuscles })
        assertTrue(extra.all { it.instructions.size >= 3 })

        fun find(q: String) = library.filter { ExerciseSearch.matches(it, q) }.map { it.id }
        assertTrue("Butterfly" in find("pec deck"))
        assertTrue("EZ-Bar_Skullcrusher" in find("skull crusher"))
        assertTrue("Leverage_Chest_Press" in find("machine chest press"))
        assertTrue("Pullups" in find("pull up"))
        assertTrue("Dumbbell_Romanian_Deadlift" in find("rdl dumbbell"))
        assertTrue("Bulgarian_Split_Squat" in find("bulgarian"))
        assertTrue("Standing_Military_Press" in find("ohp"))
        assertTrue(find("").size == library.size)
    }

    // ---------- timed exercises ----------

    @Test
    fun generalWarmupIsTimedAndWorksNoMuscle() {
        val warmup = byId["General_Warmup"]!!
        assertTrue(warmup.isTimed)
        assertEquals(CATEGORY_WARMUP, warmup.category)
        assertTrue(warmup.primaryMuscles.isEmpty() && warmup.secondaryMuscles.isEmpty())
        assertTrue("General_Warmup" in library.filter { ExerciseSearch.matches(it, "warm up") }.map { it.id })
        // No rest after it, no ramp-up sets for it, and it doesn't take the squat's ramp-up sets away.
        assertEquals(0, RestTimes.auto(warmup))
        assertFalse(WarmUps.suits(warmup))
        val w = Workout(exercises = listOf(WorkoutExercise(exerciseId = "General_Warmup", sets = listOf(SetEntry(seconds = 300))), we("Barbell_Squat", 100.0)))
        val out = WarmUps.apply(w, "kg", lookup, { null })
        assertEquals(listOf(0, 3), out.exercises.map { e -> e.sets.count { it.type == SetType.WARMUP } })
        // It counts toward no muscle and no volume.
        val done = out.exercises.first().let { it.copy(sets = it.sets.map { s -> s.copy(completed = true) }) }
        assertTrue(muscleCredit(listOf(done), lookup).isEmpty())
        assertEquals(0.0, Workout(exercises = listOf(done)).volume(), 0.0)
    }

    @Test
    fun warmupsAreOrderedFirst() {
        val ids = listOf("Barbell_Squat", "Dumbbell_Bicep_Curl", "General_Warmup")
        val ordered = orderWorkout(ids.map { WorkoutExercise(exerciseId = it) }, lookup).map { it.exerciseId }
        assertEquals("General_Warmup", ordered.first())
        // A custom warm-up without muscles too.
        val custom = Exercise(id = Exercise.newCustomId(), name = "Rowing warm-up", category = CATEGORY_WARMUP, tracking = Tracking.TIME)
        val withCustom: (String) -> Exercise? = { if (it == custom.id) custom else byId[it] }
        val again = orderWorkout(listOf("Barbell_Squat", custom.id).map { WorkoutExercise(exerciseId = it) }, withCustom).map { it.exerciseId }
        assertEquals(custom.id, again.first())
    }

    @Test
    fun setTimesParseAndFormat() {
        assertEquals(300, parseSetTime("5"))
        assertEquals(330, parseSetTime("5:30"))
        assertEquals(3723, parseSetTime("1:02:03"))
        assertNull(parseSetTime(""))
        assertNull(parseSetTime("0"))
        assertNull(parseSetTime("5:"))
        assertNull(parseSetTime("a"))
        assertEquals("45s", formatSetTime(45))
        assertEquals("5:00", formatSetTime(300))
        assertEquals("1:02:03", formatSetTime(3723))
        // A running stopwatch counts on from the time already logged.
        assertEquals(70, SetEntry(seconds = 10, timerStartedAt = 1_000L).secondsAt(61_000L))
        assertEquals(10, SetEntry(seconds = 10).secondsAt(61_000L))
        assertNull(SetEntry().secondsAt(61_000L))
    }

    @Test
    fun timedSetsAndExercisesRoundTrip() {
        val gson = Gson()
        val e = Exercise(id = "custom-x", name = "Bike", category = CATEGORY_WARMUP, tracking = Tracking.TIME)
        assertEquals(e, gson.fromJson(gson.toJson(e), Exercise::class.java))
        val set = SetEntry(seconds = 300, completed = true)
        assertEquals(set, gson.fromJson(gson.toJson(set), SetEntry::class.java))
        // Older data has neither field.
        val old = gson.fromJson("""{"id":"a","name":"Squat"}""", Exercise::class.java)
        assertFalse(old.isTimed)
    }

    // ---------- muscles ----------

    @Test
    fun secondaryMusclesCountHalf() {
        val bench = we("Barbell_Bench_Press_-_Medium_Grip", 80.0, 80.0).let { e -> e.copy(sets = e.sets.map { it.copy(completed = true) }) }
        val credit = muscleCredit(listOf(bench), lookup)
        assertEquals(2.0, credit["chest"]!!, 1e-9)
        assertEquals(1.0, credit["triceps"]!!, 1e-9)
        assertEquals("back", MuscleGroup.of("lats"))
        assertEquals(MuscleGroup.GLUTES, MuscleGroup.of("abductors"))
        assertNull(MuscleGroup.of("forearms"))
    }

    // ---------- program generator ----------

    @Test
    fun weeklyVolumeLandsInTheResearchRange() {
        for (level in Experience.all) for (n in 2..6) {
            val p = generateProgram(TrainingGoal.HYPERTROPHY, level, n, Equipment.GYM, "kg", lookup)
            val range = weeklySetRange(TrainingGoal.HYPERTROPHY, level, false)
            val weekly = p.weeklyGroupSets
            val all = p.days.flatMap { it.exercises }
            for (g in listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS)) {
                val v = weekly[g] ?: 0.0
                val direct = all.filter { patternGroup(it.pattern) == g }
                // Above the range only when every exercise for it is already at the minimum of 2 sets.
                assertTrue("$level/$n $g: $v above $range", v <= range.endInclusive + 0.01 || direct.all { it.sets == 2 })
                // The session budget can run out first: up to 2 sets short on 3 days, 1 on more.
                assertTrue("$level/$n $g: $v below $range", v >= range.start - (if (n <= 3) 2.01 else 1.01) || n == 2)
            }
            assertTrue(p.days.flatMap { it.exercises }.all { it.sets in 2..5 && it.sets == it.baseSets })
        }
    }

    @Test
    fun splitChoiceShapesTheRotation() {
        val ul3 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup, split = Split.UPPER_LOWER)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), ul3.days.map { it.name })
        assertEquals(3, ul3.sessionsPerWeek)
        val ppl3 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup, split = Split.PPL)
        assertEquals(listOf("Push", "Pull", "Legs"), ppl3.days.map { it.name })
        val fb5 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 5, Equipment.GYM, "kg", lookup, split = Split.FULL_BODY)
        assertEquals(5, fb5.days.size)
        assertEquals("Full body · 5 days", fb5.name)
    }

    @Test
    fun reducedGroupsAreKeptAtMaintenance() {
        val legs = listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, reduce = legs)
        // The lower days are gone; the leg work rides along on the upper days, four sessions a week.
        assertEquals(listOf("Upper A", "Upper B"), p.days.map { it.name })
        assertEquals(4, p.sessionsPerWeek)
        val all = p.days.flatMap { it.exercises }
        val weekly = p.weeklyGroupSets
        for (g in legs) {
            // At most one exercise each; glutes ride along with the squat and the hinge.
            assertTrue("$g has ${all.count { patternGroup(it.pattern) == g }} exercises", all.count { patternGroup(it.pattern) == g } <= 1)
            assertTrue("$g: ${weekly[g]}", (weekly[g] ?: 0.0) >= MAINTENANCE.start)
        }
        assertEquals(0, all.count { patternGroup(it.pattern) == MuscleGroup.GLUTES })
        // Reduced work comes after the upper-body exercises of the same role.
        val upper = p.days.first().exercises
        val firstReduced = upper.indexOfFirst { patternGroup(it.pattern) in legs }
        assertTrue(upper.drop(firstReduced).filter { it.role == upper[firstReduced].role }.all { patternGroup(it.pattern) in legs })

        // A reduced group that other lifts already cover gets no exercise of its own.
        val triceps = generateProgram(
            TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, reduce = listOf(MuscleGroup.TRICEPS),
        )
        assertTrue(triceps.days.flatMap { it.exercises }.none { patternGroup(it.pattern) == MuscleGroup.TRICEPS })
        assertTrue(triceps.weeklyGroupSets[MuscleGroup.TRICEPS]!! >= MAINTENANCE.start)
    }

    @Test
    fun sessionsFitTheTimeLimit() {
        for (level in Experience.all) for (n in 2..6) {
            val free = generateProgram(TrainingGoal.HYPERTROPHY, level, n, Equipment.GYM, "kg", lookup)
            var lastWeekly = Double.MAX_VALUE
            for (minutes in listOf(90, 60, 45, 30)) {
                val p = generateProgram(TrainingGoal.HYPERTROPHY, level, n, Equipment.GYM, "kg", lookup, minutes = minutes)
                for (d in p.days) {
                    // Three exercises at their minimum may still run over a very short limit; nothing else may.
                    assertTrue(
                        "$level/$n/$minutes ${d.name}: ${d.estimatedMinutes} min, ${d.exercises.size} exercises",
                        d.estimatedMinutes <= minutes || (d.exercises.size == 3 && d.exercises.all { it.sets == 2 }),
                    )
                }
                val weekly = p.weeklyGroupSets.values.sum()
                // Shorter sessions mean less volume, give or take the sets that holding main lifts at 3 keeps.
                assertTrue("$level/$n/$minutes: $weekly sets", weekly <= lastWeekly + 2.01)
                lastWeekly = weekly
            }
            assertTrue(free.days.maxOf { it.estimatedMinutes } >= generateProgram(
                TrainingGoal.HYPERTROPHY, level, n, Equipment.GYM, "kg", lookup, minutes = 45,
            ).days.maxOf { it.estimatedMinutes })
        }
        // Short sessions rest a little less.
        val short = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup, minutes = 30)
        assertEquals(150, short.days.flatMap { it.exercises }.first { it.role == Role.PRIMARY }.restSeconds)
    }

    @Test
    fun shortSessionsKeepCompoundsAndPairIsolation() {
        for (level in Experience.all) for (n in 2..6) for (minutes in listOf(30, 45)) {
            val p = generateProgram(TrainingGoal.HYPERTROPHY, level, n, Equipment.GYM, "kg", lookup, minutes = minutes)
            for (d in p.days) {
                val label = "$level/$n/$minutes ${d.name}"
                val isolation = d.exercises.filter { it.role == Role.ACCESSORY }
                // Main lifts keep 3 sets while there is isolation work to drop instead (for intermediates; beginners'
                // lower volume ranges can still take a main lift down for volume).
                if (level == Experience.INTERMEDIATE && n in 3..5 && isolation.isNotEmpty() && d.exercises.size > 3) {
                    assertTrue(label, d.exercises.filter { it.role == Role.PRIMARY }.all { it.sets >= 3 })
                }
                // Main lifts stand alone; supersets are pairs for different muscle groups: isolation with isolation,
                // or an upper-body push with a pull among the other compounds.
                assertTrue(label, d.exercises.filter { it.role == Role.PRIMARY }.all { it.supersetId == null })
                d.exercises.groupBy { it.supersetId }.filterKeys { it != null }.values.forEach { pair ->
                    assertEquals(label, 2, pair.size)
                    assertTrue(label, patternGroup(pair[0].pattern) != patternGroup(pair[1].pattern))
                    assertEquals(label, pair[0].role, pair[1].role)
                    if (pair[0].role == Role.SECONDARY) {
                        val patterns = pair.map { it.pattern }.toSet()
                        assertTrue(label, patterns.any { it in pushes } && patterns.any { it in pulls })
                    }
                }
            }
        }
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, minutes = 45)
        val paired = p.days.indexOfFirst { d -> d.exercises.any { it.supersetId != null } }
        assertTrue(paired >= 0)
        val w = p.buildWorkout(paired, "kg", lookup)!!
        assertTrue(supersetLetters(w.exercises.map { it.supersetId }).any { it != null })
        // Focus groups keep their isolation work when time is short.
        val shoulders = generateProgram(
            TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, minutes = 45, focus = listOf(MuscleGroup.SHOULDERS),
        )
        for (d in shoulders.days.filter { it.name.startsWith("Upper") }) {
            assertTrue(d.name, d.exercises.any { it.role == Role.ACCESSORY && patternGroup(it.pattern) == MuscleGroup.SHOULDERS })
        }
        // Longer sessions don't pair anything.
        val long = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, minutes = 60)
        assertTrue(long.days.flatMap { it.exercises }.all { it.supersetId == null })
    }

    private val pushes = setOf(Pattern.HORIZONTAL_PUSH, Pattern.VERTICAL_PUSH)
    private val pulls = setOf(Pattern.HORIZONTAL_PULL, Pattern.VERTICAL_PULL)

    @Test
    fun sameMuscleNeverTwiceInARowWhileThereIsAnotherChoice() {
        fun item(id: String, tier: Int, main: String, priority: Int) = OrderItem(id, tier, setOf(main), priority)
        // Chest is a focus, quads reduced: the dumbbell press still waits until something else came in between.
        val ordered = orderSession(
            listOf(
                item("bench", 1, MuscleGroup.CHEST, 0),
                item("dbBench", 2, MuscleGroup.CHEST, 0),
                item("row", 2, MuscleGroup.BACK, 1),
                item("squat", 2, MuscleGroup.QUADS, 2),
            ),
        )
        assertEquals(listOf("bench", "row", "dbBench", "squat"), ordered)
        // With nothing else left, the same group follows.
        assertEquals(listOf("a", "b"), orderSession(listOf(item("a", 2, MuscleGroup.CHEST, 1), item("b", 2, MuscleGroup.CHEST, 1))))
    }

    @Test
    fun focusAddsADifferentMovementAndSupersetsPairAntagonists() {
        // The program from the question: full body, 3 days, 45 minutes, focus abs, chest and biceps, quads reduced.
        val p = generateProgram(
            TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup, split = Split.FULL_BODY,
            focus = listOf(MuscleGroup.ABS, MuscleGroup.CHEST, MuscleGroup.BICEPS), reduce = listOf(MuscleGroup.QUADS), minutes = 45,
        )
        val a = p.days.first()
        // The chest focus adds a fly next to the bench press, not a second press.
        assertEquals(a.name, 1, a.exercises.count { it.pattern == Pattern.HORIZONTAL_PUSH })
        assertTrue(a.name, a.exercises.any { it.pattern == Pattern.CHEST_FLY })
        for (d in p.days) {
            // No two presses (or pulls) of the same kind back to back.
            d.exercises.zipWithNext().forEach { (x, y) ->
                assertFalse(d.name, x.pattern == y.pattern && x.pattern in pushes + pulls)
            }
        }
        // Biceps and triceps on the same short day go together.
        val ul = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, minutes = 45)
        for (d in ul.days) {
            val curl = d.exercises.firstOrNull { it.pattern == Pattern.BICEPS } ?: continue
            val pushdown = d.exercises.firstOrNull { it.pattern == Pattern.TRICEPS } ?: continue
            assertNotNull(d.name, curl.supersetId)
            assertEquals(d.name, curl.supersetId, pushdown.supersetId)
        }
        // Re-pairing on load gives the same supersets.
        assertEquals(
            ul.days.map { d -> d.exercises.map { it.id to it.supersetId } },
            ul.ordered().days.map { d -> d.exercises.map { it.id to it.supersetId } },
        )
    }

    @Test
    fun programDaysSaveAsRoutines() {
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, minutes = 45)
        val day = p.days.first()
        val r = day.toRoutine(p.name)
        assertEquals(day.name, r.name)
        assertEquals(day.routineId(), r.id)
        assertEquals(day.exercises.map { it.exerciseId }, r.exercises.map { it.exerciseId })
        assertEquals(day.exercises.map { it.sets }, r.exercises.map { it.sets })
        assertEquals(day.exercises.map { it.targetReps }, r.exercises.map { it.reps })
        assertEquals(day.exercises.map { it.weight }, r.exercises.map { it.weight })
        assertEquals(day.exercises.map { it.restSeconds }, r.exercises.map { it.restSeconds })
        // Supersets carry over, so the routine runs them the same way.
        assertEquals(day.exercises.map { it.supersetId }, r.exercises.map { it.supersetId })
        assertTrue(r.notes.contains(p.name))
        // Saving the same day again gives the same routine id, so it replaces rather than duplicates.
        assertEquals(r.id, day.toRoutine(p.name).id)
        assertTrue(p.days.map { it.routineId() }.toSet().size == p.days.size)
    }

    @Test
    fun manyFocusGroups() {
        val focus = listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.GLUTES)
        val plain = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup)
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, focus = focus)
        assertEquals(focus, p.focus)
        for (g in focus) {
            assertTrue("$g", p.weeklyGroupSets[g]!! > plain.weeklyGroupSets[g]!!)
        }
        assertTrue(p.days.all { it.exercises.size <= 8 })
    }

    @Test
    fun programsFrom170KeepTheirLeftOutGroups() {
        val json = """{"name":"Old","skip":["calves"],"days":[]}"""
        assertEquals(listOf("calves"), Gson().fromJson(json, Program::class.java).reduce)
    }

    @Test
    fun focusAddsVolumeAndComesFirst() {
        val plain = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup)
        val focused = generateProgram(
            TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 4, Equipment.GYM, "kg", lookup, focus = listOf(MuscleGroup.SHOULDERS),
        )
        assertTrue(focused.weeklyGroupSets[MuscleGroup.SHOULDERS]!! > plain.weeklyGroupSets[MuscleGroup.SHOULDERS]!! + 2)
        val upper = focused.days.first()
        val firstAccessory = upper.exercises.first { it.role == Role.ACCESSORY }
        assertEquals(MuscleGroup.SHOULDERS, patternGroup(firstAccessory.pattern))
    }

    // ---------- adaptation ----------

    private fun bench(sessions: Int = 2) = ProgramExercise(
        exerciseId = "Barbell_Bench_Press_-_Medium_Grip", pattern = Pattern.HORIZONTAL_PUSH, role = Role.PRIMARY,
        sets = 3, baseSets = 3, repMin = 8, repMax = 12, targetReps = 9, weight = 60.0, sessions = sessions,
    )

    private fun did(pe: ProgramExercise, reps: Int, rir: Int?, weight: Double = 60.0) = WorkoutExercise(
        exerciseId = pe.exerciseId, programExerciseId = pe.id,
        target = SetTarget(pe.sets, pe.targetReps, pe.repMin, pe.repMax, pe.weight, 2),
        sets = List(3) { SetEntry(weight = weight, reps = reps, completed = true, rir = rir) },
    )

    @Test
    fun loggedRirSteersProgression() {
        val pe = bench()
        assertEquals(10, progressExercise(pe, did(pe, 9, null), "kg", "barbell").targetReps)
        assertEquals(11, progressExercise(pe, did(pe, 9, 4), "kg", "barbell").targetReps)
        assertEquals(9, progressExercise(pe, did(pe, 9, 0), "kg", "barbell").targetReps)
        // At the top of the range with reps to spare, the weight jumps twice.
        assertEquals(65.0, progressExercise(pe, did(pe, 12, 4), "kg", "barbell").weight!!, 1e-9)
        assertEquals(62.5, progressExercise(pe, did(pe, 12, 2), "kg", "barbell").weight!!, 1e-9)
    }

    @Test
    fun stallsBringTheDeloadForward() {
        var p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.INTERMEDIATE, 3, Equipment.GYM, "kg", lookup)
        // Two weeks in, most exercises have stalled twice.
        p = p.copy(
            sessionsCompleted = 6,
            days = p.days.map { d -> d.copy(exercises = d.exercises.map { it.copy(sessions = 2, stalls = 2, weight = it.weight ?: 40.0) }) },
        )
        val w = p.buildWorkout(p.nextDayIndex, "kg", lookup)!!.let { w ->
            w.copy(exercises = w.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(weight = it.weight ?: 40.0, reps = 1, completed = true) }) })
        }
        val (next, changes) = p.afterSession(w, "kg", lookup)
        assertEquals(3, next.trainingWeeks)
        assertTrue(changes.any { it.startsWith("Fatigue is building") })
        assertFalse(next.isDeload)
        // After this week's other two sessions the deload starts, and once it's done the next block is shorter.
        var q = next.copy(sessionsCompleted = 9)
        assertTrue(q.isDeload)
        q = q.copy(sessionsCompleted = 11)
        val (block, notes) = q.afterSession(q.buildWorkout(q.nextDayIndex, "kg", lookup)!!, "kg", lookup)
        assertEquals(1, block.mesoIndex)
        assertEquals(3, block.mesoWeeks)
        assertEquals(0, block.weekIndex)
        assertTrue(notes.any { it.contains("a week shorter") })
    }

    @Test
    fun blockReviewKeepsEarnedSets() {
        val p0 = generateProgram(TrainingGoal.HYPERTROPHY, Experience.BEGINNER, 2, Equipment.GYM, "kg", lookup)
        val first = p0.days[0].exercises[0]
        val last = p0.days[0].exercises.last()
        // End of the deload week of block 1: one lift earned a set, another lost one.
        val p = p0.copy(
            sessionsCompleted = p0.sessionsPerWeek * (p0.mesoWeeks + 1) - 1,
            days = p0.days.map { d ->
                d.copy(exercises = d.exercises.map {
                    when (it.id) {
                        first.id -> it.copy(sessions = 8, sets = it.baseSets + 1, stalls = 0)
                        last.id -> it.copy(sessions = 8, sets = it.baseSets - 1, stalls = 1)
                        else -> it.copy(sessions = 8, stalls = 1)
                    }
                })
            },
        )
        val (next, notes) = p.afterSession(p.buildWorkout(p.nextDayIndex, "kg", lookup)!!, "kg", lookup)
        val all = next.days.flatMap { it.exercises }
        assertEquals(first.baseSets + 1, all.first { it.id == first.id }.baseSets)
        assertEquals(maxOf(2, last.baseSets - 1), all.first { it.id == last.id }.baseSets)
        assertTrue(all.all { it.sets == it.baseSets })
        assertEquals(4, next.mesoWeeks)
        assertTrue(notes.any { it.startsWith("Deload done") })
    }

    @Test
    fun oldProgramsKeepTheirPlaceInTheBlock() {
        val p = generateProgram(TrainingGoal.HYPERTROPHY, Experience.BEGINNER, 3, Equipment.GYM, "kg", lookup)
            .copy(perWeek = 0, sessionsCompleted = 17)
        val m = p.migrated()
        assertEquals(1, m.mesoIndex)
        assertEquals(15, m.mesoStart)
        assertEquals(0, m.weekIndex)
    }

    @Test
    fun replacingAnExerciseInAWorkout() {
        val bench = WorkoutExercise(
            id = "b", exerciseId = "Barbell_Bench_Press_-_Medium_Grip", restSeconds = 180, supersetId = "s",
            programExerciseId = "pe", notes = "Elbows in",
            sets = listOf(
                SetEntry(type = SetType.WARMUP, weight = 40.0, reps = 8, completed = true),
                SetEntry(weight = 80.0, reps = 5, completed = true),
                SetEntry(weight = 80.0),
                SetEntry(type = SetType.FAILURE),
            ),
        )
        val row = WorkoutExercise(id = "r", exerciseId = "Bent_Over_Barbell_Row", supersetId = "s")
        val w = Workout(exercises = listOf(bench, row))
        // Sets done stay with the bench press; the dumbbell press follows with the sets that were left.
        val swapped = w.swapExercise("b", "Dumbbell_Bench_Press", 120, newWeId = "d")
        assertEquals(listOf("b", "d", "r"), swapped.exercises.map { it.id })
        assertEquals(2, swapped.exercises[0].sets.size)
        assertTrue(swapped.exercises[0].sets.all { it.completed })
        val d = swapped.exercises[1]
        assertEquals("Dumbbell_Bench_Press", d.exerciseId)
        assertEquals(listOf(SetType.NORMAL, SetType.FAILURE), d.sets.map { it.type })
        assertTrue(d.sets.none { it.completed || it.weight != null })
        assertEquals(120, d.restSeconds)
        assertEquals("s", d.supersetId)
        assertEquals(null, d.programExerciseId)
        assertEquals("", d.notes)
        // Nothing done yet: replaced where it is, with as many working sets.
        val fresh = Workout(exercises = listOf(row, bench.copy(sets = bench.sets.map { it.copy(completed = false) })))
        val swappedFresh = fresh.swapExercise("b", "Push-Up", 60, newWeId = "p")
        assertEquals(listOf("r", "p"), swappedFresh.exercises.map { it.id })
        assertEquals(3, swappedFresh.exercises[1].sets.size)
        // Every set done: the new one gets one set; an unknown id changes nothing.
        val allDone = Workout(exercises = listOf(row.copy(sets = listOf(SetEntry(completed = true)))))
        assertEquals(1, allDone.swapExercise("r", "Pull-Up", 90, "u").exercises[1].sets.size)
        assertEquals(w, w.swapExercise("missing", "Push-Up", 60))
    }

    @Test
    fun similarExercisesShareTheMainMuscle() {
        val bench = Exercise(id = "bench", name = "Bench Press", equipment = "barbell", mechanic = "compound", primaryMuscles = listOf("chest"), category = "strength")
        val db = Exercise(id = "db", name = "Dumbbell Press", equipment = "dumbbell", mechanic = "compound", primaryMuscles = listOf("chest"), category = "strength")
        val incline = Exercise(id = "incline", name = "Incline Bench Press", equipment = "barbell", mechanic = "compound", primaryMuscles = listOf("chest"), category = "strength")
        val fly = Exercise(id = "fly", name = "Cable Fly", equipment = "cable", mechanic = "isolation", primaryMuscles = listOf("chest"), category = "strength")
        val stretch = Exercise(id = "stretch", name = "Chest Stretch", primaryMuscles = listOf("chest"), category = "stretching")
        val squat = Exercise(id = "squat", name = "Squat", equipment = "barbell", primaryMuscles = listOf("quadriceps"), category = "strength")
        val library = listOf(bench, db, incline, fly, stretch, squat)
        // Done before first, then the same equipment and mechanic; no stretches, no other muscles, not itself.
        assertEquals(listOf("fly", "incline", "db"), similarExercises(bench, library, done = setOf("fly")).map { it.id })
        assertEquals(listOf("incline"), similarExercises(bench, library, emptySet(), limit = 1).map { it.id })
        // Without a muscle: the same kind and tracking.
        val run = Exercise(id = "run", name = "Running", category = "cardio", tracking = Tracking.DISTANCE)
        val row = Exercise(id = "row", name = "Rowing", category = "cardio", tracking = Tracking.DISTANCE)
        val warmup = Exercise(id = "warm", name = "General Warmup", category = "cardio", tracking = Tracking.TIME)
        assertEquals(listOf("row"), similarExercises(run, listOf(run, row, warmup, squat), emptySet()).map { it.id })
    }
}
