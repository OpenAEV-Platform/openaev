package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_97__Add_mapping_type_to_conditions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'mapping_type') THEN
              CREATE TYPE mapping_type AS ENUM ('DEFAULT', 'LOCAL', 'GLOBAL');
            END IF;
          END
          $$;
          """);

      stmt.execute(
          "ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_mapping_type mapping_type;");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_conditions_mapping_type ON conditions(condition_mapping_type);");
      stmt.execute(
          "UPDATE conditions "
              + "SET condition_key = condition_key_type "
              + "WHERE condition_key IS NULL "
              + "AND condition_type = 'MAPPER';");
    }
  }
}
