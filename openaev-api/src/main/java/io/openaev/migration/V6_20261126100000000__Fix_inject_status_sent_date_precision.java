package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Stores {@code injects_statuses.tracking_sent_date} with microseconds, like {@code
 * tracking_end_date}.
 *
 * <p>The column comes from the initial schema as {@code timestamp(0) with time zone}, so PostgreSQL
 * rounds every start date to the nearest second: an execution shorter than a second whose start
 * falls in the second half of a second gets a start date after its end date. Raising the precision
 * rewrites neither the table nor its indexes. Rows already stored keep their rounded start date.
 */
@Component
public class V6_20261126100000000__Fix_inject_status_sent_date_precision extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          DO $$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'injects_statuses'
                         AND column_name = 'tracking_sent_date'
                         AND datetime_precision = 0) THEN
              ALTER TABLE injects_statuses
                ALTER COLUMN tracking_sent_date TYPE timestamp with time zone;
            END IF;
          END $$;
          """);
    }
  }
}
