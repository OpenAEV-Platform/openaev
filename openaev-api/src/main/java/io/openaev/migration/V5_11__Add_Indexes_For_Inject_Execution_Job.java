package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_11__Add_Indexes_For_Inject_Execution_Job extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Partial index on exercises: RUNNING and SCHEDULED simulations are queried every
      // minute by InjectsExecutionJob (executable(), handleAutoStartExercises())
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_exercises_running "
              + "ON exercises (exercise_start_date) "
              + "WHERE exercise_status = 'RUNNING'");

      // Partial index on injects_statuses for PENDING injects: used by
      // InjectSpecification.stalledInjectWithThresholdMinutes() to detect stuck injects.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_statuses_pending_sent_date "
              + "ON injects_statuses (tracking_sent_date) "
              + "WHERE status_name = 'PENDING'");

      // Partial index on injects_statuses for QUEUING injects: used by
      // InjectSpecification.forAtomicTesting() to find atomic tests ready to execute
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_statuses_queuing "
              + "ON injects_statuses (status_inject) "
              + "WHERE status_name = 'QUEUING'");

      // Index on injects_statuses.status_inject to speed up joins/anti-joins from injects to
      // statuses
      // (e.g. InjectSpecification.executable() checks for missing status rows via LEFT JOIN)
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_statuses_status_inject "
              + "ON injects_statuses (status_inject)");

      // Partial index on injects for the executable() spec hot path:
      // enabled injects with a non-null exercise
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_enabled_exercise "
              + "ON injects (inject_exercise) "
              + "WHERE inject_enabled = true AND inject_exercise IS NOT NULL");
    }
  }
}
