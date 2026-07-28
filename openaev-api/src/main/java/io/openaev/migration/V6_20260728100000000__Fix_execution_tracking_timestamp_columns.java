package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Converts the execution-tracking timestamp columns from {@code timestamp without time zone} to
 * {@code timestamp with time zone}.
 *
 * <p>Bug: the Java entities map these columns as {@link java.time.Instant}, which Hibernate binds
 * as a {@code timestamptz} parameter. When the target column is a naive {@code timestamp},
 * PostgreSQL converts the parameter to the JDBC session's time zone (the JVM default) before
 * storing it, while reads interpret the stored literal as UTC. On any server not running in UTC
 * this skews the stored value by the zone offset: e.g. {@code tracking_end_date} lands 2 hours
 * ahead of {@code tracking_sent_date} (already {@code timestamptz}), producing absurd execution
 * durations after a reload even though live SSE updates show the correct value.
 *
 * <p>Fix: align the columns with their siblings as {@code timestamptz}. The session time zone is
 * pinned to UTC for the ALTERs so PostgreSQL interprets existing naive literals as UTC — exactly
 * how the application reads them today, so the visible values of existing rows are unchanged — and
 * so the conversion is a metadata-only change (no table rewrite, PostgreSQL 12+).
 */
@Component
public class V6_20260728100000000__Fix_execution_tracking_timestamp_columns
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("SET LOCAL TimeZone = 'UTC'");
      convertToTimestamptz(statement, "injects_statuses", "tracking_end_date");
      convertToTimestamptz(statement, "injects_tests_statuses", "tracking_sent_date");
      convertToTimestamptz(statement, "injects_tests_statuses", "tracking_end_date");
      convertToTimestamptz(statement, "execution_traces", "execution_time");
    }
  }

  /**
   * Converts the column to {@code timestamptz} only when it is still a naive {@code timestamp}, so
   * the migration is idempotent / re-runnable (already-converted databases skip the ALTER
   * entirely).
   */
  private void convertToTimestamptz(Statement statement, String table, String column)
      throws Exception {
    statement.execute(
        "DO $$ BEGIN "
            + "IF EXISTS (SELECT 1 FROM information_schema.columns "
            + "WHERE table_name = '"
            + table
            + "' AND column_name = '"
            + column
            + "' AND data_type = 'timestamp without time zone') THEN "
            + "ALTER TABLE "
            + table
            + " ALTER COLUMN "
            + column
            + " TYPE timestamp with time zone; "
            + "END IF; "
            + "END $$;");
  }
}
