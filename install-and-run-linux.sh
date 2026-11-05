#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

RUN_MODE="${1:-bootRun}"
if [[ "$#" -gt 1 ]] || { [[ "$RUN_MODE" != "bootRun" ]] && [[ "$RUN_MODE" != "native" ]]; }; then
  echo "Usage: $0 [bootRun|native]" >&2
  echo "  bootRun  Start using Spring Boot on the JVM (default)." >&2
  echo "  native   Build and start the GraalVM native executable." >&2
  exit 2
fi

has_java_25() {
  command -v java >/dev/null 2>&1 || return 1
  java -version 2>&1 | head -n 1 | grep -Eq '"25([.]|")'
}

install_java_25() {
  if command -v apt-get >/dev/null 2>&1; then
    if [[ "${EUID}" -eq 0 ]]; then
      apt-get update
      apt-get install -y openjdk-25-jdk
    else
      sudo apt-get update
      sudo apt-get install -y openjdk-25-jdk
    fi
  elif command -v dnf >/dev/null 2>&1; then
    if [[ "${EUID}" -eq 0 ]]; then
      dnf install -y java-25-openjdk-devel
    else
      sudo dnf install -y java-25-openjdk-devel
    fi
  elif command -v zypper >/dev/null 2>&1; then
    if [[ "${EUID}" -eq 0 ]]; then
      zypper --non-interactive install java-25-openjdk-devel
    else
      sudo zypper --non-interactive install java-25-openjdk-devel
    fi
  elif command -v pacman >/dev/null 2>&1; then
    if [[ "${EUID}" -eq 0 ]]; then
      pacman -Sy --needed --noconfirm jdk-openjdk
    else
      sudo pacman -Sy --needed --noconfirm jdk-openjdk
    fi
  else
    echo "No supported package manager found (apt, dnf, zypper, or pacman)." >&2
    echo "Install a Java 25 JDK, then run this script again." >&2
    exit 1
  fi
}

echo "Cover Letter Studio setup"
if [[ "$RUN_MODE" == "native" ]]; then
  if ! has_java_25; then
    echo "Native mode requires GraalVM 25 or later with Native Image and Java 25 available on PATH." >&2
    exit 1
  fi
  if ! command -v native-image >/dev/null 2>&1; then
    echo "The native-image command was not found. Install GraalVM Native Image and ensure it is on PATH." >&2
    exit 1
  fi
elif ! has_java_25; then
  echo "Java 25 was not found. Attempting installation with the system package manager..."
  install_java_25
fi

if ! has_java_25; then
  echo "Java 25 is still unavailable. Check that your distribution provides Java 25 and that java points to it." >&2
  exit 1
fi
echo "Java 25 is available."

if [[ ! -f .env ]]; then
  printf 'GEMINI_API_KEY=\nGEMINI_MODEL=gemini-3.1-flash-lite\n' > .env
  echo "Created .env. You can enter the Gemini API key in the first-run setup page."
fi

chmod +x ./gradlew
if [[ "$RUN_MODE" == "native" ]]; then
  echo "Building the native executable. This may take several minutes..."
  ./gradlew nativeCompile
  echo "Starting the native application. Press Ctrl+C to stop it."
  exec ./build/native/nativeCompile/coveringletter
fi

echo "Starting the application. Press Ctrl+C to stop it."
exec ./gradlew bootRun
