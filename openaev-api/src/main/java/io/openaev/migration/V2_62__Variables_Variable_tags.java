package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_62__Variables_Variable_tags extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add Variable table
    select.execute(
        """
        CREATE TABLE IF NOT EXISTS variables (
            variable_id varchar(255) not null constraint variables_pkey primary key,
            variable_key varchar(255) not null,
            variable_value varchar(255),
            variable_description text,
            variable_type INT not null,
            variable_exercise varchar(255) default NULL::character varying constraint fk_exercice_id references exercises on delete cascade,
            variable_created_at timestamp not null default now(),
            variable_updated_at timestamp not null default now()
        );
        CREATE INDEX IF NOT EXISTS idx_variable_exercise on variables (variable_exercise);
        """);
  }
}
