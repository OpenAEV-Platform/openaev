package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the results-push outbox index with the predicate of the outbox poll on databases where
 * an earlier version of {@link V6_20261003170000000__Add_ioc_validations_outbox_indexes} created it
 * without the status condition: Flyway never runs an applied version again, and the wide index kept
 * every rejected validation forever. Idempotent; the table holds the validation history only, so
 * the rebuild is cheap.
 */
@Component
public class V6_20261004103000000__Narrow_ioc_validations_results_push_index
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DROP INDEX IF EXISTS idx_ioc_validations_results_push_pending");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_results_push_pending"
              + " ON ioc_validations (ioc_validation_status)"
              + " WHERE ioc_validation_results_pushed_at IS NULL"
              + " AND ioc_validation_status IN ('COMPLETED', 'PARTIAL', 'FAILED')");
    }
  }
}
