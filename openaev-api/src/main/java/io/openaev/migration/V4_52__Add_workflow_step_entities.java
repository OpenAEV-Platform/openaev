package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V4_52__Add_workflow_step_entities extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {

      select.execute(
          """
        CREATE TYPE step_type AS ENUM ('INJECT_EXECUTION', 'TIMESTAMP', 'CONDITION');
        CREATE TABLE steps (
            step_id VARCHAR(255) NOT NULL CONSTRAINT step_pkey PRIMARY KEY,
            step_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            step_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
      select.execute(
          """
        CREATE TABLE edges (
            edge_id VARCHAR(255) NOT NULL CONSTRAINT edge_pkey PRIMARY KEY,
            step_parent_id VARCHAR(255) NOT NULL UNIQUE REFERENCES steps(step_id),
            step_children_id VARCHAR(255) NOT NULL UNIQUE REFERENCES steps(step_id),
            edge_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            edge_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
    }
  }
}
