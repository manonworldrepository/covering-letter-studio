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

echo "Cover Letter Studio setup for macOS"
if [[ "$RUN_MODE" != "native" ]] && ! has_java_25; then
  echo "Java 25 was not found. Install a Java 25 JDK manually and make sure java is on PATH." >&2
  exit 1
fi
if [[ "$RUN_MODE" != "native" ]]; then
  echo "Java 25 is available."
fi

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
