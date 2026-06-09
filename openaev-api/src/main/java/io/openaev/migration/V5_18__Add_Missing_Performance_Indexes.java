package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds missing indexes identified by the 2026-06 performance audit.
 *
 * <p>Focus areas: execution_traces (fastest-growing table, zero FK indexes, cascade deletes from
 * injects_statuses/agents seq-scan it), injects scenario/exercise FK lookups, agent
 * registration/identification on assets, executor synchronization on agents, IMAP communication
 * sync, ES re-indexing cursors on *_updated_at columns and inject execution hot paths.
 */
@Component
public class V5_18__Add_Missing_Performance_Indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // --- execution_traces: one row per agent execution message; joined by
      // ExecutionTraceRepository and cascade-deleted from injects_statuses,
      // injects_tests_statuses and agents. No index existed on any FK column.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_execution_traces_inject_status "
              + "ON execution_traces (execution_inject_status_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_execution_traces_agent "
              + "ON execution_traces (execution_agent_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_execution_traces_test_status "
              + "ON execution_traces (execution_inject_test_status_id)");

      // --- injects: scenario/exercise lookups (findByScenarioId, scenario deletes,
      // findByExerciseId, kill-chain CTEs). The only existing exercise index is partial
      // on inject_enabled = true and unusable for these queries.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_scenario "
              + "ON injects (inject_scenario) WHERE inject_scenario IS NOT NULL");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_exercise "
              + "ON injects (inject_exercise) WHERE inject_exercise IS NOT NULL");
      // ES re-indexing cursor (findForIndexing pages on inject_updated_at)
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_updated_at ON injects (inject_updated_at)");

      // --- assets: agent registration resolves endpoints by hostname/IPs/MACs on every
      // agent check-in (EndpointRepository &&-overlap and lower(hostname) queries).
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tenant_hostname "
              + "ON assets (tenant_id, lower(endpoint_hostname)) "
              + "WHERE endpoint_hostname IS NOT NULL");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_endpoint_ips "
              + "ON assets USING gin (endpoint_ips)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_endpoint_macs "
              + "ON assets USING gin (endpoint_mac_addresses)");
      // Endpoint listings/indexing always filter on the discriminator
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tenant_type ON assets (tenant_id, asset_type)");

      // --- communications: IMAP sync calls existsByIdentifier per synced message;
      // communication_inject is an unindexed FK with ON DELETE CASCADE.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_communications_message_id "
              + "ON communications (communication_message_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_communications_inject "
              + "ON communications (communication_inject)");

      // --- agents: executor sync (findByExecutorId, findByExternalReferenceAndTenantId)
      // and cascade deletes from executors/parents/injects.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_agents_executor ON agents (agent_executor)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_agents_parent "
              + "ON agents (agent_parent) WHERE agent_parent IS NOT NULL");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_agents_inject "
              + "ON agents (agent_inject) WHERE agent_inject IS NOT NULL");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_agents_tenant_external_ref "
              + "ON agents (tenant_id, agent_external_reference)");

      // --- asset_agent_jobs: agent job polling filters on asset_agent_agent; the existing
      // unique index is partial on asset_agent_inject IS NULL and cannot serve these queries.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_asset_agent_jobs_agent "
              + "ON asset_agent_jobs (asset_agent_agent)");

      // --- ES re-indexing cursors: every indexing job pages with
      // WHERE x_updated_at > :from ORDER BY x_updated_at LIMIT :limit.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_updated_at ON findings (finding_updated_at)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_teams_updated_at ON teams (team_updated_at)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_asset_groups_updated_at "
              + "ON asset_groups (asset_group_updated_at)");

      // --- injects_expectations: signature updates run per agent during execution with
      // WHERE inject_id = :injectId AND agent_id = :agentId; composite beats bitmap-AND
      // of the two existing single-column indexes on this hot write path.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_expectations_inject_agent "
              + "ON injects_expectations (inject_id, agent_id)");

      // --- misc unindexed FKs / lookup columns on runtime paths
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_pauses_exercise ON pauses (pause_exercise)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_documents_target ON documents (document_target)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_tenant_external_ref "
              + "ON scenarios (tenant_id, scenario_external_reference)");
    }
  }
}
