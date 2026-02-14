@rem Gradle wrapper for Windows
@if "%DEBUG%"=="" @echo off

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set DIRNAME=%DIRNAME:~0,-1%

set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

set WRAPPER_JAR=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" goto run_with_wrapper
echo Gradle wrapper JAR not found. Running "gradle" if available...
where gradle >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Install Gradle from https://gradle.org/install/ or run "gradle wrapper" in a project that has it.
    exit /b 1
)
gradle %*
exit /b %ERRORLEVEL%

:run_with_wrapper
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar "%WRAPPER_JAR%" %*
exit /b %ERRORLEVEL%
