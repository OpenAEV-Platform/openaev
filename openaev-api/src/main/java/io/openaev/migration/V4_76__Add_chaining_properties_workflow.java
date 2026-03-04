package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_76__Add_chaining_properties_workflow extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "CREATE TABLE chaining_configurations ("
              + "chaining_configuration_id varchar(255) NOT NULL CONSTRAINT chaining_configurations_pkey PRIMARY KEY,"
              + "chaining_configuration_rate_limit jsonB,"
              + "chaining_configuration_time_out jsonB,"
              + "chaining_configuration_enable_safe_mode boolean,"
              + "chaining_configuration_workflow VARCHAR(255) REFERENCES workflows(workflow_id) ON DELETE CASCADE,"
              + "chaining_configuration_created_at TIMESTAMPTZ DEFAULT now(),"
              + "chaining_configuration_updated_at TIMESTAMPTZ DEFAULT now())");

      stmt.execute(
          "CREATE INDEX idx_chaining_workflow ON chaining_configurations(chaining_configuration_workflow);");
    }
  }
}
