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
