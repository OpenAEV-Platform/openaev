package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_77__Add_scope_data_model extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
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
                  CREATE TYPE scope_rule_selected_mode AS ENUM ('WHITELIST', 'BLACKLIST');
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
          CREATE TABLE IF NOT EXISTS scopes (
            scope_id VARCHAR(255) NOT NULL CONSTRAINT scopes_pkey PRIMARY KEY,
            scope_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            scope_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            scope_chaining_configuration VARCHAR(255)
              REFERENCES chaining_configurations(chaining_configuration_id) ON DELETE CASCADE
          );
          """);

      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS scope_rules (
            scope_rule_id VARCHAR(255) NOT NULL CONSTRAINT scope_rules_pkey PRIMARY KEY,
            scope_rule_source scope_rule_source,
            scope_rule_value VARCHAR(255),
            scope_rule_value_type scope_rule_value_type,
            scope_rule_selected_mode scope_rule_selected_mode,
            scope_rule_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            scope_rule_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            scope_id VARCHAR(255) REFERENCES scopes(scope_id) ON DELETE CASCADE
          );
          """);

      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_scope_rules_scope_id
            ON scope_rules(scope_id);
          """);

      stmt.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS uk_scopes_chaining_configuration
            ON scopes(scope_chaining_configuration);
          """);
    }
  }
}
