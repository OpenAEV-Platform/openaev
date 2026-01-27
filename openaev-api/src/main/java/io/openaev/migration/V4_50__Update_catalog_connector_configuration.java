package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_50__Update_catalog_connector_configuration extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                    ALTER TABLE catalog_connectors_configuration
                    DROP COLUMN IF EXISTS connector_configuration_default,
                    DROP COLUMN IF EXISTS connector_configuration_enum
                    """);

      select.execute(
          """
                    ALTER TABLE catalog_connectors_configuration
                    ADD COLUMN connector_configuration_default jsonb,
                    ADD COLUMN connector_configuration_enum text[]
                    """);

      select.execute(
          """
                        ALTER TABLE connector_instance_configurations ADD COLUMN connector_instance_configuration_is_encrypted BOOLEAN DEFAULT false;
                        """);
    }
  }
}
