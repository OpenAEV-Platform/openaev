package io.openaev.migration;

import io.openaev.service.finding.FindingValueDisplayBackfill;
import io.openaev.utils.SensitiveValueMaskingUtils;
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
 * <p>The backfill masks each value with {@link
 * SensitiveValueMaskingUtils#maskIfNeededFailClosed(String, String)}, the same helper every write
 * path uses, rather than with SQL: copying the masking rule into SQL would let the two drift apart.
 * Calling application code from a migration is deliberate here, because the column must always hold
 * the current masking rule. A type this version does not know gets its whole value masked.
 *
 * <p>Execution is batched ({@value #BATCH_SIZE} rows) and runs OUTSIDE a wrapping Flyway
 * transaction, so each batch commits on its own and locks stay short on large tables. The batches
 * walk the table once, in primary key order: each one starts after the last id of the previous one,
 * so no batch scans again the rows already filled. It is idempotent: only rows whose column is
 * still empty are filled, so an interrupted run resumes safely. The tenant of a row is never
 * touched.
 *
 * <p>During a rolling deploy, pods of the previous version keep inserting findings without the
 * column after this migration has run, so the migration also asks for the same fill once more,
 * after the old pods are gone ({@link FindingValueDisplayBackfill#requestDelayedFill}).
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
    FindingValueDisplayBackfill.requestDelayedFill();
  }

  private void backfillValueDisplay(Connection connection) throws SQLException {
    // The cursor on finding_id keeps every batch on the primary key index, starting where the
    // previous batch stopped, instead of scanning again the rows already filled.
    String selectSql =
        "SELECT finding_id, finding_type, finding_value FROM findings"
            + " WHERE finding_id > ? AND finding_value_display IS NULL"
            + " ORDER BY finding_id LIMIT "
            + BATCH_SIZE;
    String updateSql =
        "UPDATE findings SET finding_value_display = ?"
            + " WHERE finding_id = ? AND finding_value_display IS NULL";
    try (PreparedStatement select = connection.prepareStatement(selectSql);
        PreparedStatement update = connection.prepareStatement(updateSql)) {
      String lastId = "";
      while (true) {
        int batchSize = 0;
        select.setString(1, lastId);
        try (ResultSet rows = select.executeQuery()) {
          while (rows.next()) {
            lastId = rows.getString("finding_id");
            update.setString(
                1,
                SensitiveValueMaskingUtils.maskIfNeededFailClosed(
                    rows.getString("finding_type"), rows.getString("finding_value")));
            update.setString(2, lastId);
            update.addBatch();
            batchSize++;
          }
        }
        if (batchSize == 0) {
          return;
        }
        update.executeBatch();
      }
    }
  }
}
