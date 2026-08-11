package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code finding_human_updated_at} to {@code findings}: the last time a user acted on a
 * finding (triage status change, comment added/edited/deleted - see FindingTriageService /
 * FindingCommentService), set via a native bulk update (FindingRepository#touchHumanUpdate) so it
 * never triggers {@code finding_updated_at}'s {@code @UpdateTimestamp}. This decouples "Updated at"
 * (human activity, filterable in the findings list) from "Last seen" (scanner detection only,
 * {@code finding_updated_at}), which previously conflated both signals.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 *
 * <p>Also adds a supporting index: the column is filterable/sortable in the findings list
 * (PaginationComponentV2), mirroring the existing index on {@code finding_updated_at}.
 */
@Component
public class V6_20260811091007000__Add_finding_human_updated_at extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_human_updated_at timestamp with"
              + " time zone;");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_human_updated_at"
              + " ON findings (finding_human_updated_at);");
    }
  }
}
