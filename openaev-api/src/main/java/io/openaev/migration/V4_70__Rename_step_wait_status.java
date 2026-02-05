package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_70__Rename_step_wait_status extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                  DO $$
                  BEGIN
                    IF EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'WAIT' AND enumtypid = 'step_status'::regtype) THEN
                      ALTER TYPE step_status RENAME VALUE 'WAIT' TO 'READY';
                    END IF;
                  END;
                  $$;
          """);
    }
  }
}
