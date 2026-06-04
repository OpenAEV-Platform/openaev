package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code executor_external} column to the {@code executors} table, mirroring the pattern
 * used by {@code injector_external} and {@code collector_external}.
 *
 * <p>No backfill needed: {@code ExecutorService.register()} is called on every backend startup by
 * each integration, and now explicitly sets {@code external} to the correct value each time.
 */
@Component
public class V5_17__Add_executor_external_column extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    statement.execute(
        "ALTER TABLE executors ADD COLUMN IF NOT EXISTS executor_external bool NOT NULL DEFAULT false;");
  }
}
