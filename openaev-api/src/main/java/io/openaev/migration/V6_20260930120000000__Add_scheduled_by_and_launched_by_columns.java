package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * The actor whose marking clearance gates one specific launch, captured explicitly rather than
 * inferred from a "last edited" field.
 *
 * <p>{@code scheduled_by} = who owns/last confirmed a recurring schedule (Scenario recurrence,
 * Atomic Testing recurrence) — written at recurrence-configuration time, when a live user is
 * always present. {@code launched_by} = the actor a run's dispatch is filtered against (Exercise,
 * Atomic Testing inject) — written at launch/relaunch/scheduled-creation time.
 *
 * <p>All four columns are nullable, {@code ON DELETE SET NULL}, mirroring the existing {@code
 * inject_user} FK: a deleted or never-stamped actor resolves to zero clearance at dispatch time,
 * never an error.
 */
@Component
public class V6_20260930120000000__Add_scheduled_by_and_launched_by_columns
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          ALTER TABLE scenarios
            ADD COLUMN IF NOT EXISTS scenario_scheduled_by VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_scheduled_by"
              + " ON scenarios(scenario_scheduled_by);");

      statement.executeUpdate(
          """
          ALTER TABLE exercises
            ADD COLUMN IF NOT EXISTS exercise_launched_by VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_exercises_launched_by"
              + " ON exercises(exercise_launched_by);");

      statement.executeUpdate(
          """
          ALTER TABLE injects
            ADD COLUMN IF NOT EXISTS inject_scheduled_by VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS inject_launched_by VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_injects_scheduled_by"
              + " ON injects(inject_scheduled_by);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_injects_launched_by"
              + " ON injects(inject_launched_by);");
    }
  }
}
