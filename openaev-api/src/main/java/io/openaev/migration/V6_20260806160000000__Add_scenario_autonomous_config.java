package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the nullable {@code scenario_autonomous_config} JSON column to {@code scenarios}.
 *
 * <p>Stores the serialized autonomous-run configuration (objective, specialist agents + discovery
 * modes, allow/deny scope, time budget) an operator set up in the AI builder but has not yet
 * launched. This lets the parameters be persisted to "build later" WITHOUT parking a CREATED
 * autonomous run (which would lock the scenario into the AI cockpit). The scenario stays a normal,
 * editable chained scenario until it is actually planned or launched.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} with no default is
 * metadata-only on PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a
 * no-op. No backfill.
 */
@Component
public class V6_20260806160000000__Add_scenario_autonomous_config extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS scenario_autonomous_config jsonb;");
    }
  }
}
