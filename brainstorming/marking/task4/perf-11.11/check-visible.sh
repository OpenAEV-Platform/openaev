#!/usr/bin/env bash
# Stored status against the reader's status computed by the SQL expression, per clearance, on the
# seeded injects of openaev_perf: the admin must read the stored status everywhere.
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
VISIBLE=$(bash "$DIR/queries.sh" --visible)
for clearance in perf-green,perf-red perf-green perf-red; do
  echo "== clearance $clearance"
  podman exec -i "${CONTAINER:-openaev-dev-pgsql}" psql -U openaev -X -q -d openaev_perf <<SQL
BEGIN;
SET LOCAL app.current_markings = '$clearance';
SELECT s.status_name AS stored, s.status_expected_agent_count AS expected, $VISIBLE AS visible,
       count(*) AS injects
FROM injects_statuses s
WHERE s.status_id LIKE 'perf-status-%'
GROUP BY 1, 2, 3
ORDER BY 1, 2, 3;
ROLLBACK;
SQL
done
