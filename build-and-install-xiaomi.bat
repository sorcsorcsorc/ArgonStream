@echo off
echo ===================================================
echo Argon Stream - Unit Tests + Debug APK Build
echo ===================================================

call gradlew.bat testDebugUnitTest assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build or Unit Tests failed.
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] Debug APK generated at:
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo Installing onto connected Xiaomi device via ADB...
adb install -r app\build\outputs\apk\debug\app-debug.apk
pause