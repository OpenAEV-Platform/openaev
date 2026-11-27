#!/usr/bin/env bash
# Prints the measured statements, for psql with -v clearance=<marking ids, comma-separated> -v jit=<on|off>.
# Each one runs in its own transaction with the reader's GUC, like a request of the app.
set -euo pipefail

TENANT='2cffad3a-0001-4078-b0e2-ef74274022c3'
CLOSED="'EXECUTED','PARTIAL','ERROR','MAYBE_PREVENTED','MAYBE_PARTIAL_PREVENTED'"
ERR="'ERROR','COMMAND_NOT_FOUND','COMMAND_CANNOT_BE_EXECUTED','PREREQUISITE_FAILED','INVALID_USAGE','TIMEOUT','INTERRUPTED','PARTIAL','MAYBE_PREVENTED','MAYBE_PARTIAL_PREVENTED'"
SUCC="'EXECUTED','EXECUTED_WITH_CLEANUP_FAILURE','WARNING','ACCESS_DENIED'"
NOT_COUNTED="'ASSET_AGENTLESS','ASSET_CLEARANCE_RESTRICTED','AGENT_INACTIVE','AGENT_OVERLOADED','INFO'"

# The marking predicate the statement inspector adds to execution_traces (MarkingDimension.predicate:
# execution_traces -> agents -> assets)
mark() {
  local t=$1
  echo "($t.execution_agent_id IS NULL OR EXISTS (SELECT 1 FROM agents mkp0_agents WHERE mkp0_agents.agent_id = $t.execution_agent_id AND (mkp0_agents.agent_asset IS NULL OR EXISTS (SELECT 1 FROM assets mkp1_assets WHERE mkp1_assets.asset_id = mkp0_agents.agent_asset AND is_marking_set_allowed(mkp1_assets.marking_ids)))))"
}

# InjectStatusUtils.visibleStatus rows 3 to 6, on the COMPLETE traces the reader sees
SUBQUERY="(SELECT CASE
    WHEN coalesce(bool_or(t.execution_agent_id IS NULL AND t.execution_status IN ($ERR)), false) THEN s.status_name
    WHEN count(DISTINCT t.execution_agent_id) >= s.status_expected_agent_count THEN s.status_name
    WHEN NOT coalesce(bool_or(t.execution_status NOT IN ($NOT_COUNTED)), false) THEN 'NOT_VISIBLE'
    WHEN coalesce(bool_or(t.execution_status IN ($ERR)), false)
         AND NOT coalesce(bool_or(t.execution_status IN ($SUCC)), false) THEN 'ERROR'
    WHEN coalesce(bool_or(t.execution_status IN ($SUCC)), false)
         AND NOT coalesce(bool_or(t.execution_status IN ($ERR)), false) THEN 'EXECUTED'
    ELSE 'PARTIAL' END
  FROM execution_traces t
  WHERE t.execution_inject_status_id = s.status_id AND t.execution_action = 'COMPLETE' AND $(mark t))"

# Rows 1 and 2 short-circuit: in flight, or no agent
VISIBLE="(CASE WHEN s.status_name NOT IN ($CLOSED) OR coalesce(s.status_expected_agent_count, 0) = 0
    THEN s.status_name ELSE $SUBQUERY END)"

# --visible: print the expression alone (check-visible.sh)
if [ "${1:-}" = "--visible" ]; then
  echo "$VISIBLE"
  exit 0
fi

# Atomic testings of the tenant (the search's base, without the joins of the output)
FROM="FROM injects i LEFT JOIN injects_statuses s ON s.status_inject = i.inject_id
  WHERE i.inject_scenario IS NULL AND i.inject_exercise IS NULL AND i.tenant_id = '$TENANT'"

measure() {
  local label=$1 sql=$2
  cat <<SQL
\\echo '### $label'
BEGIN;
SET LOCAL app.current_tenants = '$TENANT';
SET LOCAL app.current_markings = :'clearance';
SET LOCAL jit = :'jit';
EXPLAIN (ANALYZE, BUFFERS) $sql;
ROLLBACK;
SQL
}

# A page in the search's order, keeping the closed injects dispatched to agents (InjectVisibleStatusService)
for size in 20 100 500; do
  cat <<SQL
SELECT string_agg(quote_literal(inject_id), ',') AS ids$size FROM (
  SELECT i.inject_id $FROM AND i.inject_id LIKE 'perf-inject-%'
    AND s.status_name IN ($CLOSED) AND s.status_expected_agent_count > 0
  ORDER BY i.inject_updated_at DESC LIMIT $size) page \\gset
SQL
done

# -- Part 1: on every read, whatever the criteria --
for size in 20 100 500; do
  measure "p1-extra-query-$size" "SELECT s.status_inject, t.execution_status, t.execution_agent_id
  FROM execution_traces t JOIN injects_statuses s ON s.status_id = t.execution_inject_status_id
  WHERE s.status_inject IN (:ids$size) AND t.execution_action = 'COMPLETE' AND $(mark t)"
done

# -- Part 2: filter on inject_status = ERROR (page + count) and sort on it --
filter() {
  local label=$1 predicate=$2
  measure "p2-$label-page" "SELECT i.inject_id, i.inject_title, s.status_name $FROM AND $predicate
  ORDER BY i.inject_updated_at DESC LIMIT 20"
  measure "p2-$label-count" "SELECT count(i.inject_id) $FROM AND $predicate"
}
filter "filter-stored" "s.status_name = 'ERROR'"
filter "filter-c" "$VISIBLE = 'ERROR'"
filter "filter-c-split" "(((s.status_name NOT IN ($CLOSED) OR coalesce(s.status_expected_agent_count, 0) = 0)
    AND s.status_name = 'ERROR')
  OR (s.status_name IN ($CLOSED) AND s.status_expected_agent_count > 0 AND $SUBQUERY = 'ERROR'))"
# A reader's ERROR can only come from a stored ERROR or PARTIAL
filter "filter-c-candidates" "s.status_name IN ('ERROR', 'PARTIAL') AND $VISIBLE = 'ERROR'"

measure "p2-sort-stored" "SELECT i.inject_id, i.inject_title, s.status_name $FROM
  ORDER BY s.status_name, i.inject_updated_at DESC LIMIT 20"
measure "p2-sort-c" "SELECT i.inject_id, i.inject_title, $VISIBLE AS status $FROM
  ORDER BY 3, i.inject_updated_at DESC LIMIT 20"
