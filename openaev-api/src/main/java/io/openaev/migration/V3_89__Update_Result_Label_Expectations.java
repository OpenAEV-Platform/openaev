package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_89__Update_Result_Label_Expectations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      String updateInjectExpectationResult =
          """
                UPDATE injects_expectations
                SET inject_expectation_results = (
                    SELECT jsonb_agg(
                        jsonb_set(
                            elem,
                            '{result}',
                            to_jsonb(
                                CASE
                                    WHEN elem->>'result' = 'FAILED' AND injects_expectations.inject_expectation_type = 'PREVENTION' THEN 'Not Prevented'
                                    WHEN elem->>'result' = 'FAILED' AND injects_expectations.inject_expectation_type = 'DETECTION' THEN 'Not Detected'
                                    WHEN elem->>'result' = 'SUCCESS' AND injects_expectations.inject_expectation_type = 'PREVENTION' THEN 'Prevented'
                                    WHEN elem->>'result' = 'SUCCESS' AND injects_expectations.inject_expectation_type = 'DETECTION' THEN 'Detected'
                                    ELSE elem->>'result'
                                END
                            )
                        )
                    )
                    FROM jsonb_array_elements(injects_expectations.inject_expectation_results::jsonb) AS elem
                )
                WHERE jsonb_typeof(inject_expectation_results::jsonb) = 'array'
                  AND jsonb_array_length(inject_expectation_results::jsonb) > 0
                  AND inject_expectation_type IN ('PREVENTION', 'DETECTION');
            """;

      statement.executeUpdate(updateInjectExpectationResult);
    }
  }
}
