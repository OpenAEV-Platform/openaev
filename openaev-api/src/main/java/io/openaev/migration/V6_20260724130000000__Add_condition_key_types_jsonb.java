package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260724130000000__Add_condition_key_types_jsonb extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.addBatch(
          "ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_key_types jsonb;");
      statement.addBatch(
          """
          UPDATE conditions
          SET condition_key_types = jsonb_build_array(condition_key_type)
          WHERE condition_key_type IS NOT NULL
            AND (
              condition_key_types IS NULL
              OR condition_key_types = '[]'::jsonb
            );
          """);
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_conditions_key_types_gin ON conditions USING GIN (condition_key_types);");
      statement.executeBatch();
    }
  }
}
