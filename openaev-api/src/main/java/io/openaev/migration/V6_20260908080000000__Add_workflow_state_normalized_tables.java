package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * WorkflowState storage refactor (ADR-007) — normalized relational store.
 *
 * <p>ADR-007 replaces the single JSONB blob {@code workflow_states.workflow_state_entries} with a
 * normalized relational model so that entry lookups, correlation matching and completion checks run
 * as flat indexed reads instead of parsing and re-serializing a JSON document on every mutation.
 *
 * <p>The two models must coexist during the migration window: runs keep flowing through the legacy
 * path while the normalized path is validated. Routing is decided per run by the new {@code
 * workflows.storage_mode} flag ({@code LEGACY_JSONB} vs {@code NORMALIZED}), set once at run
 * creation and never mutated afterwards.
 *
 * <p>This chunk is strictly additive: it introduces two tables and one column and does <b>not</b>
 * touch the existing {@code workflow_states.workflow_state_entries} JSONB column, which the {@code
 * LEGACY_JSONB} path keeps using until the coexistence window closes (ADR-007 chunk 8 removes the
 * legacy column, the {@code storage_mode} flag and all {@code LEGACY_JSONB} code paths).
 *
 * <ul>
 *   <li>{@code workflow_state_entries} — one row per normalized entry; {@code entry_type} carries
 *       the application-level discriminator ({@code INPUT} / {@code CORRELATED} / {@code
 *       HASH_EXECUTION}) and {@code correlation_hash} is populated only for {@code CORRELATED}
 *       rows.
 *   <li>{@code workflow_state_correlation_progress} — tracks, per business correlation hash, which
 *       keys are present and whether the correlation is complete and ready to fire.
 * </ul>
 *
 * <p>Idempotent throughout ({@code IF NOT EXISTS}), so re-running it is a no-op.
 */
@Component
public class V6_20260908080000000__Add_workflow_state_normalized_tables extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      // 1. Normalized entries: one row per (input | correlated | hash execution) entry.
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS workflow_state_entries (
              id                BIGSERIAL PRIMARY KEY,
              workflow_state_id VARCHAR(255) NOT NULL
                  REFERENCES workflow_states (workflow_state_id) ON DELETE CASCADE,
              -- Application-level values: INPUT | CORRELATED | HASH_EXECUTION
              entry_type        VARCHAR(20) NOT NULL,
              entry_key         VARCHAR NOT NULL,
              entry_value       TEXT NOT NULL,
              -- Non-null only for entry_type = CORRELATED
              correlation_hash  BYTEA,
              created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
              CONSTRAINT uq_wse_state_type_key_value
                  UNIQUE (workflow_state_id, entry_type, entry_key, entry_value)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_wse_lookup "
              + "ON workflow_state_entries (workflow_state_id, entry_type, entry_key);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_wse_correlation_hash "
              + "ON workflow_state_entries (correlation_hash) "
              + "WHERE correlation_hash IS NOT NULL;");

      // 2. Correlation progress keyed by the business correlation hash (not a technical id).
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS workflow_state_correlation_progress (
              correlation_hash  BYTEA PRIMARY KEY,
              workflow_state_id VARCHAR(255) NOT NULL
                  REFERENCES workflow_states (workflow_state_id) ON DELETE CASCADE,
              keys_present      TEXT[] NOT NULL DEFAULT '{}',
              is_complete       BOOLEAN NOT NULL DEFAULT FALSE
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_wscp_ready "
              + "ON workflow_state_correlation_progress (workflow_state_id) "
              + "WHERE is_complete = TRUE;");

      // 3. Dual-run routing flag on the run. Temporary — see ADR-007 chunk 8 cleanup.
      statement.execute(
          "ALTER TABLE workflows "
              + "ADD COLUMN IF NOT EXISTS storage_mode VARCHAR(20) NOT NULL "
              + "DEFAULT 'LEGACY_JSONB';");
      statement.execute(
          """
          COMMENT ON COLUMN workflows.storage_mode IS
            'TEMPORARY — ADR-007 dual-run routing flag (LEGACY_JSONB / NORMALIZED). Set once at run
             creation in WorkflowService.copyWorkflowTemplateToRun(), never mutated afterwards. To be
             dropped entirely, along with all LEGACY_JSONB code paths, once the coexistence window
             closes (see ADR-007 chunk 8 cleanup ticket).';
          """);
    }
  }
}
