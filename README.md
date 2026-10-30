# TooHeavy

An offline Android workout and calorie tracker. Kotlin + Jetpack Compose, no account, no backend.

## Features

- **Home dashboard**: a customizable grid of widgets (up next, week streak, calories and macros today, weekly volume, workouts per week, training calendar heatmap, body-weight trend, weekly calories and macro split, muscle focus, personal records, top-lift 1RM progress, daily burn, recent workouts, lifetime stats, training time). Tap the tune icon to move, resize (full or half width), remove or add widgets.
- **Charts everywhere**: animated, touch-scrubbable charts on history (weekly frequency, training calendar), workout detail (volume by exercise, muscles worked), exercise detail (1RM / heaviest / volume / reps progress, volume per session, rep ranges), food (calorie ring, 7-day intake vs goal, macro split), coach (14-day intake, weight trend, expenditure history) and profile.
- **Workouts**: empty workouts or routines, sets with weight, reps, reps in reserve (RIR), half reps and type, previous-session values, history, personal records and progress charts.
- **Rest timers and warm-ups**: rest times are picked per exercise (about 3 min for heavy barbell compounds, 2 min for other compounds, 90 s for isolation, 60 s for core and calves) and a time you choose for an exercise is remembered. The first exercise for each muscle group in a workout gets ramp-up sets toward the working weight (`data/Training.kt`). Both can be switched off in Profile → Settings.
- **Supersets**: link consecutive exercises in a workout or routine; the rest timer starts after the last exercise of each round.
- **Muscle overview**: muscles per exercise (with a body map on the exercise page), a live overview during a workout, a summary with body map after finishing, and weekly sets per muscle group over time against the 10-20 sets range (History → Muscles). Sets count fully for primary muscles and half for secondary ones.
- **Adaptive training program**: choose goal, experience, days per week, split (full body, upper/lower, push/pull/legs or recommended), equipment, up to two muscle groups to focus on and groups to leave out, with a live preview. The generator (`data/Program.kt`) follows the research on volume (weekly sets per muscle group balanced into a range for your experience, about 10-20 for intermediates), frequency (each muscle about twice a week), effort (3 → 1 reps in reserve across a block), rest and deloads, within a per-session set budget. After each workout, double progression sets the next targets: reps climb through the range, the weight goes up once every set reaches the top, three stalled sessions reset the weight by about 10 %, sets follow performance, and logged RIR doubles or holds the step. Many stalls at once bring the deload forward; after each block, exercises keep earned sets or drop one, and the block gets longer (up to 6 weeks) after steady progress or shorter after an early deload. Exercises can be swapped or edited at any time.
- **Adaptive calorie coach**: estimates your real energy expenditure from logged intake and the smoothed body-weight trend (shrunk toward the Mifflin-St Jeor estimate until there are about three weeks of data), then sets calorie and macro targets for your goal, weekly rate, protein level and diet style at a weekly check-in.
- **Exercise library**: 873 exercises with start/end position photos (shown as a looping animation), step-by-step instructions, muscles and equipment, plus your own custom exercises.
- **Calorie tracker**: a daily timeline of everything eaten (with times), calorie ring and macro bars, optional fiber/sugar/salt, 12,650 bundled foods, custom foods, recipes built from other foods (servings and cooked weight), quick add, goals with a TDEE calculator, body-weight log.
- **Hydration**: one-tap water logging against a daily goal, on the Food tab and as a Home widget.
- **Hevy import**: Profile → Your data reads Hevy's workout CSV export (`data/HevyImport.kt`), matches its exercises to the library (editable before importing), adds missing ones as custom exercises, converts weights to your unit, keeps supersets and turns RPE into RIR, and skips workouts that were already imported.
- **Barcode scanning**: scans with ZXing; unknown barcodes are looked up on Open Food Facts and saved on the device. Products that aren't found can be entered by hand (scanning the barcode into the form) and are stored with their barcode for the next scan.

All data stays on the device (`filesDir/data.json`, `filesDir/nutrition.json`). The only network calls are the Open Food Facts barcode lookup and the update check against this repo's GitHub releases.

## Data sources

| Data | Source | License |
| --- | --- | --- |
| Exercises, images, instructions | [free-exercise-db](https://github.com/yuhonas/free-exercise-db) | Public domain (Unlicense) |
| Bundled foods | [USDA FoodData Central](https://fdc.nal.usda.gov/) — SR Legacy + FNDDS | Public domain (CC0) |
| Barcode lookups | [Open Food Facts](https://world.openfoodfacts.org/) | ODbL |

Bundled assets live in `app/src/main/assets` (`exercises.json` + WebP images, `foods.json`).

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

The tag sets `versionName` (`1.3.0`) and `versionCode` (`10300`). The app looks for a newer release in two ways:

- **On start**: it downloads the APK (on mobile data it asks first) and offers to install it through the system installer. Profile (the avatar on Home) → About has a manual "Check for updates".
- **Once a day in the background** (JobScheduler, survives reboots): on unmetered networks it downloads the update. On Android 12+ it then installs it without a confirmation screen, but only while the app is closed and no workout is in progress, and posts a notification when done. This needs TooHeavy to be allowed to install apps, which Android asks for during the first in-app update. Otherwise, or on older Android, a notification opens the app to install with one tap.

CI signs with the keystore in the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets. It must be the same key that signed the installed app (currently the local debug key), or Android refuses the update.
