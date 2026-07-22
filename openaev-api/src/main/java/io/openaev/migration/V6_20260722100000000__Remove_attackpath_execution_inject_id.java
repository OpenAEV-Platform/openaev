package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Drops the {@code attackpath_execution_inject_id} column from {@code attackpath_execution}.
 *
 * <p>The column was introduced as a debugging aid but is not used by the graph read, the ingestion
 * service, or any repository projection. Removing it reduces row width and avoids confusion with
 * the canonical identity key {@code attackpath_execution_step_id}.
 */
@Component
public class V6_20260722100000000__Remove_attackpath_execution_inject_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (var statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_execution DROP COLUMN IF EXISTS attackpath_execution_inject_id");
    }
  }
}

