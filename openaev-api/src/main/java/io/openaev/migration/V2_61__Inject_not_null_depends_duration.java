package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_61__Inject_not_null_depends_duration extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
              UPDATE injects
              SET inject_depends_duration = 0
              WHERE inject_depends_duration IS NULL;
        """);
    select.execute(
        """
              ALTER TABLE injects
              ALTER COLUMN inject_depends_duration SET NOT NULL;
        """);
  }
}
