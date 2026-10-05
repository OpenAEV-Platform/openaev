package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Indexes the running IOC validations by id: the results job reads them a bounded page per run in
 * id order from where the previous run stopped. The predicate stays that of
 * IocValidationRepository.findRunningRefs, status as a literal. Idempotent.
 */
@Component
public class V6_20261005152400000__Add_ioc_validations_running_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ioc_validations_running"
              + " ON ioc_validations (ioc_validation_id)"
              + " WHERE ioc_validation_status = 'RUNNING'");
    }
  }
}
