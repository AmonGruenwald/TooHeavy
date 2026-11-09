# TooHeavy

An offline Android workout and calorie tracker. Kotlin + Jetpack Compose, no account, no backend.

## Features

- **Home dashboard**: a customizable grid of widgets (up next, week streak, calories and macros today, weekly volume, workouts per week, training calendar heatmap, body-weight trend, weekly calories and macro split, muscle focus, personal records, top-lift 1RM progress, daily burn, recent workouts, lifetime stats, training time). Tap the tune icon to move, resize (full or half width), remove or add widgets.
- **Charts everywhere**: animated, touch-scrubbable charts on history (weekly frequency, training calendar), workout detail (volume by exercise, muscles worked), exercise detail (1RM / heaviest / volume / reps progress, volume per session, rep ranges), food (calorie ring, 7-day intake vs goal, macro split), coach (14-day intake, weight trend, expenditure history) and profile.
- **Workouts**: empty workouts or routines, sets with weight, reps, reps in reserve (RIR), half reps and type, previous-session values, history, personal records and progress charts.
- **Running**: runs, rides and rows log distance and time per set with the pace worked out (`data/Running.kt`), in km or miles to match the weight unit. A running plan (Train → Running plan) is built in the app: a name, start week and number of weeks, then runs with their kind (easy, long, tempo, intervals, fartlek, recovery, race), day, distance or time target and a note, plus notes per week and copying a week's runs to the next. Runs can be recorded with GPS (`data/RunTracker.kt`, `RunTrackingService`): a foreground service with the platform location API (no Google Play services) records the route with moving time, pause/resume, auto-pause (`autoPauseStep`: standing still for 6 s pauses, moving again resumes) and live pace, and keeps going with the screen off (it asks once to be left out of battery optimisation). The recording notification shows distance, time and pace on the lock screen with Pause / Resume, and while recording a locked phone shows a focused view over the lock screen with the route map, distance, time, paces and Pause / Resume, nothing else of the app (a setting; `ui/running/LockRunScreen.kt`); finishing fills the set ("Start a run" on the Train tab opens a run, walk or ride workout and starts recording straight away), and the route (stored in `filesDir/routes`) is shown on an OpenStreetMap map (osmdroid) with km or mile splits. The plan is followed week by week; starting a planned run opens a workout, and finishing it ticks the run off.
- **Timed exercises**: exercises like General Warmup log a duration per set instead of weight and reps (`Exercise.tracking`), typed as minutes or measured with a per-set stopwatch. Warm-ups always come first when exercises are ordered, get no rest timer and count toward no muscle or volume.
- **Rest timers and warm-ups**: rest times are picked per exercise (about 3 min for heavy barbell compounds, 2 min for other compounds, 90 s for isolation, 60 s for core and calves) and a time you choose for an exercise is remembered. The first exercise for each muscle group in a workout gets ramp-up sets toward the working weight (`data/Training.kt`). Both can be switched off in Profile → Settings.
- **Supersets**: link consecutive exercises in a workout or routine; the rest timer starts after the last exercise of each round.
- **Exercise order**: programs order every session by the research on exercise order (`orderSession` in `data/Training.kt`): most demanding lifts first, never the same main muscle group twice in a row while there is another choice, focus muscles early, isolation after compounds, core and calves last. "Order exercises" applies the same to workouts and routines.
- **Muscle overview**: muscles per exercise (with a body map on the exercise page), a live overview during a workout, a summary with body map after finishing, and weekly sets per muscle group over time against the 10-20 sets range (History → Muscles). Sets count fully for primary muscles and half for secondary ones.
- **Adaptive training program**: choose goal, experience, days per week, time per workout, split (full body, upper/lower, push/pull/legs or recommended), equipment, muscle groups to focus on and muscle groups to reduce, with a live preview. Reduced groups are kept at maintenance (3-6 weekly sets, about a third of the usual volume): one exercise in the rotation, coupled onto another day when their own days disappear, or none when compound lifts for other groups already cover them. The generator (`data/Program.kt`) follows the research on volume (weekly sets per muscle group balanced into a range for your experience, about 10-20 for intermediates), frequency (each muscle about twice a week), effort (3 → 1 reps in reserve across a block), rest and deloads, within a per-session set budget. With a time limit, each session is estimated from its sets, rests, warm-ups and changeovers and trimmed to fit: sets first, then exercises from the best-covered groups (never the main lifts), and sessions of 45 minutes or less run the time-efficient way: slightly shorter rests, isolation exercises dropped before main lifts go below 3 sets, and supersets of antagonists (an upper-body push with a pull among the secondary compounds; biceps with triceps, chest flys with rear delts, leg extensions with leg curls among the isolation work; leftover isolation work with a muscle it doesn't share, never the main lifts). A focus group's extra exercise is a different movement from the day's others for that group (a fly next to the bench press). After each workout, double progression sets the next targets: reps climb through the range, the weight goes up once every set reaches the top, three stalled sessions reset the weight by about 10 %, sets follow performance, and logged RIR doubles or holds the step. Many stalls at once bring the deload forward; after each block, exercises keep earned sets or drop one, and the block gets longer (up to 6 weeks) after steady progress or shorter after an early deload. Exercises can be swapped or edited at any time. Days can be saved as routines (all from the program menu, or one at a time), copying sets, target reps, weights, rests and supersets as they are; saving again updates the same routines.
- **Adaptive calorie coach**: estimates your real energy expenditure from logged intake and the smoothed body-weight trend (shrunk toward the Mifflin-St Jeor estimate until there are about three weeks of data), then sets calorie and macro targets for your goal, weekly rate, protein level and diet style at a weekly check-in.
- **Exercise library**: 904 exercises: 873 with start/end position photos (shown as a looping animation), plus 29 common gym exercises the photo set lacks, a General Warmup and Running (`assets/exercises_extra.json`), with step-by-step instructions, muscles and equipment, plus your own custom exercises. Custom exercises can go without a main muscle and have the type Warmup. Search understands common gym names and synonyms (`data/ExerciseSearch.kt`).
- **Calorie tracker**: a daily timeline of everything eaten (with times), calorie ring and macro bars, optional fiber/sugar/salt, 12,650 bundled foods, custom foods, recipes built from other foods (servings and cooked weight), quick add (calories, fat, carbs and protein as eaten, or per 100 g with the amount eaten), goals with a TDEE calculator, body-weight log.
- **Calorie carry-over**: eating over the calorie goal lowers the next days' goals by the difference, eating under it raises them. Differences the other way make up the oldest one first, and whatever is left of a difference is dropped after a week (`data/CarryOver.kt`). Lowered goals stop at the coach's minimum (1,200 / 1,500 kcal). Can be turned off in Goals & Body.
- **Day marks**: a day in the food diary can be marked as not properly tracked (it then counts for nothing: no carry-over, no coach estimate, no intake averages) or, without food logged, as left empty on purpose (it counts as 0 kcal eaten, so its goal carries over). See `countedKcal` in `data/Nutrition.kt`.
- **Habits**: a tab for general habit tracking (`data/Habits.kt`): habits done every day, on chosen weekdays or N days a week, as yes/no or a daily count, with colours. Today and the last 7 days can be ticked off from the list; each habit has its current and best streak (in days, or weeks for weekly habits), completion over 7/30/90/365 days, a calendar heatmap, weekly completion and (for counts) daily charts. Reminders (`data/HabitReminders.kt`) use one inexact alarm for the next reminder, notify only while the habit is still open, have a Done button, and are set again after reboots, updates and clock changes. Stored in `filesDir/habits.json`; a "Habits today" Home widget.
- **Hydration**: one-tap water logging, on the Food tab and as a Home widget, against an optional daily goal (set or switched off from the Hydration card, or left empty in Goals & Body).
- **Import history**: Profile → Your data reads Hevy's workout CSV export (`data/HevyImport.kt`), matches its exercises to the library (editable before importing), adds missing ones as custom exercises, converts weights to your unit, keeps supersets and turns RPE into RIR, and skips workouts that were already imported.
- **Barcode scanning**: scans with ZXing; unknown barcodes are looked up on Open Food Facts and saved on the device. Products that aren't found can be entered by hand (scanning the barcode into the form) and are stored with their barcode for the next scan.

All data stays on the device (`filesDir/data.json`, `filesDir/nutrition.json`, `filesDir/habits.json`). The only network calls are the Open Food Facts barcode lookup, OpenStreetMap map tiles for recorded runs, and the update check against this repo's GitHub releases. GPS recording only uses location while a run is being recorded.

## Data sources

| Data | Source | License |
| --- | --- | --- |
| Exercises, images, instructions | [free-exercise-db](https://github.com/yuhonas/free-exercise-db) | Public domain (Unlicense) |
| Additional exercises (`exercises_extra.json`) | Written for this app | — |
| Bundled foods | [USDA FoodData Central](https://fdc.nal.usda.gov/) — SR Legacy + FNDDS | Public domain (CC0) |
| Barcode lookups | [Open Food Facts](https://world.openfoodfacts.org/) | ODbL |
| Map tiles for recorded runs | [OpenStreetMap](https://www.openstreetmap.org/copyright) via [osmdroid](https://github.com/osmdroid/osmdroid) | ODbL data, CC BY-SA tiles; osmdroid Apache 2.0 |

Bundled assets live in `app/src/main/assets` (`exercises.json` + WebP images, `exercises_extra.json`, `foods.json`).

## Build

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35). Point `local.properties` at the SDK:

```
sdk.dir=C\:/path/to/android/sdk
```

Then:

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk` (signed with the debug key, so it can be sideloaded directly).

Unit tests for the coach and program engines (`app/src/test`) run on the JVM:

```bash
./gradlew testDebugUnitTest
```

## Releases and updates

Before tagging, add the version's notes at the top of `Changelog.entries` in `app/src/main/java/com/tooheavy/app/data/Changelog.kt`. The app shows them once after updating, and any time under Profile (the avatar on Home) → About → What's new.

Pushing a version tag builds a signed APK and publishes it as a GitHub release (`.github/workflows/release.yml`):

```bash
git tag v1.3.0 && git push origin v1.3.0
```

Or publish a release on GitHub (Releases → Draft a new release) with a new `vX.Y.Z` tag on `main`: creating the tag starts the same workflow, which attaches the APK to your release a few minutes later. Until it's attached, installed apps ignore the release.

Without tag rights (for example from a Claude Code cloud session, which can only push its own branch), put a line `Release: 1.9.0` in the message of a commit pushed to `main`, such as the merge commit of a pull request. The workflow tags that commit `v1.9.0`, builds the APK and publishes the release; pushes to `main` without that line don't release. Actions → Release → Run workflow does the same by hand.

The tag sets `versionName` (`1.3.0`) and `versionCode` (`10300`). The app looks for a newer release in two ways:

- **On start**: it downloads the APK (on mobile data it asks first) and offers to install it through the system installer. Profile (the avatar on Home) → About has a manual "Check for updates".
- **Once a day in the background** (JobScheduler, survives reboots): on unmetered networks it downloads the update. On Android 12+ it then installs it without a confirmation screen, but only while the app is closed and no workout is in progress, and posts a notification when done. This needs TooHeavy to be allowed to install apps, which Android asks for during the first in-app update. Otherwise, or on older Android, a notification opens the app to install with one tap.

CI signs with the keystore in the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets. It must be the same key that signed the installed app (currently the local debug key), or Android refuses the update.
