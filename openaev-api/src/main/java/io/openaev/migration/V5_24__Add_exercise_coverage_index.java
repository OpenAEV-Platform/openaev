package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds a composite index supporting the tenant-wide MITRE ATT&CK coverage "latest N simulations"
 * lookup ({@code ExerciseRepository.findLatestExerciseIdsByStatus}), which filters on {@code
 * (tenant_id, exercise_status)} and orders by {@code exercise_end_date DESC}. Without it Postgres
 * has to scan and sort a potentially large tenant slice just to return the latest N ids. Partial on
 * {@code exercise_end_date IS NOT NULL} to match the query and keep the index lean.
 */
@Component
public class V5_24__Add_exercise_coverage_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_exercises_tenant_status_end_date "
              + "ON exercises (tenant_id, exercise_status, exercise_end_date) "
              + "WHERE exercise_end_date IS NOT NULL");
    }
  }
}
