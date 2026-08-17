package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code finding_archived_at} to {@code findings}: a nullable timestamp set when a user
 * manually archives a finding (bulk "Archive" action on the Finding page) and cleared back to
 * {@code NULL} on "Un-archive". This is a manual/persisted signal, distinct from the existing
 * frontend-computed "Archived" badge (derived purely from {@code finding_updated_at} vs. the
 * tenant's configurable archive-days threshold, see FindingApi's archive-days setting) - the badge
 * is the union of both: a finding shows as Archived if this column is set OR if it has not been
 * re-detected for longer than the threshold.
 *
 * <p>If a newer occurrence of the same (type, value) later becomes the representative row shown in
 * the aggregated list, that new row has {@code finding_archived_at} unset, so it naturally reverts
 * to ACTIVE without any extra reconciliation logic - consistent with treating archive as a
 * per-occurrence flag, same as triage status.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260814163808000__Add_finding_archived_at extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_archived_at timestamp with time"
              + " zone;");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_archived_at ON findings (finding_archived_at);");
    }
  }
}
