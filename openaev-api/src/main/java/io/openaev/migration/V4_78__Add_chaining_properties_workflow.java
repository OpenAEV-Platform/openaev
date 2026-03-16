package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_78__Add_chaining_properties_workflow extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "CREATE TABLE chaining_configurations ("
              + "chaining_configuration_id VARCHAR(255) NOT NULL CONSTRAINT chaining_configurations_pkey PRIMARY KEY,"

              // Rate limit
              + "chaining_configuration_rate_limit_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
              + "chaining_configuration_max_attempts INTEGER,"
              + "chaining_configuration_max_temporal_rate_seconds BIGINT,"

              // Timeout
              + "chaining_configuration_timeout_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
              + "chaining_configuration_timeout_seconds BIGINT,"

              // Safe mode
              + "chaining_configuration_safe_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,"

              // Audit
              + "chaining_configuration_created_at TIMESTAMPTZ DEFAULT now(),"
              + "chaining_configuration_updated_at TIMESTAMPTZ DEFAULT now(),"

              // FK to workflows
              + "chaining_configuration_workflow VARCHAR(255) REFERENCES workflows(workflow_id) ON DELETE CASCADE"
              + ")");

      stmt.execute(
          "CREATE INDEX idx_chaining_workflow ON chaining_configurations(chaining_configuration_workflow);");
    }
  }
}
