package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260729121735000__Add_condition_key_types_jsonb extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      String normalizePrimitiveLabelsFromArray =
          "(SELECT COALESCE(jsonb_agg(to_jsonb("
              + "lower(regexp_replace(raw_value #>> '{}', '([a-z0-9])([A-Z])', '\\1_\\2', 'g'))"
              + ")) FILTER (WHERE jsonb_typeof(raw_value) = 'string'), '[]'::jsonb) "
              + "FROM jsonb_array_elements(%s) AS element(raw_value))";

      // 1. Add new column (nullable — DEFAULT mapper conditions legitimately have no key types).
      statement.addBatch(
          "ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_key_types jsonb;");

      // 2. Backfill from old scalar column when it still exists.
      statement.addBatch(
          "DO $$ BEGIN "
              + "IF EXISTS ("
              + "  SELECT 1 FROM information_schema.columns "
              + "  WHERE table_name = 'conditions' AND column_name = 'condition_key_type'"
              + ") THEN "
              + "  UPDATE conditions "
              + "  SET condition_key_types = jsonb_build_array(condition_key_type) "
              + "  WHERE condition_key_type IS NOT NULL "
              + "    AND (condition_key_types IS NULL OR condition_key_types = '[]'::jsonb); "
              + "END IF; "
              + "END $$;");

      // 3. Normalize labels in existing arrays (camelCase → snake_case).
      statement.addBatch(
          "UPDATE conditions "
              + "SET condition_key_types = "
              + String.format(normalizePrimitiveLabelsFromArray, "condition_key_types::jsonb")
              + " WHERE condition_key_types IS NOT NULL "
              + "  AND jsonb_typeof(condition_key_types::jsonb) = 'array';");

      // 4. Fallback to ["text"] for non-DEFAULT conditions that still have no valid array.
      statement.addBatch(
          "UPDATE conditions "
              + "SET condition_key_types = '[\"text\"]'::jsonb "
              + "WHERE (condition_mapping_type IS NULL OR condition_mapping_type <> 'DEFAULT') "
              + "  AND (condition_key_types IS NULL "
              + "    OR jsonb_typeof(condition_key_types::jsonb) <> 'array' "
              + "    OR jsonb_array_length(condition_key_types::jsonb) = 0);");

      // 5. GIN index for fast array containment queries; drop the old btree index.
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_conditions_key_types_gin ON conditions USING GIN (condition_key_types);");
      statement.addBatch("DROP INDEX IF EXISTS idx_conditions_key_type;");

      // 6. Drop the old scalar column.
      statement.addBatch(
          "DO $$ BEGIN "
              + "IF EXISTS ("
              + "  SELECT 1 FROM information_schema.columns "
              + "  WHERE table_name = 'conditions' AND column_name = 'condition_key_type'"
              + ") THEN "
              + "  ALTER TABLE conditions DROP COLUMN condition_key_type; "
              + "END IF; "
              + "END $$;");

      statement.executeBatch();
    }
  }
}
