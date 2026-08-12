package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Realigns the timeout-sweep index with {@code ExecutionStatus#STALLED_STATUSES}. The {@code V5_11}
 * index was predicated on {@code status_name = 'PENDING'}, which an {@code IN ('PENDING',
 * 'EXECUTING')} query does not imply: Postgres stopped using it, leaving a full scan on every job
 * tick.
 */
@Component
public class V6_20260812100000000__Reindex_stalled_injects_sweep extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_injects_statuses_stalled_sent_date
            ON injects_statuses (tracking_sent_date)
            WHERE status_name IN ('PENDING', 'EXECUTING');
          """);

      // Superseded by the index above: no remaining query filters PENDING alone on
      // tracking_sent_date, so keeping it would only cost write amplification.
      statement.execute("DROP INDEX IF EXISTS idx_injects_statuses_pending_sent_date");
    }
  }
}
