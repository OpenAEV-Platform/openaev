package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code availableExpectations} to the Expectations field in {@code injector_contract_content}
 * for all injector contracts linked to a payload.
 *
 * <p>Payload-based contracts should always expose the three standard technical expectation types
 * (DETECTION, PREVENTION, VULNERABILITY) as available choices. {@code availableExpectations} array.
 */
@Component
public class V6_20260717000000000__Add_available_expectations_on_payload_contracts
    extends BaseJavaMigration {

  private static final String AVAILABLE_EXPECTATIONS_JSON =
      """
      [
        {"expectation_type":"DETECTION","expectation_name":"Detection","expectation_score":100.0,"expectation_expectation_group":false,"expectation_expiration_time":21600,"expectation_is_multi_selectable":false},
        {"expectation_type":"PREVENTION","expectation_name":"Prevention","expectation_score":100.0,"expectation_expectation_group":false,"expectation_expiration_time":21600,"expectation_is_multi_selectable":false},
        {"expectation_type":"VULNERABILITY","expectation_name":"Vulnerability","expectation_score":100.0,"expectation_expectation_group":false,"expectation_expiration_time":21600,"expectation_is_multi_selectable":false}
      ]
      """;

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    String availableJson = AVAILABLE_EXPECTATIONS_JSON.replace("\n", "").strip();

    try (Statement stmt = connection.createStatement()) {
      // For payload-linked contracts that have an expectations field but missing/null
      // availableExpectations: add the availableExpectations array with the 3 standard types.
      stmt.execute(
          """
          UPDATE injectors_contracts
          SET injector_contract_content = jsonb_set(
              injector_contract_content::jsonb,
              ARRAY['fields', idx::text, 'availableExpectations'],
              '%s'::jsonb
          )::text
          FROM (
              SELECT ic.injector_contract_id, pos.idx
              FROM injectors_contracts ic,
                   LATERAL (
                       SELECT ordinality - 1 AS idx
                       FROM jsonb_array_elements(ic.injector_contract_content::jsonb -> 'fields')
                            WITH ORDINALITY AS elem(val, ordinality)
                       WHERE elem.val ->> 'key' = 'expectations'
                         AND (elem.val -> 'availableExpectations' IS NULL
                              OR elem.val -> 'availableExpectations' = 'null'::jsonb
                              OR jsonb_array_length(elem.val -> 'availableExpectations') = 0)
                       LIMIT 1
                   ) pos
              WHERE ic.injector_contract_payload IS NOT NULL
                AND ic.injector_contract_content IS NOT NULL
                AND ic.injector_contract_content::jsonb -> 'fields' IS NOT NULL
          ) sub
          WHERE injectors_contracts.injector_contract_id = sub.injector_contract_id
          """
              .formatted(availableJson));
    }
  }
}
