package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Removes deprecated {@code subtype} from payload arguments in payloads and step snapshots. */
@Component
public class V6_20260710150000000__Remove_Argument_Subtype_And_Unwrap_Json
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
          UPDATE payloads
          SET payload_arguments = (
            SELECT COALESCE(
              json_agg((argument::jsonb - 'subtype')::json),
              '[]'::json
            )
            FROM json_array_elements(payload_arguments) argument
          )
          WHERE payload_arguments IS NOT NULL
            AND json_typeof(payload_arguments) = 'array'
            AND EXISTS (
              SELECT 1
              FROM json_array_elements(payload_arguments) argument
              WHERE argument::jsonb ? 'subtype'
            );
          """);

      stmt.execute(
          """
          UPDATE steps
          SET step_data = jsonb_set(
            step_data::jsonb,
            '{inject_injector_contract,injector_contract_payload,payload_arguments}',
            (
              SELECT COALESCE(
                jsonb_agg(argument - 'subtype'),
                '[]'::jsonb
              )
              FROM jsonb_array_elements(
                step_data::jsonb #> '{inject_injector_contract,injector_contract_payload,payload_arguments}'
              ) argument
            )
          )
          WHERE step_data IS NOT NULL
            AND jsonb_typeof(
              step_data::jsonb #> '{inject_injector_contract,injector_contract_payload,payload_arguments}'
            ) = 'array'
            AND EXISTS (
              SELECT 1
              FROM jsonb_array_elements(
                step_data::jsonb #> '{inject_injector_contract,injector_contract_payload,payload_arguments}'
              ) argument
              WHERE argument ? 'subtype'
            );
          """);

    }
  }
}
