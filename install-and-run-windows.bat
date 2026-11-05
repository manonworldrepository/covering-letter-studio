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

:native_java_check
call :configure_native_java
if errorlevel 1 goto install_graalvm_prompt
call :check_visual_studio
if errorlevel 1 goto missing_visual_studio
goto java_ready

:install_graalvm_prompt
echo Native mode requires GraalVM 25 or later with Native Image.
call :check_admin
if errorlevel 1 (
    echo Chocolatey requires an Administrator terminal to install GraalVM.
    echo Close this terminal, open PowerShell or Command Prompt with "Run as administrator",
    echo then run .\install-and-run-windows.bat native again.
    exit /b 1
)
set "INSTALL_GRAALVM="
set /p "INSTALL_GRAALVM=Install GraalVM with Chocolatey now? [y/N]: "
if /I not "%INSTALL_GRAALVM%"=="y" (
    echo Install GraalVM 25 or later with Native Image, then run this script again.
    exit /b 1
)
where choco >nul 2>nul
if errorlevel 1 (
    echo Chocolatey is required for automatic GraalVM installation on Windows.
    echo Install Chocolatey, then rerun this script, or install GraalVM manually.
    exit /b 1
)
set "GRAALVM_LOG=%TEMP%\coveringletter-graalvm-%RANDOM%.log"
choco install graalvm -y >"%GRAALVM_LOG%" 2>&1
set "CHOCOLATEY_EXIT=%ERRORLEVEL%"
type "%GRAALVM_LOG%"
findstr /I /C:"NuGetResolverInputException" "%GRAALVM_LOG%" >nul
if not errorlevel 1 goto graalvm_nuget_error
findstr /I /C:"Unable to obtain lock file access" /C:"UnauthorizedAccessException" /C:"Access to the path" "%GRAALVM_LOG%" >nul
if not errorlevel 1 goto graalvm_permission_error
if not "%CHOCOLATEY_EXIT%"=="0" goto graalvm_install_error
call :refresh_path
call :configure_native_java
if errorlevel 1 goto graalvm_verify_error
call :check_visual_studio
if errorlevel 1 goto missing_visual_studio
del /q "%GRAALVM_LOG%" >nul 2>nul
goto java_ready

:graalvm_nuget_error
echo Chocolatey could not resolve the GraalVM package because its NuGet package feed failed.
echo This can be a temporary feed or network error. Retry later or install GraalVM 25 manually.
echo Chocolatey output was saved to "%GRAALVM_LOG%".
exit /b 1

:graalvm_permission_error
echo Chocolatey could not access its package directory or obtain the GraalVM package lock.
echo Make sure no other Chocolatey install is running, then retry from an Administrator terminal.
echo Do not remove Chocolatey lock files while another package operation may be active.
echo Chocolatey output was saved to "%GRAALVM_LOG%".
exit /b 1

:graalvm_install_error
echo GraalVM installation with Chocolatey failed with exit code %CHOCOLATEY_EXIT%.
echo Chocolatey output was saved to "%GRAALVM_LOG%".
exit /b 1

:graalvm_verify_error
echo No usable GraalVM 25 or later installation with Native Image was found.
echo The native-image executable on PATH must belong to GraalVM 25 or later.
echo Check "%GRAALVM_LOG%", then install GraalVM 25 or set PATH to its bin directory.
exit /b 1

:missing_visual_studio
echo Windows native compilation requires Visual Studio 2022 version 17.6 or later
echo with the "Desktop development with C++" workload and x64/x86 build tools.
echo Install or modify Visual Studio, then rerun this script.
exit /b 1

:verify_java
call :check_java
if errorlevel 1 (
    echo Java 25 was installed, but this terminal cannot find it yet.
    echo Open a new terminal and run this script again.
    exit /b 1
)
goto java_ready

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

:refresh_path
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "[Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')"`) do set "PATH=%%P"
exit /b 0

:check_admin
powershell -NoProfile -Command "$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent()); if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { exit 0 } else { exit 1 }" >nul 2>nul
exit /b %errorlevel%
