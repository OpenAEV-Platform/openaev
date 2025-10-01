package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_22__Add_octi_stix_objects extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE security_coverages (
              security_coverage_id VARCHAR(255) NOT NULL CONSTRAINT security_coverage_pkey PRIMARY KEY,
              security_coverage_external_id VARCHAR(255) NOT NULL,
              security_coverage_scenario VARCHAR(255) REFERENCES scenarios(scenario_id) ON DELETE SET NULL,
              security_coverage_name VARCHAR(255) NOT NULL,
              security_coverage_description TEXT,
              security_coverage_scheduling VARCHAR(50) NOT NULL,
              security_coverage_period_start TIMESTAMPTZ,
              security_coverage_period_end TIMESTAMPTZ,
              security_coverage_threat_context_ref VARCHAR(255) NOT NULL,
              security_coverage_labels text[],
              security_coverage_attack_pattern_refs JSONB,
              security_coverage_vulnerabilities_refs JSONB,
              security_coverage_content JSONB NOT NULL,
              security_coverage_created_at TIMESTAMPTZ DEFAULT now(),
              security_coverage_updated_at TIMESTAMPTZ DEFAULT now()
          );
          """);

      statement.execute(
          """
          ALTER TABLE exercises
          ADD COLUMN exercise_security_coverage VARCHAR(255),
          ADD CONSTRAINT fk_exercise_security_coverage
              FOREIGN KEY (exercise_security_coverage)
              REFERENCES security_coverages(security_coverage_id)
              ON DELETE SET NULL;
          """);
    }
  }
}
