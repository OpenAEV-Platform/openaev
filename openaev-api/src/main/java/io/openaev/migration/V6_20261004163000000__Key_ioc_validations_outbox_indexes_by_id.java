package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Keys the two IOC validation outbox indexes by validation id: the job reads each outbox a bounded
 * page per run in id order from where the previous run stopped, so a page is an index range scan
 * and never sorts the whole pending backlog. The predicates stay those of the outbox queries in
 * IocValidationRepository, statuses as literals. Idempotent; the table holds the validation history
 * only, so the rebuild is cheap.
 */
@Component
public class V6_20261004163000000__Key_ioc_validations_outbox_indexes_by_id
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DROP INDEX IF EXISTS idx_ioc_validations_lifecycle_sync_pending");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_lifecycle_sync_pending"
              + " ON ioc_validations (ioc_validation_id)"
              + " WHERE ioc_validation_opencti_synced_status IS NULL"
              + " OR ioc_validation_opencti_synced_status <> ioc_validation_status");
      statement.execute("DROP INDEX IF EXISTS idx_ioc_validations_results_push_pending");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_results_push_pending"
              + " ON ioc_validations (ioc_validation_id)"
              + " WHERE ioc_validation_results_pushed_at IS NULL"
              + " AND ioc_validation_status IN ('COMPLETED', 'PARTIAL', 'FAILED')");
    }
  }
}
