-- Seeds the volume for the 11.11 measurement, in the default tenant of a CLONE of the dev database.
-- Variables: :a agents per inject, :n_injects atomic testings, :n_assets assets (one agent each).
--
-- Assets: even = RED ('perf-red'), odd = GREEN ('perf-green').
-- Injects, by g % 10:
--   0      in flight: EXECUTING, START and EXECUTION traces per agent, no COMPLETE
--   1      no agent: EXECUTED, expected count 0, one agent-less COMPLETE trace
--   2..9   closed on :a agents: 6 traces per agent (START ... COMPLETE), stored status computed from
--          the COMPLETE traces like InjectStatusUtils.computeStatus
-- COMPLETE outcome of a closed inject, by g % 4: 0 all EXECUTED, 1 all ERROR, 2-3 mixed per agent
-- (70% EXECUTED, 25% ERROR, 5% AGENT_INACTIVE).

\set ON_ERROR_STOP on
\set tenant '2cffad3a-0001-4078-b0e2-ef74274022c3'

BEGIN;

INSERT INTO assets (asset_id, asset_type, asset_name, tenant_id, marking_ids, endpoint_platform)
SELECT 'perf-asset-' || g, 'Endpoint', 'perf asset ' || g, :'tenant',
       CASE WHEN g % 2 = 0 THEN ARRAY['perf-red'] ELSE ARRAY['perf-green'] END, 'Linux'
FROM generate_series(1, :n_assets) g;

INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode,
                    agent_executed_by_user, tenant_id)
SELECT 'perf-agent-' || g, 'perf-asset-' || g, 'admin', 'service', 'root', :'tenant'
FROM generate_series(1, :n_assets) g;

INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,
                     inject_depends_duration, tenant_id, inject_created_at, inject_updated_at)
SELECT 'perf-inject-' || g, 'perf-' || g, false, true, 0, :'tenant',
       now() - g * interval '1 minute', now() - g * interval '1 minute'
FROM generate_series(1, :n_injects) g;

INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date,
                              tracking_end_date, status_expected_agent_count)
SELECT 'perf-status-' || g, 'perf-inject-' || g,
       CASE WHEN g % 10 = 0 THEN 'EXECUTING' ELSE 'EXECUTED' END,
       now() - g * interval '1 minute',
       CASE WHEN g % 10 = 0 THEN NULL ELSE now() - g * interval '1 minute' END,
       CASE WHEN g % 10 = 1 THEN 0 ELSE :a END
FROM generate_series(1, :n_injects) g;

-- Closed on agents: 6 traces per agent
INSERT INTO execution_traces (execution_trace_id, execution_inject_status_id, execution_agent_id,
                              execution_message, execution_action, execution_status,
                              execution_time, tenant_id)
SELECT 'perf-trace-' || g || '-' || k || '-' || act.n, 'perf-status-' || g,
       'perf-agent-' || (((g * 37 + k) % :n_assets) + 1), '', act.name,
       CASE
         WHEN act.name <> 'COMPLETE' THEN 'INFO'
         WHEN g % 4 = 0 THEN 'EXECUTED'
         WHEN g % 4 = 1 THEN 'ERROR'
         WHEN (g * 31 + k * 17) % 20 < 14 THEN 'EXECUTED'
         WHEN (g * 31 + k * 17) % 20 < 19 THEN 'ERROR'
         ELSE 'AGENT_INACTIVE'
       END,
       now(), :'tenant'
FROM generate_series(1, :n_injects) g
CROSS JOIN generate_series(0, :a - 1) k
CROSS JOIN (VALUES (1, 'START'), (2, 'PREREQUISITE_CHECK'), (3, 'PREREQUISITE_EXECUTION'),
                   (4, 'EXECUTION'), (5, 'CLEANUP_EXECUTION'), (6, 'COMPLETE')) act(n, name)
WHERE g % 10 >= 2;

-- In flight: START and EXECUTION per agent
INSERT INTO execution_traces (execution_trace_id, execution_inject_status_id, execution_agent_id,
                              execution_message, execution_action, execution_status,
                              execution_time, tenant_id)
SELECT 'perf-trace-' || g || '-' || k || '-' || act.n, 'perf-status-' || g,
       'perf-agent-' || (((g * 37 + k) % :n_assets) + 1), '', act.name, 'INFO', now(), :'tenant'
FROM generate_series(1, :n_injects) g
CROSS JOIN generate_series(0, :a - 1) k
CROSS JOIN (VALUES (1, 'START'), (4, 'EXECUTION')) act(n, name)
WHERE g % 10 = 0;

-- No agent: one agent-less COMPLETE
INSERT INTO execution_traces (execution_trace_id, execution_inject_status_id, execution_message,
                              execution_action, execution_status, execution_time, tenant_id)
SELECT 'perf-trace-' || g, 'perf-status-' || g, '', 'COMPLETE', 'EXECUTED', now(), :'tenant'
FROM generate_series(1, :n_injects) g
WHERE g % 10 = 1;

-- Stored status of the closed injects: computeStatus over every COMPLETE trace
UPDATE injects_statuses s
SET status_name = c.status
FROM (SELECT t.execution_inject_status_id AS status_id,
             CASE
               WHEN coalesce(bool_or(t.execution_status IN ('ERROR', 'TIMEOUT')), false)
                    AND NOT coalesce(bool_or(t.execution_status = 'EXECUTED'), false) THEN 'ERROR'
               WHEN coalesce(bool_or(t.execution_status = 'EXECUTED'), false)
                    AND NOT coalesce(bool_or(t.execution_status IN ('ERROR', 'TIMEOUT')), false)
                 THEN 'EXECUTED'
               WHEN NOT coalesce(bool_or(t.execution_status IN ('EXECUTED', 'ERROR', 'TIMEOUT')), false)
                 THEN 'ERROR'
               ELSE 'PARTIAL'
             END AS status
      FROM execution_traces t
      WHERE t.execution_action = 'COMPLETE'
        AND t.execution_inject_status_id LIKE 'perf-status-%'
        AND t.execution_agent_id IS NOT NULL
      GROUP BY t.execution_inject_status_id) c
WHERE s.status_id = c.status_id;

COMMIT;

VACUUM ANALYZE assets;
VACUUM ANALYZE agents;
VACUUM ANALYZE injects;
VACUUM ANALYZE injects_statuses;
VACUUM ANALYZE execution_traces;

SELECT s.status_name, s.status_expected_agent_count AS expected, count(*) AS injects
FROM injects_statuses s
WHERE s.status_id LIKE 'perf-status-%'
GROUP BY 1, 2
ORDER BY 1, 2;
SELECT count(*) AS perf_traces FROM execution_traces WHERE execution_trace_id LIKE 'perf-trace-%';
