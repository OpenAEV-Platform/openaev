package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Reassigns new UUID primary keys to CrowdStrike/SentinelOne agents whose {@code agent_id} was
 * previously set to their {@code agent_external_reference}.
 *
 * <p>This caused cross-tenant PK collisions when multiple tenants synced the same external
 * platform. After this migration, {@code agent_id} is a random UUID and {@code
 * agent_external_reference} remains the external device ID used for API callbacks.
 *
 * <p>None of the FK constraints that reference {@code agents.agent_id} declare {@code ON UPDATE
 * CASCADE}, and most of them are non-deferrable, so neither the parent PK nor the child references
 * can be updated first without an immediate FK violation (see #6559). The migration therefore
 * temporarily marks the non-deferrable constraints as {@code DEFERRABLE}, defers the checks of
 * exactly these constraints to the end of the remap, and restores {@code NOT DEFERRABLE} once every
 * table is consistent:
 *
 * <ol>
 *   <li>{@code injects_expectations.agent_id} ({@code fk_agent})
 *   <li>{@code execution_traces.execution_agent_id} (already {@code DEFERRABLE INITIALLY DEFERRED},
 *       see V4_59)
 *   <li>{@code asset_agent_jobs.asset_agent_agent} ({@code asset_agent_agent_fk})
 *   <li>{@code agents.agent_parent} ({@code agent_parent_id_fk}, self-reference)
 * </ol>
 *
 * <p>Everything runs inside the single Flyway migration transaction, so a failure at any point
 * rolls back both the data changes and the constraint alterations.
 *
 * <p>Remapped agent ids are also embedded in Elasticsearch {@code vulnerable-endpoint} documents
 * ({@code base_agents_side}); the incremental indexer cursors on asset/exercise timestamps that
 * this migration does not touch, so it forces a full reindex of that type (same pattern as V4_08).
 *
 * <p>Note on in-place edit of this migration (shipped broken in 2.260703.2): Java-based migrations
 * have no checksum ({@code BaseJavaMigration#getChecksum()} returns {@code null}), so environments
 * where the original version already succeeded (only possible without child rows, where both
 * versions are semantically identical) validate fine and are not re-run.
 */
@Component
public class V6_20260701180000000__Reassign_agent_ids_for_external_executors
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // Allow the FK checks to be deferred until all parent and child rows are
      // updated. ALTER CONSTRAINT only touches catalog metadata (no revalidation).
      // Only the constraints involved in the remap are deferred, to stay
      // insensitive to unrelated deferrable constraints in the same transaction.
      stmt.execute(
          """
          ALTER TABLE injects_expectations ALTER CONSTRAINT fk_agent DEFERRABLE INITIALLY IMMEDIATE;
          ALTER TABLE asset_agent_jobs ALTER CONSTRAINT asset_agent_agent_fk DEFERRABLE INITIALLY IMMEDIATE;
          ALTER TABLE agents ALTER CONSTRAINT agent_parent_id_fk DEFERRABLE INITIALLY IMMEDIATE;
          SET CONSTRAINTS fk_agent, asset_agent_agent_fk, agent_parent_id_fk,
                          execution_traces_execution_agent_id_fkey DEFERRED;
          """);

      // Build a temporary mapping of old_id -> new_id for affected agents.
      stmt.execute(
          """
          CREATE TEMP TABLE agent_id_remap AS
          SELECT agent_id AS old_id, gen_random_uuid()::text AS new_id
          FROM agents
          WHERE agent_external_reference IS NOT NULL
            AND agent_id = agent_external_reference
          """);

      // injects_expectations.agent_id -> agents.agent_id
      stmt.execute(
          """
          UPDATE injects_expectations ie
          SET agent_id = r.new_id
          FROM agent_id_remap r
          WHERE ie.agent_id = r.old_id
          """);

      // execution_traces.execution_agent_id -> agents.agent_id
      stmt.execute(
          """
          UPDATE execution_traces et
          SET execution_agent_id = r.new_id
          FROM agent_id_remap r
          WHERE et.execution_agent_id = r.old_id
          """);

      // asset_agent_jobs.asset_agent_agent -> agents.agent_id
      stmt.execute(
          """
          UPDATE asset_agent_jobs aaj
          SET asset_agent_agent = r.new_id
          FROM agent_id_remap r
          WHERE aaj.asset_agent_agent = r.old_id
          """);

      // agents.agent_parent -> agents.agent_id (self-reference)
      stmt.execute(
          """
          UPDATE agents a
          SET agent_parent = r.new_id
          FROM agent_id_remap r
          WHERE a.agent_parent = r.old_id
          """);

      // Reassign the PK itself.
      stmt.execute(
          """
          UPDATE agents a
          SET agent_id = r.new_id
          FROM agent_id_remap r
          WHERE a.agent_id = r.old_id
          """);

      stmt.execute("DROP TABLE agent_id_remap");

      // Validate the deferred FK checks now, then restore the original
      // non-deferrable definitions.
      stmt.execute(
          """
          SET CONSTRAINTS fk_agent, asset_agent_agent_fk, agent_parent_id_fk,
                          execution_traces_execution_agent_id_fkey IMMEDIATE;
          ALTER TABLE injects_expectations ALTER CONSTRAINT fk_agent NOT DEFERRABLE;
          ALTER TABLE asset_agent_jobs ALTER CONSTRAINT asset_agent_agent_fk NOT DEFERRABLE;
          ALTER TABLE agents ALTER CONSTRAINT agent_parent_id_fk NOT DEFERRABLE;
          """);

      // Agent ids are denormalized into ES vulnerable-endpoint documents
      // (base_agents_side); force a full reindex of that type since the
      // incremental indexer would not pick up the PK change.
      stmt.execute(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'vulnerable-endpoint'");
    }
  }
}
