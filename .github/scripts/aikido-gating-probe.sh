#!/usr/bin/env bash
# Temporary probe: scan the CI images in Aikido's release and PR gating modes, next to the
# ephemeral scan, and check whether the scanner token can read the public REST API.
# Every output lands in aikido-gating-probe/ for the artifact. Never fails.
set -uo pipefail

SCANNER=aikidosecurity/local-scanner:v1.0.163@sha256:c96405a39c65070db69d2bc22fee697415448b8e1ef9650022b92991a825d4bc
OUT=aikido-gating-probe
mkdir -p "$OUT"

scan() {
  local label=$1 image=$2
  shift 2
  echo "::group::$label: image-scan $image $*"
  local start=$SECONDS
  docker run --rm \
    -e AIKIDO_API_KEY \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$PWD/$OUT:/report" \
    "$SCANNER" \
    image-scan "$image" "$@" > "$OUT/$label.txt" 2>&1
  local code=$?
  cat "$OUT/$label.txt"
  echo "::endgroup::"
  echo "$label: exit $code in $((SECONDS - start))s" | tee -a "$OUT/exit-codes.txt"
}

scan release-standard openaev:ci --image-name openaev-ci-standard --fail-on high \
  --gating-result-output /report/release-standard.json --output-image-metadata /report/meta-standard.json
scan release-ubi9 openaev-ubi9:ci --image-name openaev-ci-ubi9 --fail-on high \
  --gating-result-output /report/release-ubi9.json --output-image-metadata /report/meta-ubi9.json

if [ -n "${BASE_SHA:-}" ] && [ -n "${HEAD_SHA:-}" ]; then
  scan pr-standard openaev:ci --image-name openaev-ci-standard --fail-on high --gating-mode pr \
    --base-commit-id "$BASE_SHA" --head-commit-id "$HEAD_SHA" --gating-result-output /report/pr-standard.json
fi

for json in "$OUT"/*.json; do
  [ -e "$json" ] || continue
  echo "::group::$json"
  cat "$json"
  echo
  echo "::endgroup::"
done

# The public API documents OAuth client credentials only: try the scanner token as a bearer token.
api() {
  local label=$1 path=$2
  local code
  code=$(curl -sS -o "$OUT/api-$label.json" -w '%{http_code}' \
    -H "Authorization: Bearer $AIKIDO_API_KEY" -H "Accept: application/json" \
    "https://app.aikido.dev/api/public/v1/$path")
  echo "GET /api/public/v1/$path -> HTTP $code" | tee -a "$OUT/api-status.txt"
  head -c 2000 "$OUT/api-$label.json"
  echo
}
api containers "containers?filter_name=openaev-ci-standard&filter_status=all"
api issues "issues/export?filter_container_repo_name=openaev-ci-standard&filter_status=all&per_page=100"
exit 0
