#!/usr/bin/env bash
set -euo pipefail

# Runs the agent in the foreground: no systemd in this container, so no installer

TOKEN="$OPENAEV__TOKEN"
URL="${OPENAEV__URL%/}"
# default tenant identifier
TENANT_ID=2cffad3a-0001-4078-b0e2-ef74274022c3
DIR=/opt/openaev-agent

if [ ! -x "$DIR/openaev-agent" ]; then
  apt-get update && apt-get install -y --no-install-recommends curl ca-certificates
  mkdir -p "$DIR"
  curl -fsSL -H "Authorization: Bearer $TOKEN" \
    "$URL/api/tenants/$TENANT_ID/agent/executable/openaev/linux/$(uname -m)" \
    -o "$DIR/openaev-agent"
  chmod +x "$DIR/openaev-agent"
  curl -fsSL https://raw.githubusercontent.com/OpenAEV-Platform/agent/main/config/default.toml \
    | sed -e "s|^url = .*|url = \"$URL\"|" \
          -e "s|^token = .*|token = \"$TOKEN\"|" \
          -e "s|^tenant_id = .*|tenant_id = \"$TENANT_ID\"|" \
    > "$DIR/openaev-agent-config.toml"
fi

[ -s /etc/machine-id ] || tr -d '-' < /proc/sys/kernel/random/uuid > /etc/machine-id

cd "$DIR"
exec ./openaev-agent