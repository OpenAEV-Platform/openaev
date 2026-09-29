#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Download openaev-agent and openaev-implant binaries from JFrog Artifactory.
# Each downloaded artifact also gets a matching `.sha256` sidecar file in the
# same directory so the checksum can be reused later as the source of truth.
#
# Usage:
#   ./scripts/download-binaries.sh <BINARY_VERSION> <LOCAL_VERSION>
#
#   BINARY_VERSION  – the JFrog artifact suffix to fetch (e.g. "latest",
#                     "prerelease", or a semver tag like "1.2.3").
#   LOCAL_VERSION   – the version string used in the local file name
#                     (e.g. the git tag). Defaults to BINARY_VERSION.
#
# Environment:
#   AGENT_IMPLANT_SIGNATURE_PRIVATE_KEY – optional RSA private key (PEM). When
#                     set, each artifact is signed (RSA / SHA-256) and the
#                     base64 signature is written to a `.sig` sidecar.
#                     When unset, signing is skipped.
# ---------------------------------------------------------------------------
set -euo pipefail

BINARY_VERSION="${1:?Usage: $0 <BINARY_VERSION> [LOCAL_VERSION]}"
LOCAL_VERSION="${2:-$BINARY_VERSION}"

JFROG_BASE="https://filigran.jfrog.io/artifactory"
AGENT_REMOTE="openaev-agent"
IMPLANT_REMOTE="openaev-implant"

RESOURCES="openaev-api/src/main/resources"
AGENT_LOCAL="${RESOURCES}/agents/openaev-agent"
IMPLANT_LOCAL="${RESOURCES}/implants/openaev-implant"

# Each artifact is a separate request that costs ~1s of latency and almost no
# bandwidth, so fetching them concurrently turns ~40s into a few seconds.
PARALLELISM="${DOWNLOAD_PARALLELISM:-8}"
QUEUE="$(mktemp)"
SIGNING_KEY_FILE=""
trap 'rm -f "$QUEUE" ${SIGNING_KEY_FILE:+"$SIGNING_KEY_FILE"}' EXIT

# The key is written to a private temp file because openssl reads keys from
# files; it is removed on exit.
if [[ -n "${AGENT_IMPLANT_SIGNATURE_PRIVATE_KEY:-}" ]]; then
  SIGNING_KEY_FILE="$(mktemp)"
  chmod 600 "$SIGNING_KEY_FILE"
  printf '%s\n' "$AGENT_IMPLANT_SIGNATURE_PRIVATE_KEY" > "$SIGNING_KEY_FILE"
  if ! openssl rsa -in "$SIGNING_KEY_FILE" -check -noout > /dev/null 2>&1; then
    echo "❌ AGENT_IMPLANT_SIGNATURE_PRIVATE_KEY is not a valid RSA private key" >&2
    exit 1
  fi
else
  echo "⚠️ AGENT_IMPLANT_SIGNATURE_PRIVATE_KEY not set, checksums will not be signed"
fi
export SIGNING_KEY_FILE

# ---------------------------------------------------------------------------
# helper: download <remote_path> <local_path>  (queued, fetched at the end)
# ---------------------------------------------------------------------------
download() {
  printf '%s %s\n' "$1" "$2" >> "$QUEUE"
}

fetch_one() {
  # Declared here, not at top level: bash cannot export arrays, and this runs in
  # the child bash that xargs spawns.
  local curl_opts=(-L --fail --retry 3 --retry-delay 5 --silent --show-error)
  local checksum_file
  mkdir -p "$(dirname "$2")"
  if ! curl "${curl_opts[@]}" -o "$2" "$1"; then
    echo "  ✗ FAILED  $1" >&2
    return 1
  fi
  checksum_file="${2}.sha256"
  if ! sha256sum "$2" > "$checksum_file"; then
    echo "  ✗ CHECKSUM FAILED  $2" >&2
    return 1
  fi
  echo "  ↓ $2"
  echo "  # $checksum_file"
  if [[ -n "$SIGNING_KEY_FILE" ]]; then
    # Sign the binary itself, so it can be verified on any machine with:
    #   openssl dgst -sha256 -verify pub.pem -signature <(base64 -d file.sig) file
    if ! openssl dgst -sha256 -sign "$SIGNING_KEY_FILE" "$2" \
        | openssl base64 -A > "${2}.sig"; then
      echo "  ✗ SIGNATURE FAILED  $2" >&2
      return 1
    fi
    echo "  ✎ ${2}.sig"
  fi
}
export -f fetch_one

run_queue() {
  local count
  count=$(wc -l < "$QUEUE")
  echo ""
  echo "Fetching ${count} artifact(s), ${PARALLELISM} at a time..."
  if ! xargs -a "$QUEUE" -P "$PARALLELISM" -n 2 bash -c 'set -o pipefail; fetch_one "$0" "$1"'; then
    echo "❌ One or more downloads failed" >&2
    exit 1
  fi
}

echo "══════════════════════════════════════════════════════════════"
echo " Downloading binaries  (remote: ${BINARY_VERSION}, local: ${LOCAL_VERSION})"
echo "══════════════════════════════════════════════════════════════"

# ── openaev-agent ─────────────────────────────────────────────────
echo ""
echo "── openaev-agent ──"

# Linux binaries
download "${JFROG_BASE}/${AGENT_REMOTE}/linux/arm64/openaev-agent-${BINARY_VERSION}" \
         "${AGENT_LOCAL}/linux/arm64/openaev-agent-${LOCAL_VERSION}"
download "${JFROG_BASE}/${AGENT_REMOTE}/linux/x86_64/openaev-agent-${BINARY_VERSION}" \
         "${AGENT_LOCAL}/linux/x86_64/openaev-agent-${LOCAL_VERSION}"

# Linux shell scripts
for script in installer installer-service-user installer-session-user \
              upgrade upgrade-service-user upgrade-session-user; do
  download "${JFROG_BASE}/${AGENT_REMOTE}/linux/openaev-agent-${script}-${BINARY_VERSION}.sh" \
           "${AGENT_LOCAL}/linux/openaev-agent-${script}-${LOCAL_VERSION}.sh"
done

# macOS binaries
download "${JFROG_BASE}/${AGENT_REMOTE}/macos/arm64/openaev-agent-${BINARY_VERSION}" \
         "${AGENT_LOCAL}/macos/arm64/openaev-agent-${LOCAL_VERSION}"
download "${JFROG_BASE}/${AGENT_REMOTE}/macos/x86_64/openaev-agent-${BINARY_VERSION}" \
         "${AGENT_LOCAL}/macos/x86_64/openaev-agent-${LOCAL_VERSION}"

# macOS shell scripts
for script in installer installer-service-user installer-session-user \
              upgrade upgrade-service-user upgrade-session-user; do
  download "${JFROG_BASE}/${AGENT_REMOTE}/macos/openaev-agent-${script}-${BINARY_VERSION}.sh" \
           "${AGENT_LOCAL}/macos/openaev-agent-${script}-${LOCAL_VERSION}.sh"
done

# Windows binaries (arm64)
for suffix in "" "-installer" "-installer-service-user" "-installer-session-user"; do
  download "${JFROG_BASE}/${AGENT_REMOTE}/windows/arm64/openaev-agent${suffix}-${BINARY_VERSION}.exe" \
           "${AGENT_LOCAL}/windows/arm64/openaev-agent${suffix}-${LOCAL_VERSION}.exe"
done

# Windows binaries (x86_64)
for suffix in "" "-installer" "-installer-service-user" "-installer-session-user"; do
  download "${JFROG_BASE}/${AGENT_REMOTE}/windows/x86_64/openaev-agent${suffix}-${BINARY_VERSION}.exe" \
           "${AGENT_LOCAL}/windows/x86_64/openaev-agent${suffix}-${LOCAL_VERSION}.exe"
done

# Windows PowerShell scripts
for script in installer installer-service-user installer-session-user \
              upgrade upgrade-service-user upgrade-session-user; do
  download "${JFROG_BASE}/${AGENT_REMOTE}/windows/openaev-agent-${script}-${BINARY_VERSION}.ps1" \
           "${AGENT_LOCAL}/windows/openaev-agent-${script}-${LOCAL_VERSION}.ps1"
done

# ── openaev-implant ───────────────────────────────────────────────
echo ""
echo "── openaev-implant ──"

for os in linux macos; do
  for arch in arm64 x86_64; do
    download "${JFROG_BASE}/${IMPLANT_REMOTE}/${os}/${arch}/openaev-implant-${BINARY_VERSION}" \
             "${IMPLANT_LOCAL}/${os}/${arch}/openaev-implant-${LOCAL_VERSION}"
  done
done

for arch in arm64 x86_64; do
  download "${JFROG_BASE}/${IMPLANT_REMOTE}/windows/${arch}/openaev-implant-${BINARY_VERSION}.exe" \
           "${IMPLANT_LOCAL}/windows/${arch}/openaev-implant-${LOCAL_VERSION}.exe"
done

run_queue

echo ""
echo "✅ All binaries downloaded successfully."

