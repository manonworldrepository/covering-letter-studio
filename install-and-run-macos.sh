#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

has_java_25() {
  command -v java >/dev/null 2>&1 || return 1
  java -version 2>&1 | head -n 1 | grep -Eq '"25([.]|")'
}

echo "Cover Letter Studio setup for macOS"
if ! has_java_25; then
  if ! command -v brew >/dev/null 2>&1; then
    echo "Java 25 and Homebrew were not found." >&2
    echo "Install Homebrew from https://brew.sh, then run this script again." >&2
    exit 1
  fi

  echo "Java 25 was not found. Installing Eclipse Temurin 25 with Homebrew..."
  brew install --cask temurin@25

  if ! has_java_25 && [[ -x /usr/libexec/java_home ]]; then
    java_home_25="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
    if [[ -n "$java_home_25" && -x "$java_home_25/bin/java" ]]; then
      export JAVA_HOME="$java_home_25"
      export PATH="$JAVA_HOME/bin:$PATH"
    fi
  fi
fi

if ! has_java_25; then
  echo "Java 25 is still unavailable. Open a new terminal and run this script again." >&2
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
