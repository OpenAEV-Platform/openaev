package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_69__Modify_Inject_async_ids extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Modify inject async ids property
    select.execute(
        """
        ALTER TABLE injects_statuses ADD COLUMN status_async_ids text[];
        UPDATE injects_statuses SET status_async_ids = ARRAY[status_async_id];
        ALTER TABLE injects_statuses DROP COLUMN status_async_id;
        """);
  }
}
