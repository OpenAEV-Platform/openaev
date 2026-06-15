package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_20__Add_delete_cascade_exercise_email_reply_to extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE exercise_mails_reply_to DROP CONSTRAINT IF EXISTS fk_exercise_id");
      statement.execute(
          "ALTER TABLE exercise_mails_reply_to "
              + "ADD CONSTRAINT fk_exercise_id "
              + "FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id) ON DELETE CASCADE");
    }
  }
}
