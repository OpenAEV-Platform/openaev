#!/usr/bin/env bash
# ==============================================================================
# OpenAEV — Auto-DB Installer
# ==============================================================================
#
# Copies the DevDatabaseEnvironmentPostProcessor and its Spring registration
# from  openaev-dev/test-containers/  into  openaev-api/  so the backend can
# auto-start a PostgreSQL container on launch (dev profile only).
# Supports both Podman and Docker (auto-detected at runtime).
#
# The copied files are git-ignored — they never pollute the API module in VCS.
#
# Supported platforms:
#   - Linux (native bash)
#   - macOS (bash or zsh)
#   - Windows 10/11 (Git Bash — install Git for Windows first)
#
# Usage:
#   cd openaev-dev && ./setup-auto-db.sh        # Linux / macOS
#   bash openaev-dev/setup-auto-db.sh           # Windows (Git Bash) or from project root
#
# To uninstall:
#   rm openaev-api/src/main/java/io/openaev/config/DevDatabaseEnvironmentPostProcessor.java
#   rm openaev-api/src/main/resources/META-INF/spring.factories
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

SOURCE_DIR="$SCRIPT_DIR/test-containers"
JAVA_DEST="$ROOT_DIR/openaev-api/src/main/java/io/openaev/config"
META_DEST="$ROOT_DIR/openaev-api/src/main/resources/META-INF"

# --- Sanity checks -----------------------------------------------------------

if [ ! -d "$SOURCE_DIR" ]; then
  echo "❌ Source directory not found: $SOURCE_DIR"
  exit 1
fi

for file in DevDatabaseEnvironmentPostProcessor.java spring.factories; do
  if [ ! -f "$SOURCE_DIR/$file" ]; then
    echo "❌ Missing source file: $SOURCE_DIR/$file"
    exit 1
  fi
done

# --- Copy Java class ----------------------------------------------------------

mkdir -p "$JAVA_DEST"
cp "$SOURCE_DIR/DevDatabaseEnvironmentPostProcessor.java" "$JAVA_DEST/"
echo "  ✅  Copied DevDatabaseEnvironmentPostProcessor.java → $JAVA_DEST/"

# --- Copy / merge spring.factories -------------------------------------------

mkdir -p "$META_DEST"

FACTORIES_FILE="$META_DEST/spring.factories"
EPP_LINE="io.openaev.config.DevDatabaseEnvironmentPostProcessor"

if [ -f "$FACTORIES_FILE" ]; then
  # File already exists — check if our entry is already there
  if grep -qF "$EPP_LINE" "$FACTORIES_FILE"; then
    echo "  ✅  spring.factories already contains the auto-db entry — skipped."
  else
    # Append our entry to the existing key (or add a new key)
    if grep -q "^org.springframework.boot.env.EnvironmentPostProcessor=" "$FACTORIES_FILE"; then
      # Key exists — append with a comma + backslash continuation
      # (avoid sed -i which behaves differently on macOS / Git Bash)
      TMP_FILE="$FACTORIES_FILE.tmp"
      sed "s/^org.springframework.boot.env.EnvironmentPostProcessor=\(.*\)/org.springframework.boot.env.EnvironmentPostProcessor=\1,\\\\/" \
        "$FACTORIES_FILE" > "$TMP_FILE"
      echo "  $EPP_LINE" >> "$TMP_FILE"
      mv "$TMP_FILE" "$FACTORIES_FILE"
      echo "  ✅  Appended auto-db entry to existing spring.factories"
    else
      # Key doesn't exist — add it
      echo "" >> "$FACTORIES_FILE"
      cat "$SOURCE_DIR/spring.factories" >> "$FACTORIES_FILE"
      echo "  ✅  Added auto-db entry to spring.factories"
    fi
  fi
else
  cp "$SOURCE_DIR/spring.factories" "$FACTORIES_FILE"
  echo "  ✅  Copied spring.factories → $META_DEST/"
fi

# --- Done ---------------------------------------------------------------------

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅  Auto-DB setup complete!                                ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "  The backend will now auto-start a PostgreSQL container"
echo "  (using Podman or Docker, auto-detected) when launched with"
echo "  the 'dev' profile and the property:"
echo ""
echo "    openaev.dev.auto-start-database=true"
echo ""
echo "  Optional — use a fixed port instead of a per-branch port:"
echo ""
echo "    openaev.dev.database-port=5432"
echo ""
echo "  Optional — force a specific container runtime:"
echo ""
echo "    openaev.dev.container-runtime=podman"
echo ""
echo "  All properties go in application-dev.properties."
echo ""
echo "  These files are git-ignored and will NOT be committed."
echo ""

