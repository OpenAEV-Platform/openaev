package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code exercise_autonomous} boolean column to {@code exercises}.
 *
 * <p>Durable marker that a simulation was created by an autonomous (orchestrator-driven) run. It
 * survives the teardown of the {@code autonomous_runs} row (rebuild / relaunch supersede), so the
 * simulations history and the simulation hero keep telling Normal from Autonomous long after the
 * run itself is gone.
 *
 * <p>Additive, idempotent, and lock-light: a {@code NOT NULL DEFAULT false} boolean {@code ADD
 * COLUMN} with a constant default is metadata-only on PostgreSQL 11+ (no table rewrite), and {@code
 * IF NOT EXISTS} makes re-running a no-op. No backfill.
 */
@Component
public class V6_20260806170000000__Add_exercise_autonomous extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE exercises ADD COLUMN IF NOT EXISTS exercise_autonomous"
              + " boolean NOT NULL DEFAULT false;");
    }
  }
}
