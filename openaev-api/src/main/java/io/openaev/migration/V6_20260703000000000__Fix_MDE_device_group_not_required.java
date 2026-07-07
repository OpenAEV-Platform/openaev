package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260703000000000__Fix_MDE_device_group_not_required extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          UPDATE catalog_connectors_configuration
          SET connector_configuration_required = false
          WHERE connector_configuration_key = 'EXECUTOR_MDE_DEVICE_GROUP'
          """);
    }
  }
}

// -- ROLLBACK --
// UPDATE catalog_connectors_configuration SET connector_configuration_required = true
// WHERE connector_configuration_key = 'EXECUTOR_MDE_DEVICE_GROUP';
