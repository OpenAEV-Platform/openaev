package io.openaev.migration;

import io.openaev.utils.MigrationTableLocks;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Read-only marker on injector contracts and payloads. Both default to false, so every existing row
 * behaves as before: nothing is read-only until it is marked.
 *
 * <p>During a rolling upgrade the platform on the earlier version reads both tables in one
 * transaction, {@code payloads} first (a collector's payload upsert) or {@code injectors_contracts}
 * first (a contract with its eager payload). The migration takes both locks up front without
 * waiting for them, and gives way to the platform when one is held (see {@link
 * MigrationTableLocks}).
 */
@Component
public class V6_20261121160000000__Coverage_scenario_safe_mode_and_approval
    extends BaseJavaMigration {

  private final MigrationTableLocks.Retry retry;

  public V6_20261121160000000__Coverage_scenario_safe_mode_and_approval() {
    this(MigrationTableLocks.RETRY);
  }

  V6_20261121160000000__Coverage_scenario_safe_mode_and_approval(MigrationTableLocks.Retry retry) {
    this.retry = retry;
  }

  @Override
  public void migrate(Context context) throws Exception {
    MigrationTableLocks.runLocked(
        context.getConnection(),
        "Read-only marker columns",
        List.of("payloads", "injectors_contracts"),
        statement -> {
          statement.execute(
              "ALTER TABLE injectors_contracts ADD COLUMN IF NOT EXISTS"
                  + " injector_contract_read_only BOOLEAN NOT NULL DEFAULT false");
          statement.execute(
              "ALTER TABLE payloads"
                  + " ADD COLUMN IF NOT EXISTS payload_read_only BOOLEAN NOT NULL DEFAULT false");
        },
        retry);
  }
}
