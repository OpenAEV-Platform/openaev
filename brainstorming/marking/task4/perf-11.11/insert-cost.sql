-- Insert cost on execution_traces: 100,000 traces (one in six COMPLETE), rolled back.
\echo '### insert-100k-traces'
BEGIN;
\timing on
INSERT INTO execution_traces (execution_trace_id, execution_inject_status_id, execution_agent_id,
                              execution_message, execution_action, execution_status,
                              execution_time, tenant_id)
SELECT 'perf-insert-' || g, 'perf-status-' || ((g % 9000) + 2), 'perf-agent-' || ((g % 500) + 1), '',
       CASE WHEN g % 6 = 0 THEN 'COMPLETE' ELSE 'EXECUTION' END, 'EXECUTED', now(),
       '2cffad3a-0001-4078-b0e2-ef74274022c3'
FROM generate_series(1, 100000) g;
\timing off
ROLLBACK;
