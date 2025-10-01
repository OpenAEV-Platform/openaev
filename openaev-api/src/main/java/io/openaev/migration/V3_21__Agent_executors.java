package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_21__Agent_executors extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("ALTER TABLE assets DROP CONSTRAINT executor_fk;");
    select.execute(
        "ALTER TABLE assets ADD CONSTRAINT executor_fk FOREIGN KEY (asset_executor) REFERENCES executors(executor_id) ON DELETE CASCADE;");
  }
}
