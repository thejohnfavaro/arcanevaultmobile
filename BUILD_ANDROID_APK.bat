@echo off
setlocal
cd /d "%~dp0"
echo Arcane Vault Android 2.0.0 - Debug APK builder
if exist gradlew.bat (
  call gradlew.bat --no-daemon :app:assembleDebug
) else (
  where gradle >nul 2>nul
  if errorlevel 1 (
    echo ERROR: Gradle not found. Open this project in Android Studio and choose Build - Build APK(s), or run the GitHub Actions workflow.
    exit /b 1
  )
  call gradle --no-daemon :app:assembleDebug
)
if errorlevel 1 exit /b 1
echo Built APK: %~dp0app\build\outputs\apk\debug\app-debug.apk
endlocal
