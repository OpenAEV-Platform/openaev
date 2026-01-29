package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_67__Rename_caldera_executor_slug extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "UPDATE catalog_connectors SET catalog_connector_slug = 'openaev_caldera_executor' WHERE catalog_connector_slug = 'openaev_caldera';");
    }
  }
}
