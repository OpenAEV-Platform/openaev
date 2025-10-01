package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_60__Delete_fk_users_injects extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
              ALTER TABLE injects
              DROP CONSTRAINT fk_a60839b2e20fc097,
              ADD CONSTRAINT fk_injects_user_id
                  foreign key (inject_user)
                  references users(user_id)
                  on delete set null;
        """);
  }
}
