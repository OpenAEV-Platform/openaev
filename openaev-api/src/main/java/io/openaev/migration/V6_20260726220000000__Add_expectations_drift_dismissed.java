package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code *_expectations_drift_dismissed} flags on scenarios, exercises and injects: a
 * user-acknowledged expectation drift (expectations customized on purpose) downgrades the drift
 * warning to a discreet icon instead of the full button. Persisted in database so the dismissal is
 * shared between users; reset on realignment. Additive and idempotent (constant-default ADD COLUMN
 * is metadata-only).
 */
@Component
public class V6_20260726220000000__Add_expectations_drift_dismissed extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS scenario_expectations_drift_dismissed"
              + " bool NOT NULL DEFAULT false;");
      statement.execute(
          "ALTER TABLE exercises ADD COLUMN IF NOT EXISTS exercise_expectations_drift_dismissed"
              + " bool NOT NULL DEFAULT false;");
      statement.execute(
          "ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_expectations_drift_dismissed"
              + " bool NOT NULL DEFAULT false;");
    }
  }
}
