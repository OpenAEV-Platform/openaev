package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V4_87__Clean_indexing_status extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();

    select.execute("TRUNCATE indexing_status CASCADE;");
  }
}
