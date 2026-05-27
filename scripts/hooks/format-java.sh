#!/usr/bin/env bash
# Post-edit hook: run Spotless format
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR"

# Extract required Java version from pom.xml
JAVA_VERSION=$(grep -oP '<java.version>\K[^<]+' pom.xml)
if [ -z "$JAVA_VERSION" ]; then echo "java.version not found in pom.xml" >&2; exit 1; fi

# Resolve JAVA_HOME if not set or wrong version
if [ -n "${JAVA_HOME:-}" ]; then
    CURRENT=$("$JAVA_HOME/bin/java" -version 2>&1 || true)
    echo "$CURRENT" | grep -q "$JAVA_VERSION" || unset JAVA_HOME
fi
if [ -z "${JAVA_HOME:-}" ]; then
    JDK=$(find "$HOME/.jdks" -maxdepth 1 -type d -name "*$JAVA_VERSION*" 2>/dev/null | head -1)
    if [ -n "$JDK" ]; then export JAVA_HOME="$JDK"; fi
fi

# Resolve mvn: PATH > Maven Wrapper local > .m2/wrapper
MVN=$(command -v mvn 2>/dev/null || true)
if [ -z "$MVN" ] && [ -f "./mvnw" ]; then MVN="./mvnw"; fi
if [ -z "$MVN" ]; then
    MVN=$(find "$HOME/.m2/wrapper/dists" -name "mvn" -type f 2>/dev/null | head -1)
fi
if [ -z "$MVN" ]; then echo "mvn not found" >&2; exit 1; fi

$MVN spotless:apply ${1:+-pl $1} -q
$MVN spotless:check ${1:+-pl $1} -q
echo "OK"
