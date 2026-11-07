# Android 2.0.0 validation

## Completed in preparation environment

- Node syntax and regression: `node tests/regression.mjs` — PASS
- Node new-features checks: `node tests/new-features.mjs` — PASS
- Node Android-specific parity checks: `node tests/mobile-parity.mjs` — PASS
- Headless Chromium smoke test of embedded HTML via a stubbed catalog service and localStorage — PASS. Tested grouped mobile navigation, physical card entry, Physical set toggle, manual match recording, and absence of uncaught page errors. Browser test is not a native Android/device test.
- Source ZIP verified with CRC checks.

## Not completed

- Compiling the Android APK (no Android SDK/Gradle installed here)
- Emulator/physical-device validation
- Live Scryfall catalog download and Android-specific system document picker
- Publishing an Android GitHub release

To build, open in Android Studio with SDK 35/JDK 17, sync dependencies, then Build > Build APK(s).
