package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_13__Update_Role_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement stmt = connection.createStatement();
    stmt.execute(
        """
          ALTER TABLE roles
            ADD COLUMN role_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            ADD COLUMN role_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
          """);
  }
}
