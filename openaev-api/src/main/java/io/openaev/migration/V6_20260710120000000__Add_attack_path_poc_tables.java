package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Attack path execution store POC (issue 6647). Adds three additive, isolated tables that hold a
 * simulation's execution data as a normalized relational model, so the attack path graph rebuilds
 * from two flat indexed reads plus one in-memory pass instead of parsing a JSONB blob.
 *
 * <ul>
 *   <li>{@code attackpath_execution} — one row = one edge (source → target); carries the run
 *       snapshot (endpoint/agent/step attributes frozen at execution time) so a past run renders
 *       its state, not today's.
 *   <li>{@code attackpath_finding} — one row = one (endpoint, type, value); {@code simulation_id}
 *       denormalized for per-simulation reads.
 *   <li>{@code attackpath_execution_finding} — many-to-many link keeping which execution produced
 *       which finding.
 * </ul>
 *
 * <p>Additive only, no change to existing tables. Reference ids ({@code simulation_id}, asset/agent
 * ids, {@code contract_external_id}) are plain columns, not hard FKs, so the POC stays
 * self-contained and droppable and the generator does not pollute real product tables. The one real
 * relationship kept is {@code tenant_id → tenants}, because tenant isolation (MT v2) is enforced
 * through it. Indexes lead with {@code simulation_id}: the tenant predicate is the inspector's
 * {@code can_access_tenant(tenant_id)} function, not an index equality.
 */
@Component
public class V6_20260710120000000__Add_attack_path_poc_tables extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_execution (
              id                   varchar(255) NOT NULL
                  CONSTRAINT attackpath_execution_pkey PRIMARY KEY,
              tenant_id            varchar(255) NOT NULL
                  CONSTRAINT attackpath_execution_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              simulation_id        varchar(255) NOT NULL,
              step_id              varchar(255),
              step_template_id     varchar(255),
              contract_external_id text,
              source_kind          text NOT NULL,
              source_asset_id      varchar(255),
              agent_id             varchar(255),
              agent_name           text,
              agent_privilege      text,
              source_injector      text,
              target_kind          text NOT NULL,
              target_asset_id      varchar(255),
              target_raw_value     text,
              target_key           text NOT NULL,
              target_hostname      text,
              target_ip            text,
              target_platform      text,
              payload_name         text,
              executed_at          timestamp NOT NULL,
              prevention_status    text,
              detection_status     text,
              command              text,
              terminal_output      text
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_sim "
              + "ON attackpath_execution (simulation_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_sim_targetkey "
              + "ON attackpath_execution (simulation_id, target_key);");
      // Indexes the tenant FK's referencing column so ON DELETE CASCADE does not seq-scan the table
      // when a tenant is removed. The read path stays on the simulation_id indexes above.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_tenant "
              + "ON attackpath_execution (tenant_id);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_finding (
              id            varchar(255) NOT NULL
                  CONSTRAINT attackpath_finding_pkey PRIMARY KEY,
              tenant_id     varchar(255) NOT NULL
                  CONSTRAINT attackpath_finding_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              simulation_id varchar(255) NOT NULL,
              type          text NOT NULL,
              value         text NOT NULL,
              endpoint_id   varchar(255),
              endpoint_raw  text,
              endpoint_key  text NOT NULL
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_find_sim " + "ON attackpath_finding (simulation_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_find_sim_endpointkey_type "
              + "ON attackpath_finding (simulation_id, endpoint_key, type);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_find_tenant " + "ON attackpath_finding (tenant_id);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_execution_finding (
              execution_id varchar(255) NOT NULL
                  CONSTRAINT attackpath_ef_execution_fk
                      REFERENCES attackpath_execution (id) ON DELETE CASCADE,
              finding_id   varchar(255) NOT NULL
                  CONSTRAINT attackpath_ef_finding_fk
                      REFERENCES attackpath_finding (id) ON DELETE CASCADE,
              CONSTRAINT attackpath_execution_finding_pkey PRIMARY KEY (execution_id, finding_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_ef_finding "
              + "ON attackpath_execution_finding (finding_id);");
    }
  }
}
