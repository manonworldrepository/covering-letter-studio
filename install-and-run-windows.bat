@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo Cover Letter Studio setup
call :check_java
if not errorlevel 1 goto java_ready

echo Java 25 was not found. Attempting to install it...
where winget >nul 2>nul
if not errorlevel 1 (
    winget install --id Microsoft.OpenJDK.25 --exact --silent --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo Java installation with winget failed.
        exit /b 1
    )
    call :refresh_path
    goto verify_java
)

where choco >nul 2>nul
if not errorlevel 1 (
    choco install temurin25 -y
    if errorlevel 1 (
        echo Java installation with Chocolatey failed.
        exit /b 1
    )
    call :refresh_path
    goto verify_java
)

echo Java 25 is missing, and neither winget nor Chocolatey is available.
echo Install a Java 25 JDK, then run this script again.
exit /b 1

:verify_java
call :check_java
if errorlevel 1 (
    echo Java 25 was installed, but this terminal cannot find it yet.
    echo Open a new terminal and run this script again.
    exit /b 1
)

:java_ready
echo Java 25 is available.

if not exist ".env" (
    >".env" echo GEMINI_API_KEY=
    >>".env" echo GEMINI_MODEL=gemini-3.1-flash-lite
    echo Created .env. You can enter the Gemini API key in the first-run setup page.
)

echo Starting the application. Press Ctrl+C to stop it.
call ".\gradlew.bat" bootRun
exit /b %errorlevel%

:check_java
set "JAVA_VERSION="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
if "%JAVA_VERSION:~0,2%"=="25" exit /b 0
exit /b 1

:refresh_path
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')"`) do set "PATH=%%P"
exit /b 0
