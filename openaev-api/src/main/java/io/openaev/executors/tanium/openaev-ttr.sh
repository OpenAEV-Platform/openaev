#!/bin/sh
# =============================================================================
# openaev-ttr.sh — OpenAEV Tanium TTR Payload Launcher (Linux / macOS)
# =============================================================================
# Purpose:
#   Execute an OpenAEV inject payload OUTSIDE the Tanium process tree so that
#   Tanium Threat Response (TTR) can detect it.  Mirrors the logic of the
#   Windows counterpart openaev-ttr.ps1 which uses a Scheduled Task.
#
# Usage (called by the Tanium package):
#   /bin/sh openaev-ttr.sh <url-encoded-base64-payload>
#
# Detach strategy (first available wins):
#   1. nohup/setsid  – double-fork, reparented to PID 1 (most portable)
#   2. systemd-run   – transient service under PID 1 (Linux with systemd)
#   3. at now        – spawned by atd (if installed)
#   4. (fallback)    – direct execution, not detached
# =============================================================================

set -eu

# ── 0. Validate input ────────────────────────────────────────────────────────
if [ -z "${1:-}" ]; then
  echo "[openaev-ttr] ERROR: no payload argument supplied" >&2
  exit 1
fi

# ── 1. URL-decode ─────────────────────────────────────────────────────────────
# Tanium URL-encodes the base64 payload; restore the 3 non-alphanumeric
# characters of standard base64: + = /
# Note: [bB]/[dD]/[fF] bracket notation is used instead of sed's 'i' flag
# because the 'i' flag is a GNU extension not available on macOS/BSD sed.
DECODED=$(echo -n "$1" \
  | sed -e 's#%2[bB]#+#g' -e 's#%3[dD]#=#g' -e 's#%2[fF]#/#g')

# ── 2. Base64-decode ──────────────────────────────────────────────────────────
PAYLOAD=$(echo -n "$DECODED" | base64 -d 2>/dev/null) \
  || { echo "[openaev-ttr] ERROR: base64 decode failed" >&2; exit 1; }

# ── 3. Write payload to a temp script ─────────────────────────────────────────
umask 077
PAYLOAD_SCRIPT=$(mktemp /tmp/openaev-payload-XXXXXXXXXX.sh) \
  || { echo "[openaev-ttr] ERROR: mktemp failed" >&2; exit 1; }
TASK_ID=$(basename "$PAYLOAD_SCRIPT" .sh)

# Ensure cleanup if the script is interrupted before detach
trap 'rm -f "$PAYLOAD_SCRIPT"' EXIT INT TERM

{
  echo '#!/bin/sh'
  printf '%s\n' "$PAYLOAD"
} > "$PAYLOAD_SCRIPT"

chmod 700 "$PAYLOAD_SCRIPT"

# ── 4. Detach from Tanium process tree ────────────────────────────────────────
# Cleanup via trap so it runs even if the payload calls exit or exec.
WRAPPED_CMD="trap 'rm -f \"${PAYLOAD_SCRIPT}\"' EXIT INT TERM; /bin/sh '${PAYLOAD_SCRIPT}'"

detach_with_nohup() {
  command -v nohup >/dev/null 2>&1 || return 1
  if command -v setsid >/dev/null 2>&1; then
    nohup setsid /bin/sh -c "$WRAPPED_CMD" >/dev/null 2>&1 &
  else
    # macOS: no setsid, double-fork via subshell
    ( nohup /bin/sh -c "$WRAPPED_CMD" >/dev/null 2>&1 & ) &
  fi
  LAUNCHER_PID=$!
  # Brief pause to let the launcher start; if it exits non-zero, the spawn failed.
  sleep 1
  if ! kill -0 "$LAUNCHER_PID" 2>/dev/null && ! wait "$LAUNCHER_PID" 2>/dev/null; then
    return 1
  fi
}

detach_with_systemd() {
  # Requires elevated privileges (Tanium agent runs as root)
  command -v systemd-run >/dev/null 2>&1 || return 1
  # --wait makes systemd-run block until the transient unit exits, giving us a real exit code.
  systemd-run --quiet --wait /bin/sh -c "$WRAPPED_CMD" 2>/dev/null
}

detach_with_at() {
  command -v at >/dev/null 2>&1 || return 1
  echo "$WRAPPED_CMD" | at now 2>/dev/null
}

# Try methods from most common to least common.
# nohup is POSIX-mandated and expected to always be available.
if detach_with_nohup; then
  echo "[openaev-ttr] Payload launched via nohup (task: $TASK_ID)"
elif detach_with_systemd; then
  echo "[openaev-ttr] Payload launched via systemd-run (task: $TASK_ID)"
elif detach_with_at; then
  echo "[openaev-ttr] Payload scheduled via at (task: $TASK_ID)"
else
  # Fallback: direct execution (not detached from Tanium process tree)
  echo "[openaev-ttr] WARN: all detach methods failed, executing directly (task: $TASK_ID)" >&2
  /bin/sh -c "$WRAPPED_CMD"
fi

# Disarm the main-script trap — the detached process handles its own cleanup
trap - EXIT INT TERM
exit 0
