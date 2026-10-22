#!/usr/bin/env bash
# ==============================================================================
# OpenAEV — Linux OpenAEV Agent Auto Installer for dev environment
# ==============================================================================

set -euo pipefail

echo "Running startup tasks..."
apt-get update
apt-get install -y --no-install-recommends curl ca-certificates
rm -rf /var/lib/apt/lists/*

TOKEN="$OPENAEV__TOKEN"
URL="${OPENAEV__URL%/}"
DEFAULT_TENANT_ID=2cffad3a-0001-4078-b0e2-ef74274022c3
INSTALL_DIR="$HOME/.local/openaev-agent-session"
SCRIPT_INSTALL=/tmp/openaev-install.sh

echo "Downloading the agent installer from $URL..."
# -f turns HTTP errors (401, 404...) into a non-zero exit code, -S still prints them with -s
if ! curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$URL/api/tenants/$DEFAULT_TENANT_ID/agent/installer/openaev/linux/session-user" \
  -o "$SCRIPT_INSTALL"; then
  echo "Fatal: cannot download the installer from $URL (backend down or OPENAEV_ADMIN_TOKEN wrong?)" >&2
  exit 1
fi

# The installer embeds openaev.agent-url (http://localhost:8080 in dev), which points at the
# container itself, not the backend: use the URL this container can reach instead
AGENT_URL="$(sed -n 's/^base_url=//p' "$SCRIPT_INSTALL")"
sed -i "s|$AGENT_URL|$URL|g" "$SCRIPT_INSTALL"
# The installer downloads the agent binary without a token, but the endpoint is gated behind
# AGENT_INSTALLER since f2dae3b5c and answers 401: authenticate that download
sed -i "/base_url/s|curl |curl -H \"Authorization: Bearer $TOKEN\" |" "$SCRIPT_INSTALL"

# No systemd in this container: fake it so the installer goes through, the agent is run in
# the foreground below instead of as a systemd user service
cat > /usr/local/bin/systemctl <<'EOF'
#!/bin/sh
case "$*" in *is-system-running*) echo running ;; esac
exit 0
EOF
chmod +x /usr/local/bin/systemctl
# The agent identifies the endpoint by its machine id (ResultMidError without it), which
# systemd would normally create: generate one, kept as long as the container is not recreated
[ -s /etc/machine-id ] || tr -d '-' < /proc/sys/kernel/random/uuid > /etc/machine-id

sh "$SCRIPT_INSTALL"

# The agent is the container's main process: its logs go to `docker logs`, and the container
# stops if the agent dies
cd "$INSTALL_DIR"
exec ./openaev-agent
