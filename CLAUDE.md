# TooHeavy

## Releasing

- **Release without asking.** When a change is done, merge it to `main` and release it straight away; the owner
  doesn't want to be asked first.
- Release by merging the pull request with a merge commit message containing a line `Release: X.Y.Z` (see
  `.github/workflows/release.yml`). The workflow tags `vX.Y.Z`, builds the APK and publishes the GitHub release,
  which the app updates itself from.
- Bump the minor version for features and the patch version for fixes, and add a matching entry at the top of
  `Changelog.entries` (`app/src/main/java/com/tooheavy/app/data/Changelog.kt`) and to `README.md` where a feature
  is described.
- After merging, check that the Release run passed and the release has its APK. If the build failed, fix it and
  release again.

## Building

- The release workflow builds `assembleRelease` only; unit tests (`app/src/test`) don't run in CI.
- Check new top-level names for clashes within a package (e.g. `com.tooheavy.app.data`) before pushing: a
  redeclaration breaks the release build.
