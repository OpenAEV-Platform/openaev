package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_96__Add_mail_from_name extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "ALTER TABLE exercises ADD COLUMN IF NOT EXISTS exercise_mail_from_name VARCHAR(255) DEFAULT 'no-reply'");
      stmt.execute(
          "ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS scenario_mail_from_name VARCHAR(255) DEFAULT 'no-reply'");
    }
  }
}
