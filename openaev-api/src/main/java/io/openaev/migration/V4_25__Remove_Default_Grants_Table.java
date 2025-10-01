package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_25__Remove_Default_Grants_Table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement stmt = connection.createStatement();
    stmt.execute("DROP TABLE IF EXISTS groups_default_grants CASCADE;");
  }
}
