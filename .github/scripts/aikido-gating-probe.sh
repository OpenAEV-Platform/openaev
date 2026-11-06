#!/usr/bin/env bash
# Temporary probe: scan the CI images in Aikido's release and PR gating modes, next to the
# ephemeral scan, on the CI images and on an old release.
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

# A five-month-old release, to see the gate fail and what its result file holds, next to the
# ephemeral findings of the same image.
OLD=openaev/platform:2.260525.0
docker pull -q "$OLD"
scan release-old "$OLD" --image-name openaev-ci-probe-old --fail-on high \
  --gating-result-output /report/release-old.json
echo "::group::ephemeral-old"
docker run --rm -e AIKIDO_API_KEY -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD/$OUT:/report" \
  "$SCANNER" ephemeral-image-scan "$OLD" --include-unfixed --result-output /report/ephemeral-old.json \
  > "$OUT/ephemeral-old.txt" 2>&1
echo "ephemeral-old: exit $?" | tee -a "$OUT/exit-codes.txt"
echo "::endgroup::"
cat "$OUT/release-old.txt"
[ -e "$OUT/release-old.json" ] && cat "$OUT/release-old.json"
exit 0
