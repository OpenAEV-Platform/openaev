package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Statement;

@Component
public class V4_99__Add_scope_variables extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add Variable table
    select.execute(
        """
        CREATE TABLE IF NOT EXISTS scope_variables (
            scope_variable_id varchar(255) not null constraint scope_variables_pkey primary key,
            scope_variable_key varchar(255) not null,
            scope_variable_value varchar(255),
            scope_variable_description text,
            scope_variable_type INT not null,
            scope_variable_workflow varchar(255) NOT NULL constraint fk_scope_variable_workflow_id references workflows(workflow_id) on delete cascade,
            scope_variable_created_at timestamp not null default now(),
            scope_variable_updated_at timestamp not null default now()
        );
        CREATE INDEX IF NOT EXISTS idx_variable_exercise on variables (variable_exercise);
        """);
  }
}
