#!/usr/bin/env bash
#
# End-to-end PoC for marking-based access control on assets.
#
# Proves the three flows in one run, against a live dev stack:
#   3.1  group AMBER, asset GREEN  -> the user SEES the asset
#   3.2  group AMBER, asset RED    -> the user DOES NOT see it
#   3.3  admin raises group to RED -> the user sees it again, with no wait
#
# Plus the two guards that make the design safe:
#   3.4  assigning a marking above your own clearance is refused
#   3.5  the caller can never lock themselves out of an asset they just marked
#
# Mutation-checked: re-run with the dimension switched off and 3.2 flips from
# 404 to 200.
#
#   mvn -o -pl openaev-api spring-boot:run -Dspring-boot.run.profiles=dev \
#       -Dspring-boot.run.arguments=--openaev.marking.active-tables=
#
# Read the result honestly: 3.2 is the ONLY assertion that discriminates on its
# own - it is the one that proves rows are actually being filtered. The others
# are consistency checks that hold either way. The 3.2 -> 3.3 pair is what
# proves the cache eviction: the same user, same asset, goes 404 then 200 with
# nothing changing in between except the grant write.
#
# Prerequisites: dev stack up, app running on :8080 with
#   openaev.marking.active-tables=assets
#
set -euo pipefail

OPENAEV_URL="${OPENAEV_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-5ccddea0-613c-4a91-a602-6a4eb243d21c}"
# Resolved from the database rather than hardcoded: a dev stack rebuilt from
# scratch gets a fresh tenant id, and a stale literal here fails as
# TENANT_ACCESS_DENIED - which used to surface as an opaque Python KeyError.
# Override TENANT to pin a specific one.
TENANT="${TENANT:-$(docker exec openaev-dev-pgsql psql -U openaev -d openaev -tA \
  -c "select tenant_id from tenants order by tenant_created_at limit 1" | tr -d '[:space:]')}"
# The vite dev server (`yarn start` in openaev-front, port 3001) is the default
# because it always matches the working tree. :8080 also serves a UI, but only
# whatever was last copied into openaev-front/builder/prod/build - which can be
# months stale. Override FRONT_URL if you want the bundled one.
FRONT_URL="${FRONT_URL:-http://localhost:3001}"
# DEMO=1 stops at each UI checkpoint so you can show the asset list between
# steps. DEMO=0 runs straight through - use it when piping the output, and for
# the mutation check described above.
DEMO="${DEMO:-1}"

admin=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json")
SUFFIX="$(date +%s)"
PASS=0
FAIL=0

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()   { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$*"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$*"; }
check() { [ "$1" = "$2" ] && ok "$3 (got $1)" || bad "$3 (expected $2, got $1)"; }

# Reads from /dev/tty rather than stdin, so the pause still works when the
# script is piped (./demo.sh | tee demo.log). Skipped entirely when
# DEMO=0 or when there is no terminal to read from, so an unattended run can
# never hang. `|| true` because read returns non-zero on EOF and set -e is on.
# The message comes in on stdin (a heredoc), not as an argument. The obvious
# `pause "$(cat <<EOF ... EOF)"` form is a trap: bash re-parses the heredoc body
# while scanning for the closing paren, so a lone apostrophe in the prose - as
# in "the marking's colour" - silently breaks the whole script.
pause() {
  local msg
  msg="$(cat)"
  printf '\n\033[33m>>> %s\033[0m\n' "$msg"
  [ "$DEMO" = "1" ] || return 0
  # `[ -r /dev/tty ]` is not enough: the node exists and looks readable even
  # when there is no controlling terminal to open (cron, CI, a detached shell).
  # Actually opening it is the only reliable probe.
  { exec 3</dev/tty; } 2>/dev/null || return 0
  printf '\033[2m    press Enter to continue\033[0m'
  read -r _ <&3 || true
  exec 3<&-
  echo
}

jqr() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

# --------------------------------------------------------------------------
say "0. Resolving the seeded marking definitions"
echo "  tenant = ${TENANT}"

[ -n "$TENANT" ] || { echo "  ERROR: could not resolve a tenant id (is the dev stack up?)" >&2; exit 1; }

markings="$(curl -s "${admin[@]}" -X POST \
  "${OPENAEV_URL}/api/tenants/${TENANT}/marking-definitions/search" \
  -d '{"page":0,"size":50,"sorts":[{"property":"marking_order","direction":"asc"}]}')"

# Fail with the server's own message rather than letting the JSON parse below
# blow up on an error payload - TENANT_ACCESS_DENIED used to surface as a bare
# KeyError: 'content', which says nothing about the actual cause.
case "$markings" in
  *'"content"'*) ;;
  *) echo "  ERROR: marking-definitions/search did not return a page:" >&2
     echo "         ${markings}" >&2
     echo "         check OPENAEV_URL, TOKEN and TENANT." >&2
     exit 1 ;;
esac

pick() { echo "$markings" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(next(x['marking_id'] for x in d['content'] if x['marking_name']=='$1'))"; }

M_GREEN="$(pick 'TLP:GREEN')"
M_AMBER="$(pick 'TLP:AMBER')"
M_RED="$(pick 'TLP:RED')"
echo "  TLP:GREEN=${M_GREEN}"
echo "  TLP:AMBER=${M_AMBER}"
echo "  TLP:RED  =${M_RED}"

# --------------------------------------------------------------------------
say "1. Creating a non-admin user, a group, and an asset"
# Admin bypasses marking filtering entirely (isAdminOrBypass), so the whole
# demo has to run as a plain user - otherwise every flow trivially "passes".

USER_EMAIL="corinne-poc-marking-${SUFFIX}@openaev.io"
user="$(curl -s "${admin[@]}" -X POST "${OPENAEV_URL}/api/tenants/${TENANT}/users" \
  -d "{\"user_email\":\"${USER_EMAIL}\",\"user_firstname\":\"Poc\",\"user_lastname\":\"Marking\",\"user_admin\":false,\"user_plain_password\":\"c\"}")"
USER_ID="$(echo "$user" | jqr "d['user_id']")"
USER_TOKEN="$(docker exec openaev-dev-pgsql psql -U openaev -d openaev -tA \
  -c "select token_value from tokens where token_user = '${USER_ID}'" | tr -d '[:space:]')"
member=(-H "Authorization: Bearer ${USER_TOKEN}" -H "Content-Type: application/json")
echo "  user  = ${USER_ID} ${USER_EMAIL} (plain password 'c')"

group="$(curl -s "${admin[@]}" -X POST "${OPENAEV_URL}/api/tenants/${TENANT}/groups" \
  -d "{\"group_name\":\"PoC Marking ${SUFFIX}\"}")"
GROUP_ID="$(echo "$group" | jqr "d['group_id']")"
echo "  group = ${GROUP_ID} PoC Marking ${SUFFIX}"

curl -s -o /dev/null "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_ID}/users" \
  -d "{\"group_users\":[\"${USER_ID}\"]}"

# The group needs RBAC on assets, otherwise RBAC - not markings - is what hides
# the row, and every flow below would "pass" for the wrong reason. Manager gives
# ACCESS_ASSETS + MANAGE_ASSETS and, critically, NOT bypass: a bypass user skips
# marking filtering entirely (isAdminOrBypass) and would prove nothing.
roles="$(curl -s "${admin[@]}" -X POST "${OPENAEV_URL}/api/tenants/${TENANT}/roles/search" \
  -d '{"page":0,"size":50}')"
ROLE_MANAGER="$(echo "$roles" | jqr "next(x['role_id'] for x in d['content'] if x['role_name']=='Manager')")"

# Second role: reading marking definitions currently sits under
# ACCESS_TENANT_SETTINGS (Capability.java, MARKING_DEFINITION READ/SEARCH), which
# Manager does not have. Without it the member gets 403 on
# /marking-definitions/search, so the UI cannot turn the ids in asset_markings
# into names and colours and the Markings column renders empty - even though the
# ids are right there in the payload.
#
# This is a PoC shortcut. Markings are reference data that any user who can see a
# marked row needs to read, exactly like tags, which have their own ACCESS_TAGS in
# CapabilityGroup.TAXONOMY. The real fix is a dedicated ACCESS_MARKINGS capability;
# until then the demo grants ACCESS_TENANT_SETTINGS through a throwaway role.
#
# A separate role rather than editing the seeded Manager: Manager is shared by
# every user of this dev database, and widening it here would silently persist
# after the demo and quietly weaken any later test that assumes stock Manager.
role_reader="$(curl -s "${admin[@]}" -X POST "${OPENAEV_URL}/api/tenants/${TENANT}/roles" \
  -d "{\"role_name\":\"PoC Marking Reader ${SUFFIX}\",\"role_capabilities\":[\"ACCESS_TENANT_SETTINGS\"]}")"
ROLE_READER="$(echo "$role_reader" | jqr "d['role_id']")"

curl -s -o /dev/null "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_ID}/roles" \
  -d "{\"group_roles\":[\"${ROLE_MANAGER}\",\"${ROLE_READER}\"]}"
echo "  roles = Manager (ACCESS_ASSETS + MANAGE_ASSETS, no BYPASS)"
echo "        + PoC Marking Reader (ACCESS_TENANT_SETTINGS - lets the UI resolve marking ids)"

ASSET_NAME="poc-marking-${SUFFIX}"
endpoint="$(curl -s "${admin[@]}" -X POST "${OPENAEV_URL}/api/tenants/${TENANT}/endpoints/agentless" \
  -d "{\"asset_name\":\"${ASSET_NAME}\",\"endpoint_hostname\":\"poc-${SUFFIX}\",\"endpoint_ips\":[\"10.0.0.1\"],\"endpoint_platform\":\"Linux\",\"endpoint_arch\":\"x86_64\"}")"
ASSET_ID="$(echo "$endpoint" | jqr "d['asset_id']")"
echo "  asset = ${ASSET_ID} with name: ${ASSET_NAME}"

# ---- UI checkpoint 1 -------------------------------------------------------
pause <<EOF
Log in to the UI as the demo user:

      ${FRONT_URL}
      login    ${USER_EMAIL}
      password c

The group has NO MARKING grant yet and the asset is unmarked, so the asset list
should already show it - an unmarked row is inside every clearance. The
Markings column shows a dash for the same reason.

      Endpoints -> ${ASSET_NAME}
EOF

# --------------------------------------------------------------------------
say "2. Granting the group TLP:AMBER"

curl -s -o /dev/null "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_ID}/markings" \
  -d "{\"group_markings\":[\"${M_AMBER}\"]}"

# The grant is AMBER; the clearance it resolves to also contains GREEN and
# CLEAR. That expansion happens in Java, once per request, so the SQL
# predicate stays a flat containment test with no notion of order.
echo "  granted TLP:AMBER -> clearance covers CLEAR, GREEN, AMBER"

sees() { curl -s -o /dev/null -w '%{http_code}' "${member[@]}" \
  "${OPENAEV_URL}/api/tenants/${TENANT}/endpoints/${ASSET_ID}"; }

mark_asset() { curl -s -o /dev/null -w '%{http_code}' "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/assets/${ASSET_ID}/markings" \
  -d "{\"asset_markings\":[$1]}"; }

# ---- UI checkpoint 2 -------------------------------------------------------
pause <<EOF
The group now grants TLP:AMBER. Go back to the demo user's asset list and check
that ${ASSET_NAME} is still visible.

Nothing should have changed - and that is the point. The grant widened the
user's *clearance*; the asset itself is still unmarked, so it was always
visible. The next steps are what move the asset relative to that clearance.

Watch the Markings column from here on: it renders asset_markings as chips in
the marking's own colour, so the asset visibly turns GREEN, then RED - and at
RED it drops out of the list entirely.

      Endpoints -> ${ASSET_NAME}   (expect: still listed, Markings empty)
EOF

# --------------------------------------------------------------------------
say "3.0 Baseline: unmarked asset is visible"
check "$(sees)" "200" "unmarked asset is visible (empty set is inside every clearance)"

say "3.1 Group AMBER, asset GREEN -> the user SEES it"
check "$(mark_asset "\"${M_GREEN}\"")" "200" "asset marked TLP:GREEN"
check "$(sees)" "200" "GREEN asset visible to an AMBER clearance"
# ---- UI checkpoint ----------------------------------------------------------
pause <<EOF
The asset is now marked TLP:GREEN and the clearance is AMBER. Refresh the demo
user's asset list.

GREEN is inside an AMBER clearance, so the row stays - but the Markings column
now shows a green TLP:GREEN chip. This is the interesting half of the flow: the
asset carries a marking, and the user is still allowed to see it.

      Endpoints -> ${ASSET_NAME}   (expect: listed, chip TLP:GREEN)
EOF


say "3.2 Group AMBER, asset RED -> the user DOES NOT see it"
check "$(mark_asset "\"${M_RED}\"")" "200" "asset marked TLP:RED"
check "$(sees)" "404" "RED asset hidden from an AMBER clearance"
# ---- UI checkpoint ----------------------------------------------------------
pause <<EOF
The asset has just been re-marked TLP:RED. The clearance is still AMBER.

Refresh the demo user's asset list. ${ASSET_NAME} is GONE.

Nothing was deleted and no permission was revoked - the row simply is not in the
result set any more. The filtering happens in SQL, below authorization, so the
user cannot tell the asset exists at all. Fetching it directly returns 404, not
403, for exactly that reason.

Keep the admin window open on the same list: the asset is still there for you.

      Endpoints -> ${ASSET_NAME}   (expect: ABSENT for the user, present for admin)
EOF


say "3.3 Admin raises the group to TLP:RED -> visible again, no wait"
curl -s -o /dev/null "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_ID}/markings" \
  -d "{\"group_markings\":[\"${M_RED}\"]}"
check "$(sees)" "200" "cached clearance was evicted on the grant write - no TTL wait"
# ---- UI checkpoint ----------------------------------------------------------
pause <<EOF
The admin just raised the group grant to TLP:RED. Nothing about the asset
changed.

Refresh the demo user's list. ${ASSET_NAME} is BACK, with a red TLP:RED chip.

Note there was no wait. The resolved clearance is cached per user, and the write
to the grant evicts it immediately - so the very next request already sees the
wider clearance rather than expiring on a TTL.

      Endpoints -> ${ASSET_NAME}   (expect: listed again, chip TLP:RED)
EOF


say "3.4 Guard: a user cannot assign a marking above their own clearance"
# Drop the group back to GREEN, then have the member try to assign RED.
curl -s -o /dev/null "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_ID}/markings" \
  -d "{\"group_markings\":[\"${M_GREEN}\"]}"
curl -s -o /dev/null "${admin[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/assets/${ASSET_ID}/markings" \
  -d '{"asset_markings":[]}'
escalation="$(curl -s -o /dev/null -w '%{http_code}' "${member[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/assets/${ASSET_ID}/markings" \
  -d "{\"asset_markings\":[\"${M_RED}\"]}")"
check "$escalation" "403" "assigning TLP:RED with only a GREEN clearance is refused"
# ---- UI checkpoint ----------------------------------------------------------
pause <<EOF
The group is back to TLP:GREEN and the asset has been cleared of markings. The
demo user then tried to mark the asset TLP:RED - above their own clearance - and
was refused with 403.

This is the guard that stops a user laundering data upward, or hiding an asset
from everyone including themselves. It is enforced server-side, in
MarkingEscalationValidator, so it holds for any caller.

There is nothing to click here: assigning markings to an asset is API-only in
this PoC. The UI reads asset_markings (the Markings column) but has no editor
for them yet - that is deliberate, and out of scope.

      Endpoints -> ${ASSET_NAME}   (expect: listed, Markings empty)
EOF


say "3.5 Guard: the caller cannot lock themselves out"
selfmark="$(curl -s -o /dev/null -w '%{http_code}' "${member[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/assets/${ASSET_ID}/markings" \
  -d "{\"asset_markings\":[\"${M_GREEN}\"]}")"
check "$selfmark" "200" "assigning a marking inside your own clearance succeeds"
check "$(sees)" "200" "the asset you just marked is still visible to you"
# ---- UI checkpoint ----------------------------------------------------------
pause <<EOF
Last flow: the demo user marked the asset TLP:GREEN - inside their own clearance
- and it worked.

Refresh their list: the asset is still there, now carrying the chip for the
marking they just applied through the API. Self-lockout is impossible by construction: the validator only accepts
markings that are a subset of your clearance, and a row is visible exactly when
its markings are a subset of your clearance. The same set on both sides.

      Endpoints -> ${ASSET_NAME}   (expect: listed, chip TLP:GREEN)
EOF


# --------------------------------------------------------------------------
# Read both sides of the containment test straight out of Postgres, bypassing the
# API. Everything above infers marking state from HTTP status codes; a silently
# broken write path could produce the same 200/404 sequence with nothing actually
# persisted. This is the independent check.
#
# The visibility rule is: row_markings SUBSET OF clearance. So the two things
# worth printing are the row's own markings and the grant the clearance is
# resolved from - they are opposite sides of the same test, not the same thing.
say "4. Both sides of the containment test, read directly from Postgres"
docker exec openaev-dev-pgsql psql -U openaev -d openaev -tA -c "
  select 'row markings (assets.marking_ids)  : ' ||
         coalesce((select string_agg(m.marking_name, ', ' order by m.marking_order)
                   from marking_definitions m
                   where m.marking_id = any(a.marking_ids)), '(none)')
  from assets a where a.asset_id = '${ASSET_ID}'
  union all
  select 'group grant (groups_markings)      : ' ||
         coalesce(string_agg(m.marking_name, ', ' order by m.marking_order), '(none)')
  from groups_markings gm
  join marking_definitions m on m.marking_id = gm.marking_id
  where gm.group_id = '${GROUP_ID}'"
echo "  the grant expands downward in Java (AMBER also grants GREEN and CLEAR);"
echo "  the SQL predicate itself is a flat subset test with no notion of order."

say "Result: ${PASS} passed, ${FAIL} failed"
[ "$FAIL" -eq 0 ]
