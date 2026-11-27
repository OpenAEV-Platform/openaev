package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * A version that is not approved now behaves like a revoked payload: schedules keep running and the
 * injects on that version fail when they run, so a schedule is never paused for approval.
 *
 * <p>Both columns were created by {@link V6_20261102100200000__Add_recurrence_paused_at} on the
 * payload approval prototype and were never released, so they are dropped without a deprecation
 * step.
 */
@Component
public class V6_20261127100000000__Drop_recurrence_paused_at extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          "ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_recurrence_paused_at;");
      statement.executeUpdate(
          "ALTER TABLE injects DROP COLUMN IF EXISTS inject_recurrence_paused_at;");
    }
  }
}
