package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_97__Add_Eol_Column extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    // ES
    statement.executeUpdate("DELETE FROM indexing_status WHERE indexing_status_type = 'endpoint';");

    statement.execute(
        """
                   ALTER TABLE assets ADD COLUMN endpoint_is_eol BOOLEAN DEFAULT FALSE;
                """);
  }
}
