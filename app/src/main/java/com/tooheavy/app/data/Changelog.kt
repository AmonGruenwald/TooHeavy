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
