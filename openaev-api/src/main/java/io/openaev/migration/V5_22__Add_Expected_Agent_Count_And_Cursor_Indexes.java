package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Second wave of the 2026-06 performance follow-ups (see #6153).
 *
 * <p>1. Adds the expected-agent-count column on injects_statuses: the count of agents resolved at
 * launch time is persisted so the implant COMPLETE callback path can decide inject completion by
 * comparing trace counts against it, instead of re-resolving the inject's entire asset/agent graph
 * (including dynamic asset-group filters) under the per-inject lock on every callback. Null for
 * injects launched before this column existed; the callback path falls back to the legacy graph
 * resolution in that case.
 *
 * <p>2. Adds the indexes backing the reworked ES injects re-indexing cursor query
 * (InjectRepository.findForIndexing), whose filter is now a UNION of branches that each page on one
 * of these columns.
 */
@Component
public class V5_22__Add_Expected_Agent_Count_And_Cursor_Indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1. Expected agent count persisted at launch
      statement.execute(
          "ALTER TABLE injects_statuses "
              + "ADD COLUMN IF NOT EXISTS status_expected_agent_count integer");

      // 2. ES injects cursor branches
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injectors_contracts_updated_at "
              + "ON injectors_contracts (injector_contract_updated_at)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_dependencies_updated_at "
              + "ON injects_dependencies (dependency_updated_at)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_injector_contract "
              + "ON injects (inject_injector_contract) WHERE inject_injector_contract IS NOT NULL");

      // 3. Retention purge support: execution_traces is purged by creation-time range
      // (ExecutionTraceRetentionJob). BRIN suits this append-only timestamp column: a few KB of
      // index for millions of rows, negligible write overhead, efficient range scans.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_execution_traces_created_at_brin "
              + "ON execution_traces USING brin (execution_created_at)");

      // 4. Collector-polled "not filled" expectation queries
      // (InjectExpectationRepository.find*ExpectationsNotFilled*): filter on type, order by
      // creation date, LIMIT. The composite index serves both the filter and the sort.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_expectations_type_created_at "
              + "ON injects_expectations (inject_expectation_type, inject_expectation_created_at)");
    }
  }
}
