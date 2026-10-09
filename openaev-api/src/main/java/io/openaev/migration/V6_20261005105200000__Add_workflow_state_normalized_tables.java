package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * WorkflowState storage refactor (ADR-010) — normalized relational store.
 *
 * <p>ADR-010 replaces the single JSONB blob {@code workflow_states.workflow_state_entries} with a
 * normalized relational model so that entry lookups, correlated-tuple reads and anti-replay checks
 * run as flat indexed reads and single-row inserts instead of parsing and re-serializing a JSON
 * document on every mutation.
 *
 * <p>The two models must coexist during the migration window: runs keep flowing through the legacy
 * path while the normalized path is validated. Routing is decided per run by the new {@code
 * workflows.storage_mode} flag ({@code LEGACY_JSONB} vs {@code NORMALIZED}), set once at run
 * creation and never mutated afterwards.
 *
 * <p>This migration is strictly additive: it introduces one table and one column and does
 * <b>not</b> touch the existing {@code workflow_states.workflow_state_entries} JSONB column, which
 * the {@code LEGACY_JSONB} path keeps using until the coexistence window closes (the
 * post-coexistence cleanup described in ADR-010 §4.4 removes the legacy column, the {@code
 * storage_mode} flag and all {@code LEGACY_JSONB} code paths).
 *
 * <p>{@code workflow_state_entries} holds one row per normalized entry; {@code entry_type} carries
 * the discriminator ({@code INPUT} / {@code CORRELATED} / {@code HASH_EXECUTION}). A correlated
 * tuple is stored as one {@code CORRELATED} row per field, all sharing the tuple's {@code
 * correlation_hash} and {@code correlation_type}.
 *
 * <p>Idempotent throughout ({@code IF NOT EXISTS}), so re-running it is a no-op.
 */
@Component
public class V6_20261005105200000__Add_workflow_state_normalized_tables extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      // 1. Normalized entries: one row per (input value | correlated field | execution hash).
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS workflow_state_entries (
              id                BIGSERIAL PRIMARY KEY,
              workflow_state_id VARCHAR(255) NOT NULL
                  REFERENCES workflow_states (workflow_state_id) ON DELETE CASCADE,
              entry_type        VARCHAR(20) NOT NULL,
              entry_key         VARCHAR(255) NOT NULL,
              entry_value       TEXT NOT NULL,
              -- Non-null only for entry_type = CORRELATED: MurmurHash3-128 (hex) of the tuple
              correlation_hash  VARCHAR(64),
              -- Non-null only for entry_type = CORRELATED: business type of the tuple
              correlation_type  VARCHAR(255),
              created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
              CONSTRAINT chk_wse_entry_type
                  CHECK (entry_type IN ('INPUT', 'CORRELATED', 'HASH_EXECUTION'))
          );
          """);
      // Leading workflow_state_id column also covers the FK (cascade deletes).
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_wse_lookup "
              + "ON workflow_state_entries (workflow_state_id, entry_type, entry_key);");

      // 2. DB-level deduplication, one partial unique index per entry type. INPUT and CORRELATED
      // values are free text of unbounded length: indexing md5(entry_value) instead of the raw
      // value keeps the index entries under the B-tree row size limit (~2.7 kB).
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_wse_input "
              + "ON workflow_state_entries (workflow_state_id, entry_key, md5(entry_value)) "
              + "WHERE entry_type = 'INPUT';");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_wse_hash "
              + "ON workflow_state_entries (workflow_state_id, entry_value) "
              + "WHERE entry_type = 'HASH_EXECUTION';");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_wse_correlated "
              + "ON workflow_state_entries "
              + "(workflow_state_id, correlation_hash, entry_key, md5(entry_value)) "
              + "WHERE entry_type = 'CORRELATED';");

      // 3. Dual-run routing flag on the run. Temporary — see ADR-010 §4.4 cleanup.
      statement.execute(
          "ALTER TABLE workflows "
              + "ADD COLUMN IF NOT EXISTS storage_mode VARCHAR(20) NOT NULL "
              + "DEFAULT 'LEGACY_JSONB';");
      statement.execute(
          """
          COMMENT ON COLUMN workflows.storage_mode IS
            'TEMPORARY — ADR-010 dual-run routing flag (LEGACY_JSONB / NORMALIZED). Set once at run
             creation in WorkflowService.copyWorkflowTemplateToRun(), never mutated afterwards. To be
             dropped entirely, along with all LEGACY_JSONB code paths, once the coexistence window
             closes (see ADR-010 §4.4 cleanup ticket).';
          """);
    }
  }
}
