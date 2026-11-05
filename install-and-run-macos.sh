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

has_native_image() {
  command -v native-image >/dev/null 2>&1
}

confirm_graalvm_install() {
  local answer
  read -r -p "GraalVM 25 with Native Image is required. Install it with Homebrew now? [y/N]: " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]]
}

echo "Cover Letter Studio setup for macOS"
if [[ "$RUN_MODE" == "native" ]]; then
  if ! has_java_25 || ! has_native_image; then
    if ! confirm_graalvm_install; then
      echo "GraalVM installation declined. Install GraalVM 25 or later with Native Image, then rerun." >&2
      exit 1
    fi
    if ! command -v brew >/dev/null 2>&1; then
      echo "Homebrew is required for automatic GraalVM installation." >&2
      echo "Install Homebrew from https://brew.sh, then rerun this script." >&2
      exit 1
    fi
    brew install --cask graalvm-jdk
    if [[ -x /usr/libexec/java_home ]]; then
      java_home_25="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
      if [[ -n "$java_home_25" && -x "$java_home_25/bin/java" ]]; then
        export JAVA_HOME="$java_home_25"
        export PATH="$JAVA_HOME/bin:$PATH"
      fi
    fi
  fi
  if ! has_java_25 || ! has_native_image; then
    echo "GraalVM 25 with Native Image is still unavailable. Check your PATH and JAVA_HOME." >&2
    exit 1
  fi
elif ! has_java_25; then
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
if [[ "$RUN_MODE" == "native" ]]; then
  echo "Building the native executable. This may take several minutes..."
  ./gradlew nativeCompile
  echo "Starting the native application. Press Ctrl+C to stop it."
  exec ./build/native/nativeCompile/coveringletter
fi

echo "Starting the application. Press Ctrl+C to stop it."
exec ./gradlew bootRun
