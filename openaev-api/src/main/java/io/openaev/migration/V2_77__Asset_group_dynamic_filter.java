package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_77__Asset_group_dynamic_filter extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Migration the data
    select.executeUpdate(
        """
        ALTER TABLE asset_groups ADD COLUMN asset_group_dynamic_filter json;
      """);
  }
}
