#!/usr/bin/env bash
# Fake OpenAEV agent for local marking tests (default tenant).
#
#   Token and URL come from openaev-dev/.env (OPENAEV_TOKEN, OPENAEV_URL); OAEV_TOKEN / OAEV_URL override them.
#   ./fake-agent.sh register [name]              # create (or refresh) the fake endpoint + agent
#   ./fake-agent.sh status   <injectId>          # show the inject status (wait for PENDING)
#   ./fake-agent.sh complete <injectId> [STATUS] # send the agent's COMPLETE trace (default EXECUTED)
#
# STATUS: any ExecutionTraceStatus, e.g. EXECUTED, ERROR, TIMEOUT, COMMAND_NOT_FOUND.
set -euo pipefail

ENV_FILE="$(cd "$(dirname "$0")/../../.." && pwd)/openaev-dev/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

URL="${OAEV_URL:-${OPENAEV_URL:-http://localhost:8080}}"
TENANT="${OAEV_TENANT:-2cffad3a-0001-4078-b0e2-ef74274022c3}"
TOKEN="${OAEV_TOKEN:-${OPENAEV_TOKEN:?no OPENAEV_TOKEN in $ENV_FILE, export OAEV_TOKEN}}"
STATE_FILE="${OAEV_FAKE_AGENT_FILE:-$HOME/.oaev-fake-agent}"
API="$URL/api/tenants/$TENANT"

call() { # method path [json]
  curl -sS --fail-with-body -X "$1" "$2" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    ${3:+-d "$3"}
}

agent_id() {
  [[ -s "$STATE_FILE" ]] || { echo "No fake agent yet: run '$0 register' first" >&2; exit 1; }
  cat "$STATE_FILE"
}

register() {
  local name="${1:-fake-green}"
  # The agent version must match the platform's, or registration queues an upgrade job
  local version
  version=$(curl -sS "$URL/api/settings" -H "Authorization: Bearer $TOKEN" | jq -r '.platform_version')
  # A MAC (and IP) of its own, stable per name: a new agent joins the endpoint of any matching MAC,
  # so a shared one would put every fake agent on the first one's endpoint
  local hash mac ip
  hash=$(printf '%s' "$name" | cksum | cut -d' ' -f1)
  mac=$(printf '02:66:%02x:%02x:%02x:%02x' $(((hash >> 24) & 255)) $(((hash >> 16) & 255)) \
    $(((hash >> 8) & 255)) $((hash & 255)))
  ip="10.66.$(((hash >> 8) & 255)).$((hash % 254 + 1))"
  # Registering again refreshes last seen: an agent unseen for 1h is inactive and never dispatched
  local endpoint
  endpoint=$(call POST "$API/endpoints/register" "$(jq -n --arg n "$name" --arg v "$version" \
    --arg mac "$mac" --arg ip "$ip" '{
    asset_name: $n,
    asset_external_reference: ("ext-" + $n),
    asset_hostname: $n,
    asset_ips: [$ip],
    asset_mac_addresses: [$mac],
    endpoint_platform: "Linux",
    endpoint_arch: "x86_64",
    endpoint_agent_version: $v,
    agent_executed_by_user: "root",
    agent_is_service: true,
    agent_is_elevated: true
  }')")
  local id
  id=$(jq -r '.asset_agents[0] | if type == "object" then .agent_id else . end' <<<"$endpoint")
  echo "$id" >"$STATE_FILE"
  echo "endpoint: $(jq -r '.asset_id' <<<"$endpoint")  ($name, mark it in the UI)"
  echo "agent:    $id  (saved in $STATE_FILE)"
}

status() {
  call GET "$API/injects/status?injectId=$1" | jq '{status_name, tracking_sent_date, tracking_end_date}'
}

complete() {
  local inject="$1" trace_status="${2:-EXECUTED}" agent
  agent=$(agent_id)
  local current
  current=$(call GET "$API/injects/status?injectId=$inject" | jq -r '.status_name')
  if [[ "$current" != "PENDING" ]]; then
    echo "Inject is $current, not PENDING: a COMPLETE now is refused (409). Retry in a few seconds." >&2
    exit 1
  fi
  call POST "$API/injects/execution/$agent/callback/$inject" "$(jq -n --arg s "$trace_status" '{
    execution_message: ("fake agent: " + $s),
    execution_status: $s,
    execution_action: "complete",
    execution_duration: 1000
  }')"
  echo "COMPLETE $trace_status sent for agent $agent"
  status "$inject"
}

case "${1:-}" in
  register) register "${2:-}" ;;
  status) status "${2:?injectId}" ;;
  complete) complete "${2:?injectId}" "${3:-EXECUTED}" ;;
  *) sed -n '2,9p' "$0"; exit 1 ;;
esac
