#!/bin/sh
# Runs a command, retrying with exponential backoff and full jitter.
#
# Two CI failure modes motivated this, both network-bound and both uncatchable by the
# command itself: Yarn 4.18.0 dies on a got@11 retry race (yarnpkg/berry#7245) when a
# registry request times out, and Maven intermittently cannot resolve a parent POM.
# A failure therefore says nothing about the next attempt. Retrying immediately hits the
# same conditions; jitter also keeps parallel jobs from resynchronising into one burst.
#
# Waiting is free when the command succeeds, and a lost job costs a full pipeline rerun,
# so callers should size attempts against their job's timeout-minutes, not against a guess
# at how long an incident lasts.
#
# POSIX sh: also runs under busybox ash inside the Alpine build.
#
# Env: RETRY_ATTEMPTS (total tries), RETRY_BASE / RETRY_CAP (seconds), RETRY_LABEL (message text).
# Usage: sh .github/scripts/retry-with-backoff.sh <command> [args...]
set -u

attempts=${RETRY_ATTEMPTS:-6}
base=${RETRY_BASE:-10}
cap=${RETRY_CAP:-90}
label=${RETRY_LABEL:-$1}
attempt=1

while true; do
  if "$@"; then
    exit 0
  fi

  if [ "${attempt}" -ge "${attempts}" ]; then
    echo "::error::${label} failed after ${attempts} attempts"
    exit 1
  fi

  window=$(( base * (1 << attempt) ))
  if [ "${window}" -gt "${cap}" ]; then
    window=${cap}
  fi
  delay=$(( $(od -An -N2 -tu2 < /dev/urandom | tr -d ' ') % window + 1 ))

  echo "::warning::${label} attempt ${attempt}/${attempts} failed; retrying in ${delay}s"
  sleep "${delay}"
  attempt=$(( attempt + 1 ))
done
