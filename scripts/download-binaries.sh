#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Download openaev-agent and openaev-implant binaries from JFrog Artifactory.
#
# Usage:
#   ./scripts/download-binaries.sh <BINARY_VERSION> <LOCAL_VERSION>
#
#   BINARY_VERSION  – the JFrog artifact suffix to fetch (e.g. "latest",
#                     "prerelease", or a semver tag like "1.2.3").
#   LOCAL_VERSION   – the version string used in the local file name
#                     (e.g. the git tag). Defaults to BINARY_VERSION.
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

CURL_OPTS=(-L --fail --retry 3 --retry-delay 5 --silent --show-error)

# ---------------------------------------------------------------------------
# helper: download <remote_path> <local_path>
# ---------------------------------------------------------------------------
download() {
  local remote="$1" local_path="$2"
  mkdir -p "$(dirname "$local_path")"
  echo "  ↓ ${local_path}"
  curl "${CURL_OPTS[@]}" -o "$local_path" "$remote"
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

echo ""
echo "✅ All binaries downloaded successfully."

