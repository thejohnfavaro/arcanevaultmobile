@echo off
echo Generating Gradle wrapper using your installed Gradle 8.11.1...
where gradle >nul 2>&1 || (echo Gradle not found. Install Gradle 8.11.1 and add it to PATH. & exit /b 1)
gradle wrapper --gradle-version 8.11.1
if errorlevel 1 exit /b 1
echo Wrapper created. Open this project in Android Studio.
