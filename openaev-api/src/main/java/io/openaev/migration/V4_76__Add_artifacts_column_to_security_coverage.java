package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_76__Add_artifacts_column_to_security_coverage extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "ALTER TABLE security_coverages ADD COLUMN IF NOT EXISTS security_coverage_artifacts_refs JSONB;");

      stmt.execute(
          """
        UPDATE security_coverages
        SET security_coverage_vulnerabilities_refs = (
            CASE
                WHEN security_coverage_vulnerabilities_refs IS NULL\s
                     OR jsonb_array_length(security_coverage_vulnerabilities_refs) = 0\s
                THEN security_coverage_vulnerabilities_refs

                ELSE (
                    SELECT jsonb_agg(
                        jsonb_set(
                            elem - 'external_ref',
                            '{external_refs}',
                            jsonb_build_array(elem->>'external_ref')
                        )
                    )
                    FROM jsonb_array_elements(security_coverage_vulnerabilities_refs) AS elem
                )
            END
        );
      """);

      stmt.execute(
          """
        UPDATE security_coverages
        SET security_coverage_attack_pattern_refs = (
            CASE
                WHEN security_coverage_attack_pattern_refs IS NULL\s
                     OR jsonb_array_length(security_coverage_attack_pattern_refs) = 0\s
                THEN security_coverage_attack_pattern_refs

                ELSE (
                    SELECT jsonb_agg(
                        jsonb_set(
                            elem - 'external_ref',
                            '{external_refs}',
                            jsonb_build_array(elem->>'external_ref')
                        )
                    )
                    FROM jsonb_array_elements(security_coverage_attack_pattern_refs) AS elem
                )
            END
        );
      """);

      stmt.execute(
          """
        UPDATE security_coverages
        SET security_coverage_indicators_refs = (
            CASE
                WHEN security_coverage_indicators_refs IS NULL\s
                     OR jsonb_array_length(security_coverage_indicators_refs) = 0\s
                THEN security_coverage_indicators_refs

                ELSE (
                    SELECT jsonb_agg(
                        jsonb_set(
                            elem - 'external_ref',
                            '{external_refs}',
                            jsonb_build_array(elem->>'external_ref')
                        )
                    )
                    FROM jsonb_array_elements(security_coverage_indicators_refs) AS elem
                )
            END
        );
      """);
    }
  }
}
