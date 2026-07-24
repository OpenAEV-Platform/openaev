package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Attack path execution store (issue 6647). Adds three additive, isolated tables that hold a
 * simulation's execution data as a normalized relational model, so the attack path graph rebuilds
 * from two flat indexed reads plus one in-memory pass instead of parsing a JSONB blob.
 *
 * <ul>
 *   <li>{@code attackpath_execution} — one row = one edge (source → target); carries the run
 *       snapshot (endpoint/agent/step attributes frozen at execution time) so a past run renders
 *       its state, not today's.
 *   <li>{@code attackpath_finding} — one row = one (endpoint, type, value); the simulation id is
 *       denormalized for per-simulation reads.
 *   <li>{@code attackpath_execution_finding} — many-to-many link keeping which execution produced
 *       which finding.
 * </ul>
 *
 * <p>Consolidated (2026-07-21): this folds the four original POC migrations (the initial tables
 * plus the inject-id, source-endpoint and payload/injector-type column additions) into one CREATE,
 * so the long-lived branch carries a single attack-path schema migration dated after the last
 * release rather than several that sort into the middle of the already-released block. Purely
 * additive and idempotent (IF NOT EXISTS throughout), so re-running it is a no-op.
 *
 * <p>Re-dated (2026-07-23, formerly 6.20260719200000000): the original timestamp sorted BEFORE
 * migrations already applied on deployed databases (prod had run the 07/20-07/23 block before this
 * one merged), so Flyway validation failed with "resolved migration not applied" and out-of-order
 * disabled. Databases that already ran the old version re-run this one as a no-op; their orphaned
 * old history row is covered by the default {@code ignore-migration-patterns=*:missing}.
 */
@Component
public class V6_20260723160000000__Add_attack_path_tables extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_execution (
              attackpath_execution_id                   text NOT NULL
                  CONSTRAINT attackpath_execution_pkey PRIMARY KEY,
              tenant_id                                 varchar(255) NOT NULL
                  CONSTRAINT attackpath_execution_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              attackpath_execution_simulation_id        varchar(255) NOT NULL,
              attackpath_execution_step_id              varchar(255),
              attackpath_execution_step_template_id     varchar(255),
              attackpath_execution_contract_external_id text,
              attackpath_execution_source_kind          text NOT NULL,
              attackpath_execution_source_asset_id      varchar(255),
              attackpath_execution_source_hostname      text,
              attackpath_execution_source_ip            text,
              attackpath_execution_source_platform      text,
              attackpath_execution_agent_id             varchar(255),
              attackpath_execution_agent_name           text,
              attackpath_execution_agent_privilege      text,
              attackpath_execution_source_injector      text,
              attackpath_execution_injector_type        varchar(255),
              attackpath_execution_target_kind          text NOT NULL,
              attackpath_execution_target_asset_id      varchar(255),
              attackpath_execution_target_raw_value     text,
              attackpath_execution_target_key           text NOT NULL,
              attackpath_execution_target_hostname      text,
              attackpath_execution_target_ip            text,
              attackpath_execution_target_platform      text,
              attackpath_execution_payload_name         text,
              attackpath_execution_payload_id           varchar(255),
              attackpath_execution_executed_at          timestamp NOT NULL,
              attackpath_execution_prevention_status    text,
              attackpath_execution_detection_status     text,
              attackpath_execution_vulnerability_status text,
              attackpath_execution_command              text,
              attackpath_execution_terminal_output      text
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_sim_targetkey "
              + "ON attackpath_execution (attackpath_execution_simulation_id, "
              + "attackpath_execution_target_key);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_step_agent "
              + "ON attackpath_execution (attackpath_execution_step_id, "
              + "attackpath_execution_agent_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_tenant "
              + "ON attackpath_execution (tenant_id);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_finding (
              attackpath_finding_id            varchar(255) NOT NULL
                  CONSTRAINT attackpath_finding_pkey PRIMARY KEY,
              tenant_id                        varchar(255) NOT NULL
                  CONSTRAINT attackpath_finding_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              attackpath_finding_simulation_id varchar(255) NOT NULL,
              attackpath_finding_type          text NOT NULL,
              attackpath_finding_value         text NOT NULL,
              attackpath_finding_endpoint_id   varchar(255),
              attackpath_finding_endpoint_raw  text,
              attackpath_finding_endpoint_key  text NOT NULL
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_find_sim "
              + "ON attackpath_finding (attackpath_finding_simulation_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_find_sim_endpointkey_type "
              + "ON attackpath_finding (attackpath_finding_simulation_id, "
              + "attackpath_finding_endpoint_key, attackpath_finding_type);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_find_tenant " + "ON attackpath_finding (tenant_id);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS attackpath_execution_finding (
              execution_id text NOT NULL
                  CONSTRAINT attackpath_ef_execution_fk
                      REFERENCES attackpath_execution (attackpath_execution_id) ON DELETE CASCADE,
              finding_id   varchar(255) NOT NULL
                  CONSTRAINT attackpath_ef_finding_fk
                      REFERENCES attackpath_finding (attackpath_finding_id) ON DELETE CASCADE,
              CONSTRAINT attackpath_execution_finding_pkey PRIMARY KEY (execution_id, finding_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_ef_finding "
              + "ON attackpath_execution_finding (finding_id);");
    }
  }
}
