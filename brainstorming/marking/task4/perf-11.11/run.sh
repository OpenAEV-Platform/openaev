#!/usr/bin/env bash
# 11.11 measurement: for each A (agents per inject), recreate openaev_perf as a clone of the dev
# database, seed it, then measure the statements for an admin and a restricted reader, without and
# with the partial COMPLETE index. Never writes to the dev database itself.
#
# Env: AGENTS ("1 10 50"), N_INJECTS (10000), N_ASSETS (500), RUNS (3), JIT (on), CONTAINER (openaev-dev-pgsql)
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
JIT=${JIT:-on}
OUT="$DIR/results-jit-$JIT"
CONTAINER=${CONTAINER:-openaev-dev-pgsql}
AGENTS=${AGENTS:-"1 10 50"}
N_INJECTS=${N_INJECTS:-10000}
N_ASSETS=${N_ASSETS:-500}
RUNS=${RUNS:-3}
PERF_DB=openaev_perf
ADMIN='perf-green,perf-red'
AMBER='perf-green'
INDEX="CREATE INDEX idx_perf_execution_traces_complete ON execution_traces (execution_inject_status_id)
  INCLUDE (execution_status, execution_agent_id) WHERE execution_action = 'COMPLETE'"

psql_db() { podman exec -i "$CONTAINER" psql -U openaev -X -q -v ON_ERROR_STOP=1 -d "$@"; }

recreate_perf_db() {
  local busy
  busy=$(psql_db postgres -Atc "SELECT count(*) FROM pg_stat_activity WHERE datname = 'openaev'")
  if [ "$busy" != "0" ]; then
    echo "The dev database has $busy open connection(s): stop the backend and pgAdmin sessions first." >&2
    exit 1
  fi
  psql_db postgres -c "DROP DATABASE IF EXISTS $PERF_DB" -c "CREATE DATABASE $PERF_DB TEMPLATE openaev"
}

measure() {
  local file=$1
  for reader in admin amber; do
    local clearance=$ADMIN
    [ "$reader" = amber ] && clearance=$AMBER
    for _ in $(seq "$RUNS"); do
      bash "$DIR/queries.sh" | psql_db "$PERF_DB" -v clearance="$clearance" -v jit="$JIT" >>"$OUT/$file-$reader.txt"
    done
  done
  for _ in $(seq "$RUNS"); do
    psql_db "$PERF_DB" <"$DIR/insert-cost.sql" >>"$OUT/$file-insert.txt"
  done
}

mkdir -p "$OUT"
rm -f "$OUT"/*.txt "$OUT"/summary.tsv
psql_db postgres -Atc "SELECT 'shared_buffers=' || current_setting('shared_buffers') || ' work_mem=' || current_setting('work_mem') || ' ' || version()" >"$OUT/settings.txt"

for a in $AGENTS; do
  echo "== A=$a: clone and seed ($(date +%T))"
  recreate_perf_db
  psql_db "$PERF_DB" -v a="$a" -v n_injects="$N_INJECTS" -v n_assets="$N_ASSETS" \
    <"$DIR/seed.sql" >"$OUT/A$a-seed.log"
  cat "$OUT/A$a-seed.log"
  echo "== A=$a: measure without the index ($(date +%T))"
  measure "A$a-idxoff"
  echo "== A=$a: measure with the index ($(date +%T))"
  psql_db "$PERF_DB" -c "$INDEX" -c "ANALYZE execution_traces"
  measure "A$a-idxon"
done

awk -f "$DIR/summary.awk" "$OUT"/A*-idx*-*.txt >"$OUT/summary.tsv"
echo "== done ($(date +%T)): $OUT/summary.tsv"
column -t -s $'\t' "$OUT/summary.tsv"
