package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_04__Detection_Remediation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
                  CREATE TABLE detection_remediations (
                  detection_remediation_id VARCHAR(255) NOT NULL CONSTRAINT detection_remediation_pkey PRIMARY KEY,
                  detection_remediation_collector_id VARCHAR(255) NOT NULL REFERENCES collectors(collector_id) ON DELETE CASCADE,
                  detection_remediation_payload_id VARCHAR(255) NOT NULL REFERENCES payloads(payload_id) ON DELETE CASCADE,
                  detection_remediation_values TEXT,
                  detection_remediation_created_at TIMESTAMPTZ DEFAULT now(),
                  detection_remediation_updated_at TIMESTAMPTZ DEFAULT now()
                );
              """);

      statement.execute(
          "CREATE INDEX idx_detection_remediation_collector ON detection_remediations(detection_remediation_collector_id);");
      statement.execute(
          "CREATE INDEX idx_detection_remediation_payload ON detection_remediations(detection_remediation_payload_id);");
    }
  }
}
