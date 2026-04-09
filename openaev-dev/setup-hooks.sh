#!/usr/bin/env bash
# ==============================================================================
# OpenAEV — Git Hooks Installer
# ==============================================================================
#
# Installs local Git hooks for the OpenAEV project:
#   - pre-commit:          Runs Spotless (backend) and ESLint (frontend) on
#                          staged files. If files were reformatted, the commit
#                          is aborted so you can review the diff first.
#   - prepare-commit-msg:  Warns about immutable collection usage (List.of,
#                          Set.of, Map.of, etc.) in changed lines of staged
#                          Java files (pre-existing code is not checked).
#                          Blocks the first commit attempt; acknowledge by
#                          committing again.
#
# Supported platforms:
#   - Linux (native bash)
#   - macOS (bash or zsh — hooks use #!/usr/bin/env bash)
#   - Windows 10/11 (Git Bash — install Git for Windows first)
#
# Usage:
#   ./openaev-dev/setup-hooks.sh            # Linux / macOS (from project root)
#   bash openaev-dev/setup-hooks.sh         # Windows (Git Bash)
#
# Prerequisites:
#   - Java 21 (JAVA_HOME or auto-detected)
#   - Node.js >= 22.11.0 (via nvm or system)
#   - Yarn (in openaev-front/)
#   - Maven wrapper (./mvnw or mvnw.cmd) at project root
#
# The hooks are local to your clone — they are NOT versioned in .git/.
# Re-run this script after a fresh clone or if hooks are updated.
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
HOOKS_DIR="$ROOT_DIR/.git/hooks"

if [ ! -d "$ROOT_DIR/.git" ]; then
  echo "❌ Not a Git repository. Run this script from the project root."
  exit 1
fi

mkdir -p "$HOOKS_DIR"

# ==============================================================================
# pre-commit hook
# ==============================================================================
cat > "$HOOKS_DIR/pre-commit" << 'HOOK_EOF'
#!/usr/bin/env bash
# Git pre-commit hook — Spotless + ESLint auto-fix on staged files.
# If formatters modify any file, the commit is ABORTED for review.
# Simply re-run `git commit` after reviewing to proceed.
#
# Cross-platform: Linux, macOS, Windows (Git Bash).
set -e
ROOT_DIR="$(git rev-parse --show-toplevel)"
GIT_DIR="${GIT_DIR:-$ROOT_DIR/.git}"

# -- Skip during rebase / merge / cherry-pick --
if [ -d "$GIT_DIR/rebase-merge" ] || [ -d "$GIT_DIR/rebase-apply" ] \
   || [ -f "$GIT_DIR/MERGE_HEAD" ] || [ -f "$GIT_DIR/CHERRY_PICK_HEAD" ]; then
  echo "⏭️  [pre-commit] Rebase/merge in progress — skipping hooks."
  exit 0
fi

# -- Detect OS --
detect_os() {
  case "$(uname -s)" in
    Darwin*)  echo "macos"  ;;
    MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
    *)        echo "linux"  ;;
  esac
}
OS="$(detect_os)"

# -- Resolve JAVA_HOME if not set --
if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}" ]; then
  case "$OS" in
    macos)
      # macOS: try IntelliJ JDKs, then /usr/libexec/java_home
      JAVA_HOME="$(find "$HOME/Library/Java/JavaVirtualMachines" "$HOME/.jdks" -maxdepth 1 -name '*21*' 2>/dev/null | head -1)"
      if [ -z "$JAVA_HOME" ] && command -v /usr/libexec/java_home &>/dev/null; then
        JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
      fi
      ;;
    windows)
      # Windows (Git Bash): IntelliJ JDKs or LOCALAPPDATA
      JAVA_HOME="$(find "$USERPROFILE/.jdks" "$LOCALAPPDATA/Programs/Eclipse Adoptium" -maxdepth 2 -name '*21*' -type d 2>/dev/null | head -1)"
      ;;
    *)
      # Linux: ~/.jdks (IntelliJ default)
      JAVA_HOME="$(find "$HOME/.jdks" -maxdepth 1 -name 'temurin-21*' 2>/dev/null | head -1)"
      ;;
  esac
  export JAVA_HOME="${JAVA_HOME:-}"
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}" ]; then
  echo "⚠️  [pre-commit] JAVA_HOME not set and no Java 21 found — skipping Spotless."
  JAVA_HOME=""
fi

# -- Resolve Maven wrapper --
mvnw_cmd() {
  if [ "$OS" = "windows" ] && [ -f "$ROOT_DIR/mvnw.cmd" ]; then
    echo "$ROOT_DIR/mvnw.cmd"
  else
    echo "$ROOT_DIR/mvnw"
  fi
}

# -- Load Node.js (nvm / fnm / system) --
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
# macOS Homebrew nvm
[ -s "/opt/homebrew/opt/nvm/nvm.sh" ] && . "/opt/homebrew/opt/nvm/nvm.sh"
# fnm (Rust-based nvm alternative, popular on Windows/macOS)
command -v fnm &>/dev/null && eval "$(fnm env --shell bash 2>/dev/null)" || true

# -- Detect which areas have staged changes --
BACKEND_CHANGED=false
FRONTEND_CHANGED=false
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM)
for file in $STAGED_FILES; do
  case "$file" in
    openaev-api/* | openaev-framework/* | openaev-model/* | pom.xml)
      BACKEND_CHANGED=true
      ;;
    openaev-front/src/*)
      FRONTEND_CHANGED=true
      ;;
  esac
done

REFORMATTED_FILES=""

# -- Backend: Spotless apply (only on staged Java files) --
if [ "$BACKEND_CHANGED" = true ] && [ -n "$JAVA_HOME" ]; then
  echo "🔧 [pre-commit] Running spotless:apply on staged backend files..."
  JAVA_FILES=""
  for file in $STAGED_FILES; do
    case "$file" in
      *.java)
        ESCAPED=$(echo "$file" | sed 's/\./\\./g')
        if [ -z "$JAVA_FILES" ]; then
          JAVA_FILES="$ESCAPED"
        else
          JAVA_FILES="$JAVA_FILES|$ESCAPED"
        fi
        ;;
    esac
  done
  if [ -n "$JAVA_FILES" ]; then
    SPOTLESS_PATTERN=".*($JAVA_FILES)"
    MVNW="$(mvnw_cmd)"
    (cd "$ROOT_DIR" && "$MVNW" spotless:apply -q -pl openaev-api,openaev-framework,openaev-model \
      -DspotlessFiles="$SPOTLESS_PATTERN" 2>&1) || {
      echo "❌ [pre-commit] spotless:apply failed. Please fix the issues and try again."
      exit 1
    }
  fi
  for file in $STAGED_FILES; do
    case "$file" in
      openaev-api/*.java | openaev-framework/*.java | openaev-model/*.java)
        if ! git diff --quiet -- "$ROOT_DIR/$file" 2>/dev/null; then
          REFORMATTED_FILES="$REFORMATTED_FILES $file"
        fi
        ;;
    esac
  done
fi

# -- Frontend: ESLint fix (only on staged frontend files) --
if [ "$FRONTEND_CHANGED" = true ]; then
  echo "🔧 [pre-commit] Running eslint --fix on staged frontend files..."
  FRONT_FILES=""
  for file in $STAGED_FILES; do
    case "$file" in
      openaev-front/src/*.ts | openaev-front/src/*.tsx | openaev-front/src/*.js | openaev-front/src/*.jsx)
        REL_PATH="${file#openaev-front/}"
        FRONT_FILES="$FRONT_FILES $REL_PATH"
        ;;
    esac
  done
  if [ -n "$FRONT_FILES" ]; then
    (cd "$ROOT_DIR/openaev-front" && yarn eslint --fix $FRONT_FILES 2>&1) || {
      echo "❌ [pre-commit] eslint --fix failed. Please fix the issues and try again."
      exit 1
    }
  fi
  for file in $STAGED_FILES; do
    case "$file" in
      openaev-front/src/*)
        if ! git diff --quiet -- "$ROOT_DIR/$file" 2>/dev/null; then
          REFORMATTED_FILES="$REFORMATTED_FILES $file"
        fi
        ;;
    esac
  done
fi

# -- If formatters changed files, abort so the user can review --
if [ -n "$REFORMATTED_FILES" ]; then
  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  🔍 Formatting tools modified the following files:         ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  for f in $REFORMATTED_FILES; do
    echo "   📝 $f"
  done
  echo ""
  echo "  Here is a summary of the changes:"
  echo "  ──────────────────────────────────"
  for f in $REFORMATTED_FILES; do
    DIFF_OUTPUT=$(cd "$ROOT_DIR" && git diff --stat -- "$f" 2>/dev/null)
    if [ -n "$DIFF_OUTPUT" ]; then
      echo "   $DIFF_OUTPUT"
    fi
  done
  echo ""
  echo "  Review the changes, then:"
  echo "     git diff              -- see full diff"
  echo "     git add -p            -- stage interactively"
  echo "     git add -u && git commit   -- accept all and commit"
  echo ""
  echo "  ❌ Commit aborted — please review formatting changes first."
  exit 1
fi

# -- If no reformatting needed, re-stage (safety net) and proceed --
for file in $STAGED_FILES; do
  case "$file" in
    openaev-api/* | openaev-framework/* | openaev-model/* | openaev-front/src/*)
      git add "$ROOT_DIR/$file"
      ;;
  esac
done

echo "✅ [pre-commit] Formatting OK — no changes needed."
HOOK_EOF

chmod +x "$HOOKS_DIR/pre-commit" 2>/dev/null || true

# ==============================================================================
# prepare-commit-msg hook
# ==============================================================================
cat > "$HOOKS_DIR/prepare-commit-msg" << 'HOOK_EOF'
#!/usr/bin/env bash
# Git prepare-commit-msg hook — Immutable collection usage warnings.
# Warns about List.of, Set.of, Map.of, Collections.unmodifiable* in CHANGED
# lines of staged Java files (pre-existing code you didn't touch is ignored).
#
# Cross-platform: Linux, macOS, Windows (Git Bash).
#
# Flow:
#   1st commit attempt -> warnings shown, commit aborted, marker file created
#   2nd commit attempt -> marker valid (same files), commit proceeds
#
# The marker is fingerprinted against staged files, so any `git add` resets it.

ROOT_DIR="$(git rev-parse --show-toplevel)"
HOOK_GIT_DIR="${GIT_DIR:-$ROOT_DIR/.git}"

# -- Skip during rebase / merge / cherry-pick --
if [ -d "$HOOK_GIT_DIR/rebase-merge" ] || [ -d "$HOOK_GIT_DIR/rebase-apply" ] \
   || [ -f "$HOOK_GIT_DIR/MERGE_HEAD" ] || [ -f "$HOOK_GIT_DIR/CHERRY_PICK_HEAD" ]; then
  exit 0
fi

# -- Portable hash (works on Linux, macOS, Windows Git Bash) --
portable_hash() {
  # git is always available in a git hook — use it for hashing
  git hash-object --stdin
}

MARKER_FILE="$HOOK_GIT_DIR/immutable-reviewed"

STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM)
IMMUTABLE_WARNINGS=0
WARNING_LINES=""

# Only scan CHANGED lines (additions) — not the entire file.
# This avoids false positives from pre-existing code the committer didn't touch.
for file in $STAGED_FILES; do
  case "$file" in
    *.java)
      # Extract added lines from the staged diff with line numbers.
      # git diff --cached -U0 gives hunks like @@ -old,count +new,count @@
      # We parse the +new,count to track line numbers of added lines.
      CURRENT_LINE=0
      git diff --cached -U0 -- "$file" | while IFS= read -r diff_line; do
        # Parse hunk header to get starting line number of added lines
        if printf '%s' "$diff_line" | grep -qE '^\@\@ '; then
          # Extract +N or +N,M from the hunk header
          NEW_RANGE=$(printf '%s' "$diff_line" | sed -n 's/^@@ -[^ ]* +\([^ ]*\) @@.*/\1/p')
          CURRENT_LINE=$(printf '%s' "$NEW_RANGE" | cut -d, -f1)
          # Store in temp file for subshell visibility
          printf '%s' "$CURRENT_LINE" > "$HOOK_GIT_DIR/.immutable-hook-line"
          continue
        fi
        # Only process added lines (start with +, but not +++ file header)
        case "$diff_line" in
          +++)
            continue
            ;;
          +*)
            LINE_CONTENT="${diff_line##+}"
            CURRENT_LINE=$(cat "$HOOK_GIT_DIR/.immutable-hook-line" 2>/dev/null || echo 0)
            # Skip comments
            TRIMMED="${LINE_CONTENT#"${LINE_CONTENT%%[![:space:]]*}"}"
            case "$TRIMMED" in
              //*|'*'*|\**) ;;
              *)
                # Detect immutable collection factory methods
                if printf '%s' "$LINE_CONTENT" | grep -qE '(List|Map|Set)\.(of|copyOf)[[:space:]]*\('; then
                  printf '   ⚠️  %s:%s — %.100s\n' "$file" "$CURRENT_LINE" "$TRIMMED" >> "$HOOK_GIT_DIR/.immutable-hook-warnings"
                fi
                # Detect Collections.unmodifiable* wrappers
                if printf '%s' "$LINE_CONTENT" | grep -qE 'Collections\.unmodifiable(List|Map|Set|Collection|SortedMap|SortedSet)[[:space:]]*\('; then
                  printf '   ⚠️  %s:%s — %.100s\n' "$file" "$CURRENT_LINE" "$TRIMMED" >> "$HOOK_GIT_DIR/.immutable-hook-warnings"
                fi
                ;;
            esac
            # Increment line counter for next added line
            printf '%s' "$((CURRENT_LINE + 1))" > "$HOOK_GIT_DIR/.immutable-hook-line"
            ;;
          -*)
            # Removed lines don't affect new line numbers — skip
            ;;
        esac
      done
      ;;
  esac
done

# Collect warnings from temp file (needed because the while loop runs in a subshell)
if [ -f "$HOOK_GIT_DIR/.immutable-hook-warnings" ]; then
  WARNING_LINES=$(cat "$HOOK_GIT_DIR/.immutable-hook-warnings")
  IMMUTABLE_WARNINGS=$(wc -l < "$HOOK_GIT_DIR/.immutable-hook-warnings" | tr -d ' ')
  rm -f "$HOOK_GIT_DIR/.immutable-hook-warnings"
fi
rm -f "$HOOK_GIT_DIR/.immutable-hook-line"

if [ "$IMMUTABLE_WARNINGS" -gt 0 ]; then
  STAGED_FINGERPRINT=$(git diff --cached --name-only --diff-filter=ACM | sort | portable_hash)

  if [ -f "$MARKER_FILE" ] && [ "$(cat "$MARKER_FILE")" = "$STAGED_FINGERPRINT" ]; then
    rm -f "$MARKER_FILE"
    echo "✅ [prepare-commit-msg] Immutable collection warnings acknowledged — proceeding."
    exit 0
  fi

  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  printf "║  ⚠️  Immutable collection usage detected (%d occurrence(s))     ║\n" "$IMMUTABLE_WARNINGS"
  echo "╚══════════════════════════════════════════════════════════════╝"
  printf '%s\n' "$WARNING_LINES"
  echo ""
  echo "   If these collections will be modified later, consider:"
  echo "      List.of(...)  ->  new ArrayList<>(List.of(...))"
  echo "      Map.of(...)   ->  new HashMap<>(Map.of(...))"
  echo "      Set.of(...)   ->  new HashSet<>(Set.of(...))"
  echo ""
  echo "  ┌────────────────────────────────────────────────────────┐"
  echo "  │  To acknowledge and proceed, simply commit again.     │"
  echo "  │  The warnings have been recorded — next commit will   │"
  echo "  │  pass if no staged files have changed.                │"
  echo "  └────────────────────────────────────────────────────────┘"
  echo ""
  echo "  ❌ Commit aborted — please review immutable collection usage."

  printf '%s' "$STAGED_FINGERPRINT" > "$MARKER_FILE"
  exit 1
fi

rm -f "$MARKER_FILE"
HOOK_EOF

chmod +x "$HOOKS_DIR/prepare-commit-msg" 2>/dev/null || true

# ==============================================================================
# Done
# ==============================================================================
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅  Git hooks installed successfully!                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "  Installed hooks:"
echo "    pre-commit           — Spotless + ESLint auto-fix"
echo "    prepare-commit-msg   — Immutable collection warnings"
echo ""
echo "  Hooks will be skipped during rebase, merge, and cherry-pick."
echo ""
echo "  Prerequisites:"
echo "    Java 21  (JAVA_HOME or auto-detected from ~/.jdks)"
echo "    Node.js >= 22.11.0  (nvm, fnm, or system)"
echo "    ./mvnw (or mvnw.cmd on Windows)"
echo "    yarn in openaev-front/"
echo ""
echo "  Supported platforms: Linux, macOS, Windows (Git Bash)"
echo ""
echo "  To uninstall:"
echo "    rm .git/hooks/pre-commit .git/hooks/prepare-commit-msg"
echo ""

