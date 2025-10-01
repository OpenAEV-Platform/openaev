package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_17__User_Onboarding_Enable extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
              ALTER TABLE users ADD COLUMN user_onboarding_widget_enable varchar(255) default 'DEFAULT';
              ALTER TABLE users ADD COLUMN user_onboarding_contextual_help_enable varchar(255) default 'DEFAULT';
              CREATE TABLE user_onboarding_progresses (
                onboarding_id UUID PRIMARY KEY,
                user_id varchar(256) NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
                onboarding_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
                onboarding_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
              );
              CREATE TABLE user_onboarding_status (
                user_onboarding_status_onboarding_id UUID NOT NULL REFERENCES user_onboarding_progresses(onboarding_id) ON DELETE CASCADE,
                user_onboarding_status_step TEXT NOT NULL,
                user_onboarding_status_completed BOOLEAN NOT NULL DEFAULT FALSE,
                user_onboarding_status_skipped BOOLEAN NOT NULL DEFAULT FALSE
              );
              """);
    }
  }
}
