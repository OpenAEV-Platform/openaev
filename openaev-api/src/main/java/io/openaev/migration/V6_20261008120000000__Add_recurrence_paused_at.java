package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Persisted pause of a recurring scenario or atomic testing: set when it stops being launchable (an
 * action not approved, or a sensitive change), cleared only when a user re-enables the schedule.
 * Nullable, no backfill: existing schedules are not paused.
 */
@Component
public class V6_20261008120000000__Add_recurrence_paused_at extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE scenarios
          ADD COLUMN IF NOT EXISTS scenario_recurrence_paused_at TIMESTAMP WITH TIME ZONE;
          """);
      statement.execute(
          """
          ALTER TABLE injects
          ADD COLUMN IF NOT EXISTS inject_recurrence_paused_at TIMESTAMP WITH TIME ZONE;
          """);
    }
  }
}
