package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_56__Update_catalog_connector_configuration_type extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                            CREATE TYPE connector_configuration_type AS ENUM ('ARRAY', 'BOOLEAN', 'INTEGER', 'OBJECT', 'STRING');
                            CREATE TYPE connector_configuration_format AS ENUM ('DATE', 'DATETIME', 'DURATION', 'EMAIL', 'PASSWORD', 'URI');

                            ALTER TABLE catalog_connectors_configuration
                            DROP COLUMN IF EXISTS connector_configuration_type,
                            DROP COLUMN IF EXISTS connector_configuration_format
                            """);

      select.execute(
          """
                            ALTER TABLE catalog_connectors_configuration
                            ADD COLUMN connector_configuration_type connector_configuration_type,
                            ADD COLUMN connector_configuration_format connector_configuration_format
                            """);

      select.execute(
          """
                                  ALTER TABLE connector_instance_logs
                                  RENAME COLUMN connector_configuration_created_at TO connector_instance_log_created_at;
                            """);
    }
  }
}
