package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_02__Add_scope_variables extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // Create scope_variables table
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS scope_variables (
              scope_variable_id          varchar(255) NOT NULL CONSTRAINT scope_variables_pkey PRIMARY KEY,
              scope_variable_key         varchar(255) NOT NULL,
              scope_variable_type        varchar(255) NOT NULL,
              scope_variable_value       varchar(255) NOT NULL,
              scope_variable_description varchar(255),
              scope_variable_workflow    varchar(255) NOT NULL
                  CONSTRAINT fk_scope_variable_workflow_id
                  REFERENCES workflows(workflow_id) ON DELETE CASCADE,
              scope_variable_created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              scope_variable_updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
          );
          """);

      // Index on the workflow FK for faster lookups
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_scope_variable_workflow ON scope_variables (scope_variable_workflow);");

      // Unique constraint: a variable key + type combination must be unique per workflow
      stmt.execute(
          "ALTER TABLE scope_variables ADD CONSTRAINT uk_scope_variable_key_type_workflow"
              + " UNIQUE (scope_variable_key, scope_variable_type, scope_variable_workflow);");
    }
  }
}
