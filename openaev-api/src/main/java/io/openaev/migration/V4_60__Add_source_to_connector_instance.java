package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_60__Add_source_to_connector_instance extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
        DO $$
        BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'connector_instance_source') THEN
                CREATE TYPE connector_instance_source AS ENUM ('PROPERTIES_MIGRATION', 'CATALOG_DEPLOYMENT', 'OTHER');
            END IF;
        END $$;
        """);

      select.execute(
          """
        ALTER TABLE connector_instances ADD COLUMN IF NOT EXISTS connector_instance_source connector_instance_source NOT NULL DEFAULT 'OTHER';
        """);
    }
  }
}
