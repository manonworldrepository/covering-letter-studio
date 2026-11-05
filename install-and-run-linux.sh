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

select_graalvm_25() {
  local java_bin resolved_java graalvm_home java_version image_path image_home image_version
  local -a candidates=()

  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    candidates+=("$JAVA_HOME/bin/java")
  fi
  if command -v java >/dev/null 2>&1; then
    candidates+=("$(command -v java)")
  fi

  for java_bin in "${candidates[@]}"; do
    resolved_java="$(readlink -f "$java_bin" 2>/dev/null || printf '%s' "$java_bin")"
    graalvm_home="$(dirname "$(dirname "$resolved_java")")"
    java_version="$("$resolved_java" -version 2>&1)" || continue
    grep -Eq 'version "25([.]|[+])|openjdk 25' <<< "$java_version" || continue
    grep -q 'GraalVM' <<< "$java_version" || continue

    image_path="$graalvm_home/bin/native-image"
    if [[ ! -x "$image_path" ]]; then
      image_path="$(command -v native-image 2>/dev/null || true)"
    fi
    if [[ -z "$image_path" || ! -x "$image_path" ]]; then
      return 2
    fi
    image_path="$(readlink -f "$image_path" 2>/dev/null || printf '%s' "$image_path")"
    image_home="$(dirname "$(dirname "$image_path")")"
    if [[ "$image_home" != "$graalvm_home" ]]; then
      return 2
    fi
    image_version="$("$image_path" --version 2>&1)" || continue
    grep -Eq 'native-image[[:space:]]+(25|2[6-9]|[3-9][0-9])([.]|[+[:space:]])' <<< "$image_version" || continue
    grep -q 'GraalVM' <<< "$image_version" || continue

    export JAVA_HOME="$graalvm_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
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
  if select_graalvm_25; then
    native_status=0
  else
    native_status=$?
  fi
  if [[ "$native_status" -eq 2 ]]; then
    echo "A GraalVM 25 JDK was found, but its Native Image executable is missing." >&2
    echo "Install the Native Image component for this GraalVM distribution, then rerun the script." >&2
    exit 1
  elif [[ "$native_status" -ne 0 ]]; then
    echo "GraalVM 25 or later with Native Image was not found or is not configured for Java." >&2
    echo "Install it manually, set JAVA_HOME to its installation directory, and add its bin directory to PATH." >&2
    exit 1
  fi
elif ! select_java_25; then
  echo "Java 25 was not found. Install a Java 25 JDK manually and make sure it is available under /usr/lib/jvm or on PATH." >&2
  exit 1
fi

if ! select_java_25; then
  echo "Java 25 is still unavailable. Check that your distribution installed a Java 25 JDK." >&2
  exit 1
fi
echo "Using Java 25 at $JAVA_HOME."

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
