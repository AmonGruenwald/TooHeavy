package com.tooheavy.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tooheavy.app.BuildConfig

data class ChangelogEntry(val version: String, val date: String, val changes: List<String>)

/** Release notes shown in the app, newest first. Add an entry before tagging a release. */
object Changelog {
    val entries = listOf(
        ChangelogEntry(
            "1.22.1", "2026-11-09",
            listOf("The barcode scanner focuses where you tap, like the camera app; a ring shows the spot."),
        ),
        ChangelogEntry(
            "1.22.0", "2026-11-09",
            listOf(
                "Quick add asks for calories, fat, carbs and protein in that order.",
                "Quick add per 100 g: switch to \"Per 100 g\", enter the values from the label and how much you ate, " +
                    "and it works out the rest. The entry shows the amount, and editing it later lets you change the grams.",
            ),
        ),
        ChangelogEntry(
            "1.21.0", "2026-11-09",
            listOf(
                "The water goal is optional: tap Goal on the Hydration card to switch it off or change it (in litres). " +
                    "Without a goal the card and the Home widget just show what you drank today.",
            ),
        ),
        ChangelogEntry(
            "1.20.0", "2026-11-03",
            listOf(
                "Save your program as routines: \"Save as routines\" in the program's menu saves every day, or use " +
                    "the menu on a single day. Sets, reps, weights, rests and supersets are copied as they are now; " +
                    "saving again updates the same routines instead of adding new ones.",
                "The program shows clearly which exercises form a superset: a bar runs along them, and the label says " +
                    "how many go back to back. Exercises after it are done on their own.",
            ),
        ),
        ChangelogEntry(
            "1.19.0", "2026-11-03",
            listOf(
                "Programs: supersets in short sessions now pair opposing muscles: biceps with triceps, chest flys " +
                    "with rear delts, leg extensions with leg curls, and an upper-body push with a pull among the " +
                    "secondary lifts. Main lifts keep their full rest. Your program is paired again the new way.",
                "Programs never put the same muscle group twice in a row while there's something else to do, so a " +
                    "second chest press no longer follows the bench press straight away.",
                "A focus muscle's extra exercise is a different movement from the day's others (a fly next to the " +
                    "bench press, not a second press). Create the program again to get this for your days.",
                "History and the exercise library moved into Train; the tabs are now Home, Train, Food and Habits.",
                "A Running widget for Home: distance this week, distance per week, the next planned run and a Start " +
                    "button.",
            ),
        ),
        ChangelogEntry(
            "1.18.0", "2026-11-02",
            listOf(
                "A focused lock-screen view during runs: waking a locked phone shows just the map with your route, " +
                    "distance, time, average and current pace, and a big Pause / Resume button.",
                "The rest of the app isn't reachable from the lock screen any more; unlock (there's a button) to " +
                    "finish the run or use the app, which is still where you left it.",
            ),
        ),
        ChangelogEntry(
            "1.17.0", "2026-11-02",
            listOf(
                "Runs on the lock screen: the recording notification shows distance, moving time, average and " +
                    "current pace on the lock screen, with a Pause / Resume button. It stays while paused.",
                "While a run is recorded, waking the phone shows the run itself without unlocking (you still unlock " +
                    "to leave the app). Turn it off with \"Show on lock screen\" in the recording panel.",
            ),
        ),
        ChangelogEntry(
            "1.16.0", "2026-11-02",
            listOf(
                "Start a run without a plan: Train → Start a run, then pick a run, a walk or hike, or a bike ride. The " +
                    "workout opens and GPS recording starts right away.",
            ),
        ),
        ChangelogEntry(
            "1.15.0", "2026-11-02",
            listOf(
                "Habits: a new tab to track anything you want to do regularly. Every day, on chosen days or a number " +
                    "of days a week; as a simple yes or a count (like 8 glasses of water).",
                "Reminders at the times you pick, only when the habit isn't done yet, with a Done button right in " +
                    "the notification.",
                "Tick off today and the last week from the list. Each habit shows its streak, best streak, completion " +
                    "over 7, 30 and 90 days and a year, a calendar of done days and charts by week and by day.",
                "A \"Habits today\" widget for the Home screen (tap the tune icon to add it).",
            ),
        ),
        ChangelogEntry(
            "1.14.0", "2026-11-01",
            listOf(
                "Record runs with GPS: in a workout, tap \"Record with GPS\" on a run (or a ride or row). A live map, " +
                    "distance, moving time, average and current pace; pause and resume; it keeps recording with the " +
                    "screen off. Finishing fills in the set's distance and time.",
                "Recorded routes are shown on an OpenStreetMap map in the workout, with the time and pace of every " +
                    "km (or mile). Maps need a connection; recording doesn't.",
                "Auto-pause: standing still for a few seconds pauses the recording, and it carries on once you move " +
                    "again. Switch it off in the recording panel.",
                "Before the first recording, the app asks to be left out of battery optimisation so Android doesn't " +
                    "stop it with the screen off; the recording panel warns while it's still on.",
                "If the app is closed while recording, the run comes back paused, ready to resume or finish.",
                "Fartlek is a new kind of run for running plans.",
            ),
        ),
        ChangelogEntry(
            "1.13.0", "2026-11-01",
            listOf(
                "Running plans are built in the app instead of from text: pick the name, the start week and the " +
                    "number of weeks, then add each run with its kind (easy, long, tempo, intervals, recovery or " +
                    "race), the day, a distance or time target and a note.",
                "Add a note to any run or week. Copy a week's runs to the next week to fill the plan quickly.",
            ),
        ),
        ChangelogEntry(
            "1.12.0", "2026-11-01",
            listOf(
                "Running workouts: the new Running exercise logs distance and time, and shows your pace. Treadmill " +
                    "runs and walks, trail runs, bikes, rowers and the elliptical log distance too. Distances are in " +
                    "km, or miles if you lift in pounds.",
                "Running plans: Train → Running plan. Paste or write your own plan (\"Week 1\" starts a week, every " +
                    "other line is a run; distances and times become targets), pick the week it starts, and follow it " +
                    "week by week. Start a run from the plan and it's ticked off when you finish; runs can also be " +
                    "marked done, skipped, edited, added and moved around by week.",
                "Your own exercises can log distance and time.",
            ),
        ),
        ChangelogEntry(
            "1.11.0", "2026-11-01",
            listOf(
                "Mark a day in the food diary as not properly tracked (⋮ next to the timeline): it then doesn't count " +
                    "for the calorie carry-over, the coach's expenditure estimate or the intake averages.",
                "Or mark a day without food as left empty on purpose: it counts as 0 kcal eaten, so its calorie goal " +
                    "carries over to the next days. Logging food on the day clears the mark.",
                "New exercise: General Warmup. It logs time instead of weight and reps, works no particular muscle " +
                    "and always goes first when exercises are ordered. Type the minutes or use the stopwatch on each set.",
                "Your own exercises can be timed too, don't need a main muscle any more, and can have the new type Warmup.",
            ),
        ),
        ChangelogEntry(
            "1.9.0", "2026-11-01",
            listOf(
                "Calorie carry-over: eat over your calorie goal and the next days' goals go down by the difference; " +
                    "eat under it and they go up. Days that go the other way make up the oldest difference first, and " +
                    "whatever isn't made up within a week is dropped. Days without food logged don't count.",
                "The food diary and the Calories today widget show the adjusted goal and how much was carried over. " +
                    "Lowered goals don't go below 1,200 kcal for women or 1,500 kcal for men; the rest waits for the next days.",
                "Turn it off in Goals & Body. Changing your goal, or a coach check-in, doesn't change past days.",
            ),
        ),
        ChangelogEntry(
            "1.8.0", "2026-10-30",
            listOf(
                "29 more exercises, including Bulgarian split squats, dumbbell and Smith machine Romanian deadlifts, " +
                    "machine and Smith hip thrusts, pendulum and belt squats, Nordic curls, Pendlay, seal, Meadows and " +
                    "chest-supported rows, cable and machine lateral raises, the landmine press, Bayesian curls and an " +
                    "assisted pull-up machine. They come without photos.",
                "Search knows common gym names: pec deck, machine chest press, skull crusher, RDL, OHP, pull-up and more.",
                "Programs order each workout the way research suggests: the most demanding lifts first, focus muscles " +
                    "early, muscle groups alternating so nothing starts tired, isolation work after the compounds, and " +
                    "core and calves last. Your current program is reordered too.",
                "\"Order exercises\" in workouts and routines applies the same order. In a workout, exercises you've " +
                    "started stay where they are.",
                "Short programs keep the isolation work for your focus muscles, and home programs now include Nordic curls.",
            ),
        ),
        ChangelogEntry(
            "1.7.3", "2026-10-30",
            listOf(
                "Workouts of 45 minutes or less are built the time-efficient way: isolation exercises are dropped " +
                    "before your main lifts go below 3 sets, and the remaining isolation exercises are paired into " +
                    "supersets (for example curls with lateral raises) that share one rest.",
                "Supersets from the program show on the program page and carry into the workout. To get them, " +
                    "create your program again with a time of 30 or 45 minutes.",
            ),
        ),
        ChangelogEntry(
            "1.7.2", "2026-10-30",
            listOf(
                "Program setup asks how long a workout may take (30 to 90 minutes, or no limit). Sessions are fitted to " +
                    "it, counting sets, rests, warm-ups and moving between exercises: sets come off first, then " +
                    "exercises from the best-covered muscles, never the main lifts. Short sessions rest a little less.",
                "The preview and the program page show about how long each workout takes. To shorten your current " +
                    "program, open it and choose New program from the menu.",
                "\"Import from Hevy\" is now \"Import history\".",
            ),
        ),
        ChangelogEntry(
            "1.7.1", "2026-10-30",
            listOf(
                "\"Leave out\" is now \"Reduce\": reduced muscle groups are kept at maintenance with 3 to 6 sets a " +
                    "week instead of disappearing. They get one exercise, added to another day if their own days go, " +
                    "or none when other lifts already work them.",
                "Focus on as many muscle groups as you like. Sessions get a little more room for each one.",
            ),
        ),
        ChangelogEntry(
            "1.7.0", "2026-10-30",
            listOf(
                "Program setup asks more: pick your split (full body, upper/lower or push/pull/legs), up to two muscle " +
                    "groups to focus on, and groups to leave out. A preview shows the days and weekly sets before you create it.",
                "Programs follow training research: every muscle group gets a weekly set count in the range that suits " +
                    "your experience, sessions stay a sensible length, and focus muscles are trained early with more sets.",
                "Your program adapts over time: logged RIR speeds progress up or holds it back, the deload comes early " +
                    "when many lifts stall, and after each block your volume and the block length adjust to how you did.",
                "Rest timers are set per exercise automatically: longer for heavy compound lifts, shorter for isolation " +
                    "and core. Pick a different time once and it's remembered.",
                "Automatic warm-up sets for the first exercise of each muscle group in a workout, ramping up to your working weight.",
                "Log reps in reserve (RIR) and half reps on every set.",
                "Supersets: link exercises in a workout or routine. The rest timer waits until you've done a set of each.",
                "See which muscles you trained: on every exercise, during and after a workout, and week by week under " +
                    "History → Muscles, with a body map.",
            ),
        ),
        ChangelogEntry(
            "1.6.0", "2026-10-30",
            listOf(
                "Import your workout history from Hevy: export your workouts there as a CSV file, then choose it under " +
                    "Profile (the avatar on Home) → Your data.",
                "Sets, weights, reps, warm-up/drop/failure sets and notes come along. Exercises are matched to the " +
                    "library, you can change any match before importing, and exercises the library doesn't have are " +
                    "added as your own.",
                "Importing the same file again skips the workouts you already have.",
            ),
        ),
        ChangelogEntry(
            "1.5.0", "2026-10-30",
            listOf(
                "The food diary is now one timeline instead of meal sections: everything you eat and drink in the " +
                    "order you had it. Set the time when adding food, and copy yesterday in one tap.",
                "Recipes: combine foods into a dish, set the servings and the cooked weight, and log it like any food.",
                "Hydration: log water with one tap, set a daily water goal, and add the new Hydration widget to Home.",
                "Salt joins fiber and sugar as an optional nutrient, from barcode scans and your own foods.",
                "Scan a product's barcode with the camera while creating a food.",
                "Create your own exercises in the Library, with muscles, equipment and instructions.",
            ),
        ),
        ChangelogEntry(
            "1.4.0", "2026-10-30",
            listOf(
                "A quieter, classier look: warm black, ivory and champagne gold instead of the bright gradients.",
                "Serif headlines and figures, thin borders, and gold-accented buttons, tabs and charts.",
                "Charts now highlight one thing at a time in gold, with everything else in soft gray.",
                "The app icon's barbell is now gold on charcoal.",
            ),
        ),
        ChangelogEntry(
            "1.3.0", "2026-10-30",
            listOf(
                "Updates arrive by themselves: TooHeavy checks once a day and downloads new versions on Wi-Fi.",
                "On Android 12 and newer, updates install in the background while the app is closed and no workout " +
                    "is running. You get a notification when it's done.",
                "On older Android versions, or if Android asks for confirmation, a notification lets you install with one tap.",
                "Version and release notes under Profile (the avatar on Home) → About. This note appears after each update.",
            ),
        ),
        ChangelogEntry(
            "1.2.1", "2026-10-30",
            listOf("Cleaner app icon: a straight barbell in a platinum finish."),
        ),
        ChangelogEntry(
            "1.2.0", "2026-10-30",
            listOf(
                "New look: a dark theme with glass cards and violet-to-ember highlights.",
                "Home tab with a dashboard you can customize: 17 widgets to move, resize, add or remove.",
                "Animated charts you can scrub through on history, workouts, exercises, food, the coach and your profile.",
                "New tab bar (Home, Train, History, Library, Food). Profile is now behind the avatar on Home.",
                "New app icon.",
            ),
        ),
        ChangelogEntry(
            "1.1.0", "2026-10-30",
            listOf(
                "Personal training program: answer four questions and get a plan for your goal, schedule and equipment.",
                "The program sets your weights, reps and sets after every workout from what you actually lifted, " +
                    "and runs in 4-week blocks that end with a deload week.",
                "Adaptive calorie coach: works out how much you really burn from your food log and weight trend, " +
                    "and updates your calorie and macro targets at a weekly check-in.",
                "Weight trend chart and quick weigh-in on the Food tab.",
            ),
        ),
        ChangelogEntry(
            "1.0.1", "2026-10-30",
            listOf(
                "First release: workouts, routines, history, records, the exercise library, and the calorie tracker " +
                    "with barcode scanning.",
                "In-app updates from GitHub releases.",
            ),
        ),
    )

    fun forVersion(version: String): ChangelogEntry? = entries.firstOrNull { it.version == version }
}

/** Shows the release notes once after the app has been updated. */
object WhatsNew {
    private const val PREFS = "app"
    private const val KEY_SEEN = "seen_version"

    var showDialog by mutableStateOf(false)

    fun onAppStart(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(KEY_SEEN, null)
        val current = BuildConfig.VERSION_NAME
        if (seen == current) return
        prefs.edit().putString(KEY_SEEN, current).apply()
        // Versions before 1.3.0 didn't record what was seen, so an update from them is told apart from a
        // fresh install by the install times.
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val updated = seen != null || info.lastUpdateTime > info.firstInstallTime
        if (updated && Changelog.forVersion(current) != null) showDialog = true
    }
}
