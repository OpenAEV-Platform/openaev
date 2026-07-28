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
      statement.execute(
          "ALTER TABLE injects_statuses"
              + " ALTER COLUMN tracking_end_date TYPE timestamp with time zone");
      statement.execute(
          "ALTER TABLE injects_tests_statuses"
              + " ALTER COLUMN tracking_sent_date TYPE timestamp with time zone,"
              + " ALTER COLUMN tracking_end_date TYPE timestamp with time zone");
      statement.execute(
          "ALTER TABLE execution_traces"
              + " ALTER COLUMN execution_time TYPE timestamp with time zone");
    }
  }
}
