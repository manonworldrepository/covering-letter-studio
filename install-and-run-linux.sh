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

select_java_25() {
  local java_bin resolved_java java_home java_version
  local -a candidates=()

  if command -v java >/dev/null 2>&1; then
    candidates+=("$(command -v java)")
  fi
  for java_bin in /usr/lib/jvm/*/bin/java; do
    [[ -x "$java_bin" ]] && candidates+=("$java_bin")
  done

  for java_bin in "${candidates[@]}"; do
    resolved_java="$(readlink -f "$java_bin" 2>/dev/null || printf '%s' "$java_bin")"
    java_version="$("$resolved_java" -version 2>&1)" || continue
    if grep -Eq 'version "25([.]|")|openjdk 25' <<< "$java_version"; then
      java_home="$(dirname "$(dirname "$resolved_java")")"
      export JAVA_HOME="$java_home"
      export PATH="$JAVA_HOME/bin:$PATH"
      return 0
    fi
  done
  return 1
}

echo "Cover Letter Studio setup"
if [[ "$RUN_MODE" == "native" ]]; then
  if [[ "${EUID}" -eq 0 && -n "${SUDO_USER:-}" ]]; then
    echo "Do not run native mode with sudo; Gradle needs your regular user environment." >&2
    echo "Run: ./install-and-run-linux.sh native" >&2
    exit 1
  fi
elif ! select_java_25; then
  echo "Java 25 was not found. Install a Java 25 JDK manually and make sure it is available under /usr/lib/jvm or on PATH." >&2
  exit 1
fi
if [[ "$RUN_MODE" != "native" ]]; then
  echo "Using Java 25 at $JAVA_HOME."
fi

if [[ ! -f .env ]]; then
  printf 'GEMINI_API_KEY=\nGEMINI_MODEL=gemini-3.1-flash-lite\n' > .env
  echo "Created .env. You can enter the Gemini API key in the first-run setup page."
fi

chmod +x ./gradlew
echo "Cleaning previous build outputs..."
./gradlew clean

if [[ "$RUN_MODE" == "native" ]]; then
  echo "Building the native executable. This may take several minutes..."
  ./gradlew nativeCompile
  echo "Starting the native application. Press Ctrl+C to stop it."
  exec ./build/native/nativeCompile/coveringletter
fi

echo "Starting the application. Press Ctrl+C to stop it."
exec ./gradlew bootRun
