@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "RUN_MODE=%~1"
if not defined RUN_MODE set "RUN_MODE=bootRun"
if /I not "%RUN_MODE%"=="bootRun" if /I not "%RUN_MODE%"=="native" goto usage
if not "%~2"=="" goto usage

echo Cover Letter Studio setup
if /I "%RUN_MODE%"=="native" goto java_ready
call :check_java
if errorlevel 1 goto missing_java
goto java_ready

:missing_java
echo Java 25 was not found. Install a Java 25 JDK manually and make sure java is on PATH.
echo Then run this script again.
exit /b 1

:java_ready
if /I not "%RUN_MODE%"=="native" echo Java 25 is available.

if not exist ".env" (
    >".env" echo GEMINI_API_KEY=
    >>".env" echo GEMINI_MODEL=gemini-3.1-flash-lite
    echo Created .env. You can enter the Gemini API key in the first-run setup page.
)

if /I "%RUN_MODE%"=="native" goto run_native

echo Starting the application. Press Ctrl+C to stop it.
call ".\gradlew.bat" bootRun
exit /b %errorlevel%

:run_native
echo Building the native executable. This may take several minutes...
call ".\gradlew.bat" nativeCompile
if errorlevel 1 exit /b %errorlevel%
echo Starting the native application. Press Ctrl+C to stop it.
call ".\build\native\nativeCompile\coveringletter.exe"
exit /b %errorlevel%

:usage
echo Usage: %~nx0 [bootRun^|native]
echo   bootRun  Start using Spring Boot on the JVM ^(default^).
echo   native   Build and start the GraalVM native executable.
exit /b 2

:check_java
set "JAVA_VERSION="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
if "%JAVA_VERSION:~0,2%"=="25" exit /b 0
exit /b 1
