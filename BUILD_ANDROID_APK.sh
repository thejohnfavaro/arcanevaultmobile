#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -x ./gradlew ]; then
  ./gradlew --no-daemon :app:assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon :app:assembleDebug
else
  echo 'Gradle not installed. Open this folder in Android Studio or run the included GitHub Actions workflow.' >&2
  exit 1
fi
echo "Built APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
