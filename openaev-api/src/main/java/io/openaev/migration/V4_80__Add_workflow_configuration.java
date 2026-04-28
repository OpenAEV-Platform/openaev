package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_80__Add_workflow_configuration extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // -- Enum types --

      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'scope_rule_value_type') THEN
              CREATE TYPE scope_rule_value_type AS ENUM ('IP', 'IP_SUBNET', 'DOMAIN', 'ASSET_ID', 'ASSET_GROUP_ID');
            END IF;
          END
          $$;
          """);

      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'scope_rule_selected_mode') THEN
              CREATE TYPE scope_rule_selected_mode AS ENUM ('ALLOWLIST', 'DENYLIST');
            END IF;
          END
          $$;
          """);

      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'scope_rule_source') THEN
              CREATE TYPE scope_rule_source AS ENUM ('ASSET', 'ASSET_GROUP', 'MANUAL', 'CSV');
            END IF;
          END
          $$;
          """);

      stmt.execute(
          """
          ALTER TABLE workflows
            ADD COLUMN IF NOT EXISTS workflow_rate_limit_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
            ADD COLUMN IF NOT EXISTS workflow_max_attempts              INTEGER,
            ADD COLUMN IF NOT EXISTS workflow_max_temporal_rate_seconds BIGINT,
            ADD COLUMN IF NOT EXISTS workflow_timeout_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
            ADD COLUMN IF NOT EXISTS workflow_timeout_seconds           BIGINT,
            ADD COLUMN IF NOT EXISTS workflow_safe_mode_enabled         BOOLEAN NOT NULL DEFAULT FALSE;
          """);

      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS workflow_scope_rules (
            workflow_scope_rule_id            VARCHAR(255) NOT NULL
              CONSTRAINT workflow_scope_rules_pkey PRIMARY KEY,
            workflow_scope_rule_selected_mode scope_rule_selected_mode,
            workflow_scope_rule_source        scope_rule_source,
            workflow_scope_rule_value         VARCHAR(255),
            workflow_scope_rule_value_type    scope_rule_value_type,
            workflow_scope_rule_created_at    TIMESTAMP WITH TIME ZONE DEFAULT now(),
            workflow_scope_rule_updated_at    TIMESTAMP WITH TIME ZONE DEFAULT now(),
            workflow_id                       VARCHAR(255)
              REFERENCES workflows(workflow_id) ON DELETE CASCADE
          );
          """);

      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_workflow_scope_rules_workflow_id
            ON workflow_scope_rules(workflow_id);
          """);
    }
  }
}
