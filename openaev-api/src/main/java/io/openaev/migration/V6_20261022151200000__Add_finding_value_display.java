package io.openaev.migration;

import io.openaev.service.finding.FindingValueDisplayBackfill;
import java.sql.Connection;
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
 * <p>The backfill is {@link FindingValueDisplayBackfill#fill}, which masks each value in Java with
 * the helper every write path uses, rather than with SQL: copying the masking rule into SQL would
 * let the two drift apart. Calling application code from a migration is deliberate here, because
 * the column must always hold the current masking rule. It runs OUTSIDE a wrapping Flyway
 * transaction, so each batch commits on its own and locks stay short on large tables.
 *
 * <p>During a rolling deploy, pods of the previous version keep inserting findings without the
 * column after this migration has run, so the migration also asks for the same fill once more,
 * after the old pods are gone ({@link FindingValueDisplayBackfill#requestDelayedFill}).
 */
@Component
public class V6_20261022151200000__Add_finding_value_display extends BaseJavaMigration {

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
    FindingValueDisplayBackfill.fill(connection);
    // Created after the backfill, so the index is built once instead of being updated row by row.
    // It serves distinct grouping and equality filters, which compare (type, displayed value).
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_type_value_display"
              + " ON findings (finding_type, finding_value_display)");
    }
    FindingValueDisplayBackfill.requestDelayedFill();
  }
}
