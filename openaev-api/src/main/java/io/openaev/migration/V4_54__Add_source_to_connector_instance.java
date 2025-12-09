package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_54__Add_source_to_connector_instance extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
        CREATE TYPE connector_instance_source AS ENUM ('PROPERTIES_MIGRATION', 'CATALOG_DEPLOYMENT', 'OTHER');
        ALTER TABLE connector_instances ADD COLUMN connector_instance_source connector_instance_source NOT NULL DEFAULT 'OTHER';
        """);
    }
  }
}
