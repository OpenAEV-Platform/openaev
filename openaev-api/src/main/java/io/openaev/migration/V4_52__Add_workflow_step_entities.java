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
        CREATE TABLE inject_templates (
            inject_template_id VARCHAR(255) NOT NULL CONSTRAINT inject_templates_pkey PRIMARY KEY,
            inject_template_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            inject_template_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
      select.execute(
          """
        CREATE TABLE inject_executions (
            inject_execution_id VARCHAR(255) NOT NULL CONSTRAINT inject_execution_pkey PRIMARY KEY,
            inject_execution_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            inject_execution_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
      select.execute(
          """
        CREATE TABLE edges (
            edge_id VARCHAR(255) NOT NULL CONSTRAINT edge_pkey PRIMARY KEY,
            edge_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
            edge_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
          );
        """);
    }
  }
}
