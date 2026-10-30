# TooHeavy

An offline Android workout and calorie tracker. Kotlin + Jetpack Compose, no account, no backend.

## Features

- **Workouts**: empty workouts or routines, sets with weight/reps/type, previous-session values, rest timer, history, personal records and progress charts.
- **Exercise library**: 873 exercises with start/end position photos (shown as a looping animation), step-by-step instructions, muscles and equipment.
- **Calorie tracker**: daily diary by meal, calorie ring and macro bars, 12,650 bundled foods, custom foods, quick add, goals with a TDEE calculator, body-weight log.
- **Barcode scanning**: scans with ZXing; unknown barcodes are looked up on Open Food Facts and saved on the device. Products that aren't found can be entered by hand and are stored with their barcode for the next scan.

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

## Releases and updates

Pushing a version tag builds a signed APK and publishes it as a GitHub release (`.github/workflows/release.yml`):

```bash
git tag v1.1.0 && git push origin v1.1.0
```

The tag sets `versionName` (`1.1.0`) and `versionCode` (`10100`). On start, the app checks the latest release. If it's newer, the app downloads the APK in the background (on mobile data it asks first) and offers to install it. Profile → About has a manual "Check for updates".

CI signs with the keystore in the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets. It must be the same key that signed the installed app (currently the local debug key), or Android refuses the update.
