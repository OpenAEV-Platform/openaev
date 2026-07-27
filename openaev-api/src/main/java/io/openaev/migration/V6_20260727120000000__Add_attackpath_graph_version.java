package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Versioning groundwork for the attack-path delta reads (#6647, spec 002): one monotonic counter
 * per simulation in {@code attackpath_graph_version}, plus a {@code row_version} stamp on both
 * projection tables so "what changed since v" is a cursor read rather than a journal.
 *
 * <p>The counter row is what makes the version order equal the commit order: every projection write
 * bumps it with {@code INSERT ... ON CONFLICT DO UPDATE}, whose row lock is held until commit, so
 * two concurrent writers on the same simulation cannot commit their rows in a different order than
 * they took their versions. A client is therefore never handed a version whose rows are still
 * uncommitted.
 *
 * <p>The counter's primary key is {@code (simulation_id, tenant_id)}, not the simulation alone: the
 * table is deliberately not tenant-active (the bump is an {@code INSERT ... ON CONFLICT}, which the
 * statement inspector cannot rewrite), so its isolation is structural. One counter per tenant means
 * a tenant can never read, bump or delete another tenant's version even though nothing rewrites its
 * statements, and simulation ids are only unique within a tenant anyway.
 *
 * <p>Both row-version columns are {@code NOT NULL DEFAULT 0}, so already-populated rows stay valid
 * and need no backfill: they read as version 0, which any {@code since = 0} delta includes and
 * every later {@code since} excludes — exactly the intended semantics. The cursor indexes back the
 * only delta query shape ({@code WHERE simulation_id = ? AND row_version > ?}). Additive and
 * idempotent; no Elasticsearch-indexed entity is touched, so no {@code indexing_status} reset.
 */
@Component
public class V6_20260727120000000__Add_attackpath_graph_version extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE TABLE IF NOT EXISTS attackpath_graph_version ("
              + "attackpath_graph_version_simulation_id VARCHAR(255) NOT NULL, "
              + "tenant_id VARCHAR(255) NOT NULL, "
              + "attackpath_graph_version_value BIGINT NOT NULL, "
              + "CONSTRAINT pk_attackpath_graph_version "
              + "PRIMARY KEY (attackpath_graph_version_simulation_id, tenant_id), "
              + "CONSTRAINT fk_attackpath_graph_version_tenant FOREIGN KEY (tenant_id) "
              + "REFERENCES tenants(tenant_id) ON DELETE CASCADE);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_graph_version_tenant "
              + "ON attackpath_graph_version(tenant_id);");

      statement.execute(
          "ALTER TABLE attackpath_execution ADD COLUMN IF NOT EXISTS "
              + "attackpath_execution_row_version BIGINT NOT NULL DEFAULT 0;");
      statement.execute(
          "ALTER TABLE attackpath_finding ADD COLUMN IF NOT EXISTS "
              + "attackpath_finding_row_version BIGINT NOT NULL DEFAULT 0;");

      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_sim_rowversion ON attackpath_execution("
              + "attackpath_execution_simulation_id, attackpath_execution_row_version);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_finding_sim_rowversion ON attackpath_finding("
              + "attackpath_finding_simulation_id, attackpath_finding_row_version);");
    }
  }
}
