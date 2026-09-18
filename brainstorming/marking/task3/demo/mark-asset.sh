#!/usr/bin/env bash
#
# Set the markings carried by one ASSET, as admin, on the default tenant.
#
#   ./mark-asset.sh <asset-id> TLP:GREEN                 # one
#   ./mark-asset.sh <asset-id> TLP:GREEN PAP:RED         # several
#   ./mark-asset.sh <asset-id> "[TLP:GREEN, PAP:RED]"    # bracket form, QUOTED
#   ./mark-asset.sh <asset-id> none                      # clear every marking
#
# NOTE ON THE BRACKET FORM: [TLP:RED] is a glob pattern in zsh (the macOS
# default) and in bash, so it must be QUOTED. Unquoted it fails before this
# script is ever invoked:
#
#     ./mark-asset.sh <id> [TLP:RED]        ->  zsh: no matches found: [TLP:RED]
#     ./mark-asset.sh <id> "[TLP:RED]"      ->  fine
#
# Worse, if a single-character file happens to exist in the current directory,
# zsh matches it and silently passes THAT instead. The plain space-separated
# form has no such hazard, which is why it is listed first.
#
# Marking names are the human ones - TLP:GREEN, PAP:AMBER - resolved to ids here
# so you never paste a UUID. Case-insensitive.
#
# This is the READ side of the model: an asset's markings are what a clearance is
# tested against. Compare group-markings.sh, which sets the clearance itself.
#
# The endpoint replaces the WHOLE set, so this script does too. That is why
# clearing is spelled "none" rather than by omitting the argument - an
# accidental `./mark-asset.sh <id>` must not silently declassify an asset.
#
# Runs as admin on purpose: admin skips marking filtering (isAdminOrBypass), so
# it can always see and re-mark an asset, including one marked above its own
# head. A non-admin caller would additionally hit MarkingEscalationValidator.
#
set -euo pipefail
. "$(dirname "$0")/_common.sh"

[ $# -ge 2 ] || die "usage: $(basename "$0") <asset-id> TLP:GREEN [PAP:RED ...]
       $(basename "$0") <asset-id> \"[TLP:GREEN, PAP:RED]\"   <- brackets MUST be quoted
       $(basename "$0") <asset-id> none                      # clear every marking"

ASSET_ID="$1"; shift

names=()
while IFS= read -r n; do names+=("$n"); done < <(normalize_names "$@")

if is_clear_request "${names[@]+"${names[@]}"}"; then
  PAYLOAD='{"asset_markings":[]}'
  WANTED="(none - clearing every marking)"
else
  [ ${#names[@]} -gt 0 ] || die "no marking names given"
  resolved="$(resolve_markings asset_markings "${names[@]}")" || die "$resolved"
  PAYLOAD="$(echo "$resolved" | head -1)"
  WANTED="$(echo "$resolved" | tail -1)"
fi

echo "asset    ${ASSET_ID}"
echo "markings ${WANTED}"

response="$(curl -s -w '\n%{http_code}' "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/assets/${ASSET_ID}/markings" \
  -d "$PAYLOAD")"
code="$(echo "$response" | tail -1)"
body="$(echo "$response" | sed '$d')"

case "$code" in
  200)
    echo "$body" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'\033[32mOK\033[0m {d[\"asset_name\"]} now carries {len(d.get(\"asset_markings\") or [])} marking(s)')"
    ;;
  403)
    die "403 - refused. A caller may only assign markings inside its own clearance
      (MarkingEscalationValidator), and only markings defined in this tenant."
    ;;
  404)
    die "404 - no such asset in tenant ${TENANT}.
      Note this is also what you get for an asset marked ABOVE your clearance:
      filtering happens below authorization, so the two are indistinguishable
      on purpose. As admin that should not apply, so suspect the id."
    ;;
  *)
    die "HTTP ${code}
${body}"
    ;;
esac
