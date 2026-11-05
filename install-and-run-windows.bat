@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "RUN_MODE=%~1"
if not defined RUN_MODE set "RUN_MODE=bootRun"
if /I not "%RUN_MODE%"=="bootRun" if /I not "%RUN_MODE%"=="native" goto usage
if not "%~2"=="" goto usage

echo Cover Letter Studio setup
if /I "%RUN_MODE%"=="native" goto native_java_check

call :check_java
if errorlevel 1 goto missing_java

:native_java_check
call :configure_native_java
if errorlevel 1 goto missing_graalvm
call :check_visual_studio
if errorlevel 1 goto missing_visual_studio
goto java_ready

:missing_java
echo Java 25 was not found. Install a Java 25 JDK manually and make sure java is on PATH.
echo Then run this script again.
exit /b 1

:missing_graalvm
echo GraalVM 25 or later with Native Image was not found.
echo Install it manually and configure JAVA_HOME and PATH to its bin directory.
exit /b 1

:missing_visual_studio
echo Windows native compilation requires Visual Studio 2022 version 17.6 or later
echo with the "Desktop development with C++" workload and x64/x86 build tools.
echo Install or modify Visual Studio manually, then rerun this script.
exit /b 1

:java_ready
echo Java 25 is available.

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
if not exist ".\build\native\nativeCompile\coveringletter.exe" (
    echo Native compilation succeeded, but the expected executable was not found.
    exit /b 1
)
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

:configure_native_java
set "GRAALVM_HOME="
for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "$paths=@(& where.exe native-image 2>$null); if($env:JAVA_HOME){$paths+=Join-Path $env:JAVA_HOME 'bin\native-image.cmd'; $paths+=Join-Path $env:JAVA_HOME 'bin\native-image.exe'}; if(Get-Command scoop -ErrorAction SilentlyContinue){$paths+=scoop which native-image 2>$null}; foreach($root in @((Join-Path $env:ProgramFiles 'GraalVM'),(Join-Path ${env:ProgramFiles(x86)} 'GraalVM'))){if(Test-Path $root){$paths+=Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {Join-Path $_.FullName 'bin\native-image.cmd'; Join-Path $_.FullName 'bin\native-image.exe'}}}; foreach($p in $paths){if(!(Test-Path $p)){continue}; $bin=Split-Path -Parent $p; if((Split-Path -Leaf $bin) -ine 'bin'){continue}; $h=Split-Path -Parent $bin; $jp=Join-Path $h 'bin\java.exe'; if(!(Test-Path $jp)){continue}; $j=(& $jp -version 2>&1) -join [Environment]::NewLine; $m=[regex]::Match($j,'version\s+[^0-9]*(\d+)'); if(!$m.Success -or [int]$m.Groups[1].Value -lt 25 -or $j -notmatch 'GraalVM'){continue}; $n=(& $p --version 2>&1) -join [Environment]::NewLine; if($n -match 'GraalVM'){Write-Output $h; exit 0}}; exit 1"`) do set "GRAALVM_HOME=%%H"
if not defined GRAALVM_HOME exit /b 1
set "JAVA_HOME=%GRAALVM_HOME%"
set "PATH=%GRAALVM_HOME%\bin;%PATH%"
echo Using GraalVM at "%GRAALVM_HOME%".
exit /b 0

:check_visual_studio
powershell -NoProfile -Command "$v=Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'; if(!(Test-Path $v)){exit 1}; $p=& $v -latest -products '*' -version '[17.6,)' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath; if(!$p){exit 1}; exit 0" >nul 2>nul
exit /b %errorlevel%
