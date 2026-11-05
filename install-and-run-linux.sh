#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

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
if ! has_java_25; then
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
echo "Starting the application. Press Ctrl+C to stop it."
exec ./gradlew bootRun
