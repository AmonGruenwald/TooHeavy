#!/bin/bash
# Builds the APK. Usage: ./build.sh [assembleRelease|assembleDebug]
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export GRADLE_USER_HOME="C:/Dev/android-tools/gradle-home"
export ANDROID_USER_HOME="C:/Dev/android-tools/android-home"
cd "$(dirname "$0")"
/c/Dev/android-tools/gradle-8.10.2/bin/gradle --console=plain -q ${1:-assembleRelease} 2>&1 | grep -v "^w: " | head -150
echo "EXIT=${PIPESTATUS[0]}"
