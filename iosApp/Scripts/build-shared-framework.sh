#!/bin/sh
#
# Builds and embeds the Kotlin framework for whichever SDK and architecture Xcode is
# targeting. Runs as a pre-build phase; Xcode gives scripts a minimal environment, so
# Gradle and a JDK are located explicitly rather than assumed to be on PATH.

set -e

PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
export PATH

PROJECT_ROOT="$SRCROOT/.."
cd "$PROJECT_ROOT"

# ---------------------------------------------------------------- locate a JDK

if [ -z "$JAVA_HOME" ]; then
  if /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home)"
  else
    # Homebrew's openjdk formulae are keg-only, so java_home never sees them.
    for candidate in \
      /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
      /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
      /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
      /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; do
      if [ -x "$candidate/bin/java" ]; then
        JAVA_HOME="$candidate"
        break
      fi
    done
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  echo "error: no JDK found. Install one with 'brew install openjdk@21', or set JAVA_HOME." >&2
  exit 1
fi
export JAVA_HOME

# -------------------------------------------------------------- locate Gradle
#
# Plain `gradle` is last because the current Homebrew formula is Gradle 9, which the
# Kotlin 2.2 plugin rejects.

if [ -x "$PROJECT_ROOT/gradlew" ]; then
  GRADLE="$PROJECT_ROOT/gradlew"
elif [ -x /opt/homebrew/opt/gradle@8/bin/gradle ]; then
  GRADLE=/opt/homebrew/opt/gradle@8/bin/gradle
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="$(command -v gradle)"
else
  echo "error: no Gradle found. Install it with 'brew install gradle@8'." >&2
  exit 1
fi

echo "note: building Shared.framework with $GRADLE (JAVA_HOME=$JAVA_HOME)"

# Reads CONFIGURATION, SDK_NAME, ARCHS, TARGET_BUILD_DIR and FRAMEWORKS_FOLDER_PATH
# from the Xcode environment, so this only works as a build phase.
"$GRADLE" --quiet :shared:embedAndSignAppleFrameworkForXcode
