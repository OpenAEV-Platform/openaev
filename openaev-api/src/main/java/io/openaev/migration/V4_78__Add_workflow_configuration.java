package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_78__Add_workflow_configuration extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "CREATE TABLE workflow_configurations ("
              + "workflow_configuration_id VARCHAR(255) NOT NULL CONSTRAINT workflow_configurations_pkey PRIMARY KEY,"

              // Rate limit
              + "workflow_configuration_rate_limit_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
              + "workflow_configuration_max_attempts INTEGER,"
              + "workflow_configuration_max_temporal_rate_seconds BIGINT,"

              // Timeout
              + "workflow_configuration_timeout_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
              + "workflow_configuration_timeout_seconds BIGINT,"

              // Safe mode
              + "workflow_configuration_safe_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,"

              // Audit
              + "workflow_configuration_created_at TIMESTAMPTZ DEFAULT now(),"
              + "workflow_configuration_updated_at TIMESTAMPTZ DEFAULT now(),"

              // FK to workflows
              + "workflow_configuration_workflow VARCHAR(255) REFERENCES workflows(workflow_id) ON DELETE CASCADE"
              + ")");

      stmt.execute(
          "CREATE INDEX idx_workflow_workflow ON workflow_configurations(workflow_configuration_workflow);");
    }
  }
}
