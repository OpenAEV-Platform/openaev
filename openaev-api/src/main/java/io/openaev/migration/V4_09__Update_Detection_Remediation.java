package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_09__Update_Detection_Remediation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {

      // 1. Add new column for collector_type
      statement.execute(
          """
                ALTER TABLE detection_remediations
                ADD COLUMN detection_remediation_collector_type VARCHAR(255);
            """);

      // 2. Backfill collector_type from collector_id
      statement.execute(
          """
                UPDATE detection_remediations dr
                SET detection_remediation_collector_type = c.collector_type
                FROM collectors c
                WHERE dr.detection_remediation_collector_id = c.collector_id;
            """);

      // 3. Drop old collector_id column
      statement.execute(
          """
                ALTER TABLE detection_remediations
                DROP COLUMN detection_remediation_collector_id;
            """);

      // 4. Add FK constraint to collectors(collector_type)
      statement.execute(
          """
                ALTER TABLE detection_remediations
                ADD CONSTRAINT fk_remediation_collector_type
                FOREIGN KEY (detection_remediation_collector_type)
                REFERENCES collectors(collector_type)
                ON DELETE CASCADE;
            """);

      // 5. Add index on collector_type
      statement.execute(
          """
                CREATE INDEX idx_detection_remediation_collector_type
                ON detection_remediations(detection_remediation_collector_type);
            """);
    }
  }
}
