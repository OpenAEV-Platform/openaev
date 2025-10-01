package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_74__OpenBAS extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Migration the data
    select.executeUpdate(
        """
        UPDATE injects
        SET inject_type = REPLACE(inject_type, 'openex_', 'openaev_')
        WHERE inject_type LIKE 'openex_%';
        """);
  }
}
