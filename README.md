# TooHeavy

An offline Android workout and calorie tracker in the style of Hevy. Kotlin + Jetpack Compose, no account, no backend.

## Features

- **Workouts**: empty workouts or routines, sets with weight/reps/type, previous-session values, rest timer, history, personal records and progress charts.
- **Exercise library**: 873 exercises with start/end position photos (shown as a looping animation), step-by-step instructions, muscles and equipment.
- **Calorie tracker**: daily diary by meal, calorie ring and macro bars, 12,650 bundled foods, custom foods, quick add, goals with a TDEE calculator, body-weight log.
- **Barcode scanning**: scans with ZXing; unknown barcodes are looked up on Open Food Facts and saved on the device. Products that aren't found can be entered by hand and are stored with their barcode for the next scan.

All data stays on the device (`filesDir/data.json`, `filesDir/nutrition.json`). The only network call is the Open Food Facts barcode lookup.

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
