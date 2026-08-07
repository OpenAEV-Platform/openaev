package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Autonomous (AI-driven) attack path store. Adds four additive, tenant-isolated tables backing the
 * autonomous launch mode of chained scenarios, gated by the {@code INJECT_CHAINING} preview flag:
 *
 * <ul>
 *   <li>{@code autonomous_runs} - one AI-driven run bound to a chained simulation + XTM One
 *       session.
 *   <li>{@code autonomous_events} - append-only, per-run-sequenced AI decision timeline.
 *   <li>{@code autonomous_directives} - operator steering directives injected into a live run.
 *   <li>{@code autonomous_objective_templates} - reusable objective gallery (built-ins + custom).
 * </ul>
 *
 * <p>Purely additive and idempotent ({@code IF NOT EXISTS} throughout), so re-running is a no-op.
 */
@Component
public class V6_20260804100000000__Add_autonomous_attack_path extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS autonomous_runs (
              autonomous_run_id                    varchar(255) NOT NULL
                  CONSTRAINT autonomous_runs_pkey PRIMARY KEY,
              tenant_id                            varchar(255) NOT NULL
                  CONSTRAINT autonomous_runs_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              autonomous_run_objective             text NOT NULL,
              autonomous_run_objective_template_key varchar(255),
              autonomous_run_status                varchar(255) NOT NULL,
              autonomous_run_simulation_id         varchar(255),
              autonomous_run_scenario_id           varchar(255),
              autonomous_run_scope_asset_group_id  varchar(255),
              autonomous_run_xtm_session_id        varchar(255),
              autonomous_run_xtm_agent_slug        varchar(255),
              autonomous_run_last_error            text,
              autonomous_run_created_at            timestamp NOT NULL DEFAULT now(),
              autonomous_run_updated_at            timestamp NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_autonomous_runs_tenant "
              + "ON autonomous_runs (tenant_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_autonomous_runs_simulation "
              + "ON autonomous_runs (autonomous_run_simulation_id);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS autonomous_events (
              autonomous_event_id         varchar(255) NOT NULL
                  CONSTRAINT autonomous_events_pkey PRIMARY KEY,
              tenant_id                   varchar(255) NOT NULL
                  CONSTRAINT autonomous_events_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              autonomous_event_run_id     varchar(255) NOT NULL,
              autonomous_event_sequence   bigint NOT NULL,
              autonomous_event_type       varchar(255) NOT NULL,
              autonomous_event_title      text,
              autonomous_event_content    text,
              autonomous_event_data       jsonb,
              autonomous_event_created_at timestamp NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_autonomous_events_run_seq "
              + "ON autonomous_events (autonomous_event_run_id, autonomous_event_sequence);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_autonomous_events_tenant "
              + "ON autonomous_events (tenant_id);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS autonomous_directives (
              autonomous_directive_id          varchar(255) NOT NULL
                  CONSTRAINT autonomous_directives_pkey PRIMARY KEY,
              tenant_id                        varchar(255) NOT NULL
                  CONSTRAINT autonomous_directives_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              autonomous_directive_run_id      varchar(255) NOT NULL,
              autonomous_directive_content     text NOT NULL,
              autonomous_directive_status      varchar(255) NOT NULL,
              autonomous_directive_created_at  timestamp NOT NULL DEFAULT now(),
              autonomous_directive_consumed_at timestamp
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_autonomous_directives_run_status "
              + "ON autonomous_directives (autonomous_directive_run_id, "
              + "autonomous_directive_status);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_autonomous_directives_tenant "
              + "ON autonomous_directives (tenant_id);");

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS autonomous_objective_templates (
              autonomous_objective_template_id              varchar(255) NOT NULL
                  CONSTRAINT autonomous_objective_templates_pkey PRIMARY KEY,
              tenant_id                                     varchar(255) NOT NULL
                  CONSTRAINT autonomous_objective_templates_tenant_fk
                      REFERENCES tenants (tenant_id) ON DELETE CASCADE,
              autonomous_objective_template_key             varchar(255) NOT NULL,
              autonomous_objective_template_label           varchar(255) NOT NULL,
              autonomous_objective_template_description     text,
              autonomous_objective_template_icon            varchar(255),
              autonomous_objective_template_prompt          text NOT NULL,
              autonomous_objective_template_kill_chain_focus varchar(255),
              autonomous_objective_template_scope_mode      varchar(255) NOT NULL DEFAULT 'environment',
              autonomous_objective_template_builtin         boolean NOT NULL DEFAULT false,
              autonomous_objective_template_enabled         boolean NOT NULL DEFAULT true,
              autonomous_objective_template_order           integer NOT NULL DEFAULT 0,
              autonomous_objective_template_created_at      timestamp NOT NULL DEFAULT now(),
              autonomous_objective_template_updated_at      timestamp NOT NULL DEFAULT now()
          );
          """);
      // Self-heal dev/staging databases that applied an earlier stamp of this migration which
      // created the table WITHOUT scope_mode: CREATE TABLE IF NOT EXISTS no-ops on the existing
      // table, so add the column idempotently. Constant-default ADD COLUMN is metadata-only on
      // PG 11+, and this is a no-op on fresh installs where CREATE TABLE already added it.
      statement.execute(
          "ALTER TABLE autonomous_objective_templates "
              + "ADD COLUMN IF NOT EXISTS autonomous_objective_template_scope_mode "
              + "varchar(255) NOT NULL DEFAULT 'environment';");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_autonomous_obj_tpl_tenant_key "
              + "ON autonomous_objective_templates (tenant_id, "
              + "autonomous_objective_template_key);");
    }
  }
}
