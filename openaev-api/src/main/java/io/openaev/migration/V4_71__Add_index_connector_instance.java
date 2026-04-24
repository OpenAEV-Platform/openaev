package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_71__Add_index_connector_instance extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      statement.execute(
          """
          CREATE INDEX idx_conf_key
          ON connector_instance_configurations (connector_instance_configuration_key);
          """);

      // GIN index for ?? operator on the value column
      statement.execute(
          """
          CREATE INDEX idx_conf_value_gin
          ON connector_instance_configurations
          USING GIN (connector_instance_configuration_value);
          """);

      // FK join column speeds up the JOINs and DELETE CASCADE operations
      statement.execute(
          """
          CREATE INDEX idx_conf_instance_id
          ON connector_instance_configurations (connector_instance_id);
          """);

      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_cil_instance_id
          ON connector_instance_logs (connector_instance_id);
          """);
    }
  }
}
