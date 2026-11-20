package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Keeps the first value of a list saved in a single-choice inject field, which the inject saving
 * now refuses. Only the few injects holding such a list are rewritten.
 */
@Component
public class V6_20261120120000000__Unwrap_lists_in_single_choice_inject_fields
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          WITH unwrapped AS (
            SELECT i.inject_id, i.tenant_id,
                   jsonb_object_agg(
                     field->>'key',
                     COALESCE(i.inject_content::jsonb -> (field->>'key') -> 0, 'null'::jsonb)
                   ) AS fields
            FROM injects i
            JOIN injectors_contracts ic
              ON ic.injector_contract_id = i.inject_injector_contract
             AND ic.tenant_id = i.tenant_id
            CROSS JOIN LATERAL jsonb_array_elements(
              ic.injector_contract_content::jsonb -> 'fields') AS field
            WHERE field->>'type' IN ('select', 'choice', 'dependency-select')
              AND field->>'cardinality' = '1'
              AND jsonb_typeof(i.inject_content::jsonb -> (field->>'key')) = 'array'
            GROUP BY i.inject_id, i.tenant_id
          )
          UPDATE injects i
          SET inject_content = i.inject_content::jsonb || unwrapped.fields
          FROM unwrapped
          WHERE i.inject_id = unwrapped.inject_id
            AND i.tenant_id = unwrapped.tenant_id
          """);
    }
  }
}
