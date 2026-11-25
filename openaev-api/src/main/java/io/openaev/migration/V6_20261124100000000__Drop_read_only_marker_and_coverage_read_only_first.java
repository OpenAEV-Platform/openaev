package io.openaev.migration;

import static io.openaev.utils.MigrationTableLocks.LockMode.ACCESS_EXCLUSIVE;

import io.openaev.utils.MigrationTableLocks;
import io.openaev.utils.MigrationTableLocks.TableLock;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Removes what V6_20261121160000000 added: the read-only marker on injector contracts and payloads,
 * and the coverage_scenario_read_only_first tenant setting rows. V6_20261121160000000 has already
 * run on deployed instances, so it stays as is and this forward migration undoes it. Idempotent.
 *
 * <p>It runs right after V6_20261121160000000 on an upgrade from a release, against the same
 * platform reads of both tables, so it takes its locks the same way (see {@link
 * MigrationTableLocks}).
 */
@Component
public class V6_20261124100000000__Drop_read_only_marker_and_coverage_read_only_first
    extends BaseJavaMigration {

  /** The migration commits its own transaction, to retry it from the start. */
  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    MigrationTableLocks.runLocked(
        context.getConnection(),
        "Read-only marker drop",
        List.of(
            new TableLock("payloads", ACCESS_EXCLUSIVE),
            new TableLock("injectors_contracts", ACCESS_EXCLUSIVE)),
        statement -> {
          statement.execute(
              "ALTER TABLE injectors_contracts DROP COLUMN IF EXISTS injector_contract_read_only");
          statement.execute("ALTER TABLE payloads DROP COLUMN IF EXISTS payload_read_only");
          statement.execute(
              "DELETE FROM parameters WHERE parameter_key = 'coverage_scenario_read_only_first'");
        });
  }
}
