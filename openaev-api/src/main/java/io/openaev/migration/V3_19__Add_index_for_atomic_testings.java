package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_19__Add_index_for_atomic_testings extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement createIndex = connection.createStatement();
    createIndex.execute(
        "CREATE INDEX idx_null_exercise_and_scenario ON injects (inject_id) WHERE inject_scenario IS NULL AND inject_exercise IS NULL;");
  }
}
