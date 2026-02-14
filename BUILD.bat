@echo off
chcp 65001 >nul
echo Building Music Player mod...
echo.

where gradle >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle not found. Install Gradle or use Gradle Wrapper.
    echo 1. Install: https://gradle.org/install/
    echo 2. Or copy gradle wrapper from Fabric example mod:
    echo    https://fabricmc.net/develop/
    pause
    exit /b 1
)

gradle build
if %ERRORLEVEL% NEQ 0 (
    echo Build failed.
    pause
    exit /b 1
)

echo.
echo Done. JAR: build\libs\musicplayer-1.0.0.jar
echo Put this file in your Minecraft "mods" folder.
pause
