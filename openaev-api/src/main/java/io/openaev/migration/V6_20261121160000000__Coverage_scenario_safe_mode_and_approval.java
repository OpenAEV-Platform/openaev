package io.openaev.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Read-only marker on injector contracts and payloads. Both default to false, so every existing row
 * behaves as before: nothing is read-only until it is marked.
 *
 * <p>During a rolling upgrade the platform on the earlier version reads both tables in one
 * transaction, {@code payloads} first (a collector's payload upsert) or {@code injectors_contracts}
 * first (a contract with its eager payload). The migration takes both locks up front, {@code
 * payloads} first, under a lock timeout shorter than the default {@code deadlock_timeout} (1s):
 * when it has to wait, it gives way to the platform, rolls back, pauses and tries again, a bounded
 * number of times.
 */
@Slf4j
@Component
public class V6_20261121160000000__Coverage_scenario_safe_mode_and_approval
    extends BaseJavaMigration {

  private static final String LOCK_TIMEOUT = "500ms";
  private static final int MAX_ATTEMPTS = 30;
  private static final long FIRST_PAUSE_MILLIS = 500;
  private static final long MAX_PAUSE_MILLIS = 10_000;
  private static final Set<String> LOCK_CONFLICTS =
      Set.of(
          "55P03", // lock_not_available: the lock timeout expired
          "40P01"); // deadlock_detected

  /** The migration commits its own transaction, to retry it from the start. */
  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      for (int attempt = 1; ; attempt++) {
        try {
          addColumns(connection);
          connection.commit();
          return;
        } catch (SQLException e) {
          connection.rollback();
          if (!LOCK_CONFLICTS.contains(e.getSQLState()) || attempt == MAX_ATTEMPTS) {
            throw e;
          }
          long pause = Math.min(MAX_PAUSE_MILLIS, FIRST_PAUSE_MILLIS << Math.min(attempt - 1, 10));
          log.info(
              "Read-only marker columns: attempt {} gave way to a lock held by the platform,"
                  + " retrying in {} ms",
              attempt,
              pause);
          Thread.sleep(pause);
        }
      }
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  private static void addColumns(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
      statement.execute("LOCK TABLE payloads, injectors_contracts IN ACCESS EXCLUSIVE MODE");
      statement.execute(
          "ALTER TABLE injectors_contracts"
              + " ADD COLUMN IF NOT EXISTS injector_contract_read_only BOOLEAN NOT NULL DEFAULT false");
      statement.execute(
          "ALTER TABLE payloads"
              + " ADD COLUMN IF NOT EXISTS payload_read_only BOOLEAN NOT NULL DEFAULT false");
    }
  }
}
