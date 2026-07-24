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
      String normalizePrimitiveLabelsFromArray =
          "(SELECT COALESCE(jsonb_agg(to_jsonb(CASE "
              + "WHEN value = 'TargetedAsset' THEN 'targeted-asset' "
              + "WHEN value = 'targeted_asset' THEN 'targeted-asset' "
              + "ELSE lower(regexp_replace(value, '([a-z0-9])([A-Z])', '\\1_\\2', 'g')) "
              + "END)), '[]'::jsonb) "
              + "FROM jsonb_array_elements_text(%s) AS element(value))";

      statement.addBatch(
          "ALTER TABLE conditions ADD COLUMN IF NOT EXISTS condition_key_types jsonb;");
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
      String isEnvelopeForConditionKeyTypes =
          "jsonb_typeof(condition_key_types::jsonb) = 'object' "
              + "AND (condition_key_types::jsonb) ? 'type' "
              + "AND (condition_key_types::jsonb) ? 'value' "
              + "AND (condition_key_types::jsonb) ? 'null' "
              + "AND (condition_key_types::jsonb ->> 'type') IN ('json', 'jsonb')";
      statement.addBatch(
          "UPDATE conditions "
              + "SET condition_key_types = ((condition_key_types::jsonb) ->> 'value')::jsonb "
              + "WHERE condition_key_types IS NOT NULL AND ("
              + isEnvelopeForConditionKeyTypes
              + ");");
      statement.addBatch(
          "UPDATE conditions "
              + "SET condition_key_types = "
              + String.format(normalizePrimitiveLabelsFromArray, "condition_key_types::jsonb")
              + " "
              + "WHERE condition_key_types IS NOT NULL "
              + "  AND jsonb_typeof(condition_key_types::jsonb) = 'array';");
      statement.addBatch(
          "DO $$ BEGIN "
              + "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'conditions_key_types_no_json_envelope') THEN "
              + "ALTER TABLE conditions "
              + "ADD CONSTRAINT conditions_key_types_no_json_envelope "
              + "CHECK (condition_key_types IS NULL OR NOT ("
              + isEnvelopeForConditionKeyTypes
              + ")) NOT VALID; "
              + "ALTER TABLE conditions VALIDATE CONSTRAINT conditions_key_types_no_json_envelope; "
              + "END IF; END $$;");
      String isEnvelopeForStepConditionKeyTypes =
          "jsonb_typeof(step_condition_key_types::jsonb) = 'object' "
              + "AND (step_condition_key_types::jsonb) ? 'type' "
              + "AND (step_condition_key_types::jsonb) ? 'value' "
              + "AND (step_condition_key_types::jsonb) ? 'null' "
              + "AND (step_condition_key_types::jsonb ->> 'type') IN ('json', 'jsonb')";
      statement.addBatch(
          "UPDATE steps "
              + "SET step_condition_key_types = ((step_condition_key_types::jsonb) ->> 'value')::jsonb "
              + "WHERE step_condition_key_types IS NOT NULL AND ("
              + isEnvelopeForStepConditionKeyTypes
              + ");");
      statement.addBatch(
          "UPDATE steps "
              + "SET step_condition_key_types = "
              + String.format(normalizePrimitiveLabelsFromArray, "step_condition_key_types::jsonb")
              + " "
              + "WHERE step_condition_key_types IS NOT NULL "
              + "  AND jsonb_typeof(step_condition_key_types::jsonb) = 'array';");
      statement.addBatch(
          "DO $$ BEGIN "
              + "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'steps_condition_key_types_no_jsonb_envelope') THEN "
              + "ALTER TABLE steps "
              + "ADD CONSTRAINT steps_condition_key_types_no_jsonb_envelope "
              + "CHECK (step_condition_key_types IS NULL OR NOT ("
              + isEnvelopeForStepConditionKeyTypes
              + ")) NOT VALID; "
              + "ALTER TABLE steps VALIDATE CONSTRAINT steps_condition_key_types_no_jsonb_envelope; "
              + "END IF; END $$;");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_conditions_key_types_gin ON conditions USING GIN (condition_key_types);");
      statement.addBatch("DROP INDEX IF EXISTS idx_conditions_key_type;");
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
