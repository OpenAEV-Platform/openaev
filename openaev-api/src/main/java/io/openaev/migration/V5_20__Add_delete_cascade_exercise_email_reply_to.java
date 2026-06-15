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
          """
          DO $$
          BEGIN
            -- Drop existing constraint if present
            IF EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname = 'fk_exercise_id'
                AND conrelid = 'exercise_mails_reply_to'::regclass
            ) THEN
              ALTER TABLE exercise_mails_reply_to DROP CONSTRAINT fk_exercise_id;
            END IF;

            -- Re-create with ON DELETE CASCADE if not already present
            IF NOT EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname = 'fk_exercise_id'
                AND conrelid = 'exercise_mails_reply_to'::regclass
            ) THEN
              ALTER TABLE exercise_mails_reply_to
                ADD CONSTRAINT fk_exercise_id
                FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id) ON DELETE CASCADE;
            END IF;
          END $$;
          """);
    }
  }
}
