package io.openaev.migration;

import io.openaev.database.model.Finding;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code findings.finding_value_display}, the value of a finding as the platform displays it,
 * and fills it for every existing finding.
 *
 * <p>Search, filters, sorts, distinct grouping and notification triggers used to run on the
 * cleartext {@code finding_value}, so they could confirm a secret the platform masks on screen
 * (issue #7893, ADR-017). They now read this column instead.
 *
 * <p>The backfill masks each value with {@link Finding#displayValueOf(String, String)}, the same
 * helper every write path uses, rather than with SQL: copying the masking rule into SQL would let
 * the two drift apart. Calling application code from a migration is deliberate here, because the
 * column must always hold the current masking rule. A type this version does not know gets its
 * whole value masked.
 *
 * <p>Execution is batched ({@value #BATCH_SIZE} rows) and runs OUTSIDE a wrapping Flyway
 * transaction, so each batch commits on its own and locks stay short on large tables. It is
 * idempotent: only rows whose column is still empty are picked up, so an interrupted run resumes
 * where it stopped. The tenant of a row is never touched.
 */
@Component
public class V6_20261022151200000__Add_finding_value_display extends BaseJavaMigration {

  private static final int BATCH_SIZE = 1000;

  /** No wrapping transaction: each batch commits on its own to keep locks bounded. */
  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute("ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_value_display TEXT");
    }
    backfillValueDisplay(connection);
    // Created after the backfill, so the index is built once instead of being updated row by row.
    // It serves distinct grouping and equality filters, which compare (type, displayed value).
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_type_value_display"
              + " ON findings (finding_type, finding_value_display)");
    }
  }

  private void backfillValueDisplay(Connection connection) throws SQLException {
    String selectSql =
        "SELECT finding_id, finding_type, finding_value FROM findings"
            + " WHERE finding_value_display IS NULL LIMIT "
            + BATCH_SIZE;
    String updateSql =
        "UPDATE findings SET finding_value_display = ?"
            + " WHERE finding_id = ? AND finding_value_display IS NULL";
    try (PreparedStatement select = connection.prepareStatement(selectSql);
        PreparedStatement update = connection.prepareStatement(updateSql)) {
      while (true) {
        int batchSize = 0;
        try (ResultSet rows = select.executeQuery()) {
          while (rows.next()) {
            update.setString(
                1,
                Finding.displayValueOf(
                    rows.getString("finding_type"), rows.getString("finding_value")));
            update.setString(2, rows.getString("finding_id"));
            update.addBatch();
            batchSize++;
          }
        }
        if (batchSize == 0) {
          return;
        }
        int updated = 0;
        for (int count : update.executeBatch()) {
          if (count > 0 || count == Statement.SUCCESS_NO_INFO) {
            updated++;
          }
        }
        // A batch that updates nothing would be selected again forever: stop instead.
        if (updated == 0) {
          return;
        }
      }
    }
  }
}
