package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Normalizes payload arguments accidentally persisted as a wrapped JSON object:
 *
 * <pre>
 * {"type":"json","value":"[...]","null":false}
 * </pre>
 *
 * <p>The application expects {@code payload_arguments} to contain the raw JSON array directly and
 * payload argument entries without deprecated {@code subtype}.
 */
@Component
public class V6_20260708153000000__Normalize_Payload_Arguments_Wrapped_Json
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
          UPDATE payloads
          SET payload_arguments = (payload_arguments ->> 'value')::json
          WHERE payload_arguments IS NOT NULL
            AND json_typeof(payload_arguments) = 'object'
            AND payload_arguments ->> 'type' = 'json'
            AND payload_arguments -> 'value' IS NOT NULL;
          """);

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
    }
  }
}
