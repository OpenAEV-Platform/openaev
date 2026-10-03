package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Partial indexes matching the two outbox predicates the IOC validation job polls every few
 * seconds: validations whose status OpenCTI has not acknowledged yet, and finished validations
 * whose result bundle has not been pushed. Once synchronized, rows leave both indexes, so the poll
 * cost follows the pending backlog instead of the whole validation history.
 */
@Component
public class V6_20261003170000000__Add_ioc_validations_outbox_indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_lifecycle_sync_pending"
              + " ON ioc_validations (tenant_id)"
              + " WHERE ioc_validation_opencti_synced_status IS NULL"
              + " OR ioc_validation_opencti_synced_status <> ioc_validation_status");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_results_push_pending"
              + " ON ioc_validations (ioc_validation_status)"
              + " WHERE ioc_validation_results_pushed_at IS NULL");
    }
  }
}
