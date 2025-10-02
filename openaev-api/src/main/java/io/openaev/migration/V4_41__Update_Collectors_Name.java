package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_41__Update_Collectors_Name extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                 ALTER TABLE detection_remediations
                     DROP CONSTRAINT fk_remediation_collector_type;

                 UPDATE collectors
                 SET collector_type = REPLACE(collector_type, 'openbas', 'openaev')
                 WHERE collector_type LIKE '%openbas%';

                 UPDATE detection_remediations
                 SET detection_remediation_collector_type = REPLACE(detection_remediation_collector_type, 'openbas', 'openaev')
                 WHERE detection_remediation_collector_type LIKE '%openbas%';

                 ALTER TABLE detection_remediations
                     ADD CONSTRAINT fk_remediation_collector_type
                         FOREIGN KEY (detection_remediation_collector_type)
                             REFERENCES collectors(collector_type)
                             ON DELETE CASCADE;
              """);
    }
  }
}
