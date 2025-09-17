package io.openbas.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_32__Rollback_User_Onboarding_Enable extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
              DROP TABLE IF EXISTS user_onboarding_status;
              DROP TABLE IF EXISTS user_onboarding_progresses;
              ALTER TABLE users DROP COLUMN IF EXISTS user_onboarding_widget_enable;
              ALTER TABLE users DROP COLUMN IF EXISTS user_onboarding_contextual_help_enable;
              """);
    }
  }
}
