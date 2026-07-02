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
 * <p>Because none of the FK constraints that reference {@code agents.agent_id} declare {@code ON
 * UPDATE CASCADE}, this migration builds an {@code old_id → new_id} remap table and manually
 * propagates the new PK to every child table before updating the parent row:
 *
 * <ol>
 *   <li>{@code injects_expectations.agent_id}
 *   <li>{@code execution_traces.execution_agent_id}
 *   <li>{@code asset_agent_jobs.asset_agent_agent}
 *   <li>{@code agents.agent_parent} (self-reference)
 * </ol>
 */
@Component
public class V6_20260701180000000__Reassign_agent_ids_for_external_executors
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // Build a temporary mapping of old_id → new_id for affected agents.
      // FK constraints on child tables have no ON UPDATE CASCADE, so we must
      // update every referencing table manually before touching the PK.
      stmt.execute(
          """
          CREATE TEMP TABLE agent_id_remap AS
          SELECT agent_id AS old_id, gen_random_uuid()::text AS new_id
          FROM agents
          WHERE agent_external_reference IS NOT NULL
            AND agent_id = agent_external_reference
          """);

      // injects_expectations.agent_id → agents.agent_id
      stmt.execute(
          """
          UPDATE injects_expectations ie
          SET agent_id = r.new_id
          FROM agent_id_remap r
          WHERE ie.agent_id = r.old_id
          """);

      // execution_traces.execution_agent_id → agents.agent_id
      stmt.execute(
          """
          UPDATE execution_traces et
          SET execution_agent_id = r.new_id
          FROM agent_id_remap r
          WHERE et.execution_agent_id = r.old_id
          """);

      // asset_agent_jobs.asset_agent_agent → agents.agent_id
      stmt.execute(
          """
          UPDATE asset_agent_jobs aaj
          SET asset_agent_agent = r.new_id
          FROM agent_id_remap r
          WHERE aaj.asset_agent_agent = r.old_id
          """);

      // agents.agent_parent → agents.agent_id (self-reference)
      stmt.execute(
          """
          UPDATE agents a
          SET agent_parent = r.new_id
          FROM agent_id_remap r
          WHERE a.agent_parent = r.old_id
          """);

      // Finally reassign the PK itself.
      stmt.execute(
          """
          UPDATE agents a
          SET agent_id = r.new_id
          FROM agent_id_remap r
          WHERE a.agent_id = r.old_id
          """);

      stmt.execute("DROP TABLE agent_id_remap");
    }
  }
}
