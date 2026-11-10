#!/usr/bin/env bash
# Post-edit hook: run Spotless format (Linux and macOS)
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR"

# Extract required Java version from pom.xml (sed -E: BSD grep on macOS has no -P)
JAVA_VERSION=$(sed -nE 's|.*<java\.version>([^<]+)</java\.version>.*|\1|p' pom.xml | head -1)
if [ -z "$JAVA_VERSION" ]; then echo "java.version not found in pom.xml" >&2; exit 1; fi

# Print the major version of the JDK at $1 (empty if not a JDK)
java_major() {
    [ -x "$1/bin/java" ] || return 0
    "$1/bin/java" -version 2>&1 | sed -nE '1s/.*version "(1\.)?([0-9]+).*/\2/p'
}

# Resolve JAVA_HOME if not set or wrong version
if [ -n "${JAVA_HOME:-}" ] && [ "$(java_major "$JAVA_HOME")" != "$JAVA_VERSION" ]; then
    unset JAVA_HOME
fi
if [ -z "${JAVA_HOME:-}" ]; then
    CANDIDATES=()
    # macOS registered JDKs
    if [ -x /usr/libexec/java_home ]; then
        CANDIDATES+=("$(/usr/libexec/java_home -v "$JAVA_VERSION" 2>/dev/null || true)")
    fi
    # IntelliJ (Linux: ~/.jdks, macOS: ~/Library/Java), system, Homebrew, SDKMAN
    for DIR in "$HOME"/.jdks/*"$JAVA_VERSION"* \
               "$HOME"/.jdks/*"$JAVA_VERSION"*/Contents/Home \
               "$HOME"/Library/Java/JavaVirtualMachines/*"$JAVA_VERSION"*/Contents/Home \
               /Library/Java/JavaVirtualMachines/*"$JAVA_VERSION"*/Contents/Home \
               /opt/homebrew/opt/openjdk@"$JAVA_VERSION"/libexec/openjdk.jdk/Contents/Home \
               /usr/local/opt/openjdk@"$JAVA_VERSION"/libexec/openjdk.jdk/Contents/Home \
               "$HOME"/.sdkman/candidates/java/"$JAVA_VERSION"*; do
        CANDIDATES+=("$DIR")
    done
    for JDK in "${CANDIDATES[@]}"; do
        if [ -n "$JDK" ] && [ "$(java_major "$JDK")" = "$JAVA_VERSION" ]; then
            export JAVA_HOME="$JDK"
            break
        fi
    done
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
