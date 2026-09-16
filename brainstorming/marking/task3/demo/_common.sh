#!/usr/bin/env bash
#
# Shared plumbing for the marking admin helpers (mark-asset.sh, group-markings.sh).
# Not executable on its own - source it:
#
#     . "$(dirname "$0")/_common.sh"
#
# demo.sh deliberately does NOT use this. A demo you hand to someone else should
# be one self-contained file they can read top to bottom, even at the cost of a
# little duplication.

OPENAEV_URL="${OPENAEV_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-5ccddea0-613c-4a91-a602-6a4eb243d21c}"
TENANT="${TENANT:-2cffad3a-0001-4078-b0e2-ef74274022c3}"

admin=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json")

die() { printf '\033[31m%s\033[0m\n' "$*" >&2; exit 1; }

# Strip an optional [ ... ] wrapper and split on commas, so all of these work:
#
#     TLP:GREEN PAP:RED
#     [TLP:GREEN, PAP:RED]
#     "TLP:GREEN,PAP:RED"
#
# Prints one name per line. Bracket form has to be quoted in most shells anyway,
# but accepting it unquoted too costs nothing and matches how people write a set
# when they are reading the design docs.
normalize_names() {
  printf '%s\n' "$@" \
    | tr -d '[]' \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' \
    | grep -v '^$' || true
}

# Fetch every marking definition in the tenant. Dies with something readable
# rather than letting the caller's python throw a traceback on an error body.
# `|| true` on curl so a connection failure (exit 7) does not trip `set -e`
# before the guard can run.
fetch_definitions() {
  local body
  body="$(curl -s "${admin[@]}" -X POST \
    "${OPENAEV_URL}/api/tenants/${TENANT}/marking-definitions/search" \
    -d '{"page":0,"size":200,"sorts":[{"property":"marking_order","direction":"asc"}]}' || true)"

  echo "$body" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    assert 'content' in d
except Exception:
    sys.exit(1)" 2>/dev/null \
    || die "could not read the marking definitions from ${OPENAEV_URL}.
      Is the app up, and is TOKEN valid? Note MARKING_DEFINITION read currently
      sits under ACCESS_TENANT_SETTINGS. The response was:
${body}"

  printf '%s' "$body"
}

# resolve_markings <json-field-name> <name> [name ...]
#
# Prints two lines: the request payload, then a human label for the resolution.
# Case-insensitive, de-duplicated, and an unknown name lists what does exist -
# that is the mistake people actually make (there is no TLP:ORANGE).
resolve_markings() {
  local field="$1"; shift
  local definitions
  definitions="$(fetch_definitions)"

  MARKINGS="$definitions" FIELD="$field" python3 - "$@" <<'PY'
import json, os, sys

catalog = json.loads(os.environ["MARKINGS"])["content"]
field = os.environ["FIELD"]
by_name = {m["marking_name"].casefold(): m for m in catalog}

ids, labels, missing = [], [], []
for name in sys.argv[1:]:
    hit = by_name.get(name.casefold())
    if hit is None:
        missing.append(name)
        continue
    # The endpoint takes a set; the same id twice is a typo, not an intent.
    if hit["marking_id"] not in ids:
        ids.append(hit["marking_id"])
        labels.append(f'{hit["marking_name"]} ({hit["marking_id"]})')

if missing:
    known = ", ".join(sorted(m["marking_name"] for m in catalog))
    sys.exit(f'unknown marking(s): {", ".join(missing)}\ndefined in this tenant: {known}')

print(json.dumps({field: ids}))
print(", ".join(labels))
PY
}

# Was the single argument the literal "none"? macOS ships bash 3.2, where the
# ${x,,} case-conversion expansions do not exist and fail at runtime with
# "bad substitution" - which `bash -n` does not catch.
is_clear_request() {
  [ $# -eq 1 ] && [ "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')" = "none" ]
}
