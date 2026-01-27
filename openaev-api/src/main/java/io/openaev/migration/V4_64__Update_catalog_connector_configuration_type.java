package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_64__Update_catalog_connector_configuration_type extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
          ALTER TYPE connector_configuration_format ADD VALUE IF NOT EXISTS 'DEFAULT' before 'DATE';
          """);
    }
  }
}
