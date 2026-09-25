#!/usr/bin/env bash
#
# Set the markings a GROUP grants its members, as admin, on the default tenant.
#
#   ./group-markings.sh <group-id> TLP:GREEN                # one
#   ./group-markings.sh <group-id> TLP:GREEN PAP:RED        # several
#   ./group-markings.sh <group-id> "[TLP:GREEN, PAP:RED]"   # bracket form, QUOTED
#   ./group-markings.sh <group-id> none                     # revoke every grant
#
# NOTE ON THE BRACKET FORM: [TLP:RED] is a glob pattern in zsh (the macOS
# default) and in bash, so it must be QUOTED. Unquoted it fails before this
# script is ever invoked:
#
#     ./group-markings.sh <id> [TLP:RED]        ->  zsh: no matches found: [TLP:RED]
#     ./group-markings.sh <id> "[TLP:RED]"      ->  fine
#
# Worse, if a single-character file happens to exist in the current directory,
# zsh matches it and silently passes THAT instead. The plain space-separated
# form has no such hazard, which is why it is listed first.
#
# This is the WRITE side of the model: a group's grants are what every member's
# clearance is resolved from. Compare mark-asset.sh, which marks the rows that
# clearance is then tested against.
#
# Two things worth knowing about what this does downstream:
#
#   * The grant EXPANDS DOWNWARD when it is resolved. Granting TLP:AMBER yields a
#     clearance covering AMBER, GREEN and CLEAR. The expansion happens in Java,
#     once per request, so the SQL predicate stays a flat subset test with no
#     notion of order. Do not grant the lower ones by hand.
#
#   * Every member's cached clearance is EVICTED on write, so the change lands on
#     their very next request - no TTL to wait out. That is what demo.sh flow 3.3
#     proves: the same user, same asset, goes 404 then 200 with nothing changing
#     in between except this call.
#
# Replaces the WHOLE set, like the sibling users and roles endpoints, so clearing
# is spelled "none" rather than by omitting the argument: an accidental
# `./group-markings.sh <id>` must not silently revoke a group's clearance.
#
set -euo pipefail
. "$(dirname "$0")/_common.sh"

[ $# -ge 2 ] || die "usage: $(basename "$0") <group-id> TLP:GREEN [PAP:RED ...]
       $(basename "$0") <group-id> \"[TLP:GREEN, PAP:RED]\"   <- brackets MUST be quoted
       $(basename "$0") <group-id> none                      # revoke every grant"

GROUP_ID="$1"; shift

names=()
while IFS= read -r n; do names+=("$n"); done < <(normalize_names "$@")

if is_clear_request "${names[@]+"${names[@]}"}"; then
  PAYLOAD='{"group_markings":[]}'
  WANTED="(none - revoking every grant)"
else
  [ ${#names[@]} -gt 0 ] || die "no marking names given"
  resolved="$(resolve_markings group_markings "${names[@]}")" || die "$resolved"
  PAYLOAD="$(echo "$resolved" | head -1)"
  WANTED="$(echo "$resolved" | tail -1)"
fi

echo "group  ${GROUP_ID}"
echo "grants ${WANTED}"

response="$(curl -s -w '\n%{http_code}' "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_ID}/markings" \
  -d "$PAYLOAD")"
code="$(echo "$response" | tail -1)"
body="$(echo "$response" | sed '$d')"

case "$code" in
  200)
    echo "$body" | python3 -c "
import sys, json
d = json.load(sys.stdin)
n = len(d.get('group_markings') or [])
print(f'\033[32mOK\033[0m {d[\"group_name\"]} now grants {n} marking(s); member clearances evicted')"
    ;;
  403)
    die "403 - refused. A caller may only grant markings inside its own clearance
      (MarkingEscalationValidator), and only markings defined in this tenant.
      Note granting a LOWER marking than you hold is allowed: you can already
      read those rows, so granting them discloses nothing new."
    ;;
  404)
    die "404 - no such group in tenant ${TENANT}."
    ;;
  *)
    die "HTTP ${code}
${body}"
    ;;
esac
