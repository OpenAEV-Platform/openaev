#!/bin/sh
# Retries `yarn install` with exponential backoff and full jitter.
#
# Yarn 4.18.0 dies on an uncatchable got@11 retry race (yarnpkg/berry#7245) when a registry
# request times out, so a failure says nothing about the next attempt. Retrying immediately
# hits the same network conditions; jittered backoff also keeps parallel jobs from resynchronising.
#
# POSIX sh: this also runs under busybox ash inside the Alpine build.
# Usage: sh .github/scripts/yarn-install-retry.sh [extra yarn install args...]
set -u

max_attempts=${YARN_INSTALL_ATTEMPTS:-4}
attempt=1

while true; do
  if yarn install "$@"; then
    exit 0
  fi

  if [ "${attempt}" -ge "${max_attempts}" ]; then
    echo "::error::yarn install failed after ${max_attempts} attempts"
    exit 1
  fi

  window=$(( 5 * (1 << attempt) ))
  if [ "${window}" -gt 60 ]; then
    window=60
  fi
  delay=$(( $(od -An -N2 -tu2 < /dev/urandom | tr -d ' ') % window + 1 ))

  echo "::warning::yarn install attempt ${attempt}/${max_attempts} failed; retrying in ${delay}s"
  sleep "${delay}"
  attempt=$(( attempt + 1 ))
done
