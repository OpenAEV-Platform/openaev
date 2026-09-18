package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260917152012291__add_email_change_confirmation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("""
        CREATE TABLE user_email_change_confirmations(
            user_id VARCHAR(255) PRIMARY KEY REFERENCES users(user_id),
            confirmation_code VARCHAR(255) NOT NULL,
            confirmation_email VARCHAR(255) NOT NULL,
            confirmation_valid_until TIMESTAMP WITH TIME ZONE NOT NULL
        )
        """);
    }
  }
}
