#!/bin/sh
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"

WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

# Ensure we use the Java 21 runtime that exists in this environment.
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
JAVA_CMD="$JAVA_HOME/bin/java"

if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p "$WRAPPER_DIR"
  echo "Downloading Maven wrapper jar..."
  curl -sSfL "$WRAPPER_URL" -o "$WRAPPER_JAR"
fi

# Redirect Maven user home + local repo to the project directory.
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$BASE_DIR/.m2}"
mkdir -p "$MAVEN_USER_HOME"

exec "$JAVA_CMD" -cp "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -Dmaven.repo.local="$MAVEN_USER_HOME/repository" \
  -Duser.home="$BASE_DIR" \
  "$@"

#!/bin/sh
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"

WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

# Ensure we use the Java 21 runtime that exists in this environment.
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
JAVA_CMD="$JAVA_HOME/bin/java"

if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p "$WRAPPER_DIR"
  echo "Downloading Maven wrapper jar..."
  curl -sSfL "$WRAPPER_URL" -o "$WRAPPER_JAR"
fi

# Some Maven wrapper jar builds omit a Main-Class manifest entry; run by classname explicitly.
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$BASE_DIR/.m2}"
mkdir -p "$MAVEN_USER_HOME"

exec "$JAVA_CMD" -cp "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -Dmaven.repo.local="$MAVEN_USER_HOME/repository" \
  -Duser.home="$BASE_DIR" \
  "$@"

