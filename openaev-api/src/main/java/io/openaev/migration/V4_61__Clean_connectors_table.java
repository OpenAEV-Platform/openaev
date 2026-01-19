package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_61__Clean_connectors_table extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                    ALTER TABLE injectors
                    DROP COLUMN IF EXISTS injector_connector_instance_id;
                    ALTER TABLE collectors
                    DROP COLUMN IF EXISTS collector_connector_instance_id;
                    ALTER TABLE executors
                    DROP COLUMN IF EXISTS executor_connector_instance_id;
                  """);
    }
  }
}
