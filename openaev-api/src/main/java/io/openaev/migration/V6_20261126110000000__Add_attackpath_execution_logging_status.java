package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Logging result of an attack path execution, next to its prevention, detection and vulnerability
 * results. Nullable with no default: the column is added without rewriting the table, and an
 * existing execution gets its value at the next result of its inject.
 */
@Component
public class V6_20261126110000000__Add_attackpath_execution_logging_status
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_execution"
              + " ADD COLUMN IF NOT EXISTS attackpath_execution_logging_status text");
    }
  }
}
