package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Widens {@code finding_triage_histories} (V6_20260731130000000__Add_finding_triage) so it can also
 * record manual archive/un-archive events (FindingArchiveService), not just triage status
 * transitions - giving the "Triage History" tab a single unified activity timeline per product
 * feedback, instead of archive actions being invisible there.
 */
@Component
public class V6_20260817124500000__Add_finding_triage_history_action extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              DO $$
              BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'finding_history_action_type') THEN
                      CREATE TYPE finding_history_action_type AS ENUM ('TRIAGE_CHANGE', 'ARCHIVE', 'UNARCHIVE');
                END IF;
               END;
              $$;
              """);

      // Existing rows are all pre-archive-history, plain triage transitions - default + backfill
      // to TRIAGE_CHANGE.
      statement.execute(
          """
              ALTER TABLE finding_triage_histories
                  ADD COLUMN IF NOT EXISTS finding_triage_history_action finding_history_action_type NOT NULL DEFAULT 'TRIAGE_CHANGE';
              """);

      // Archive/un-archive rows have no "triage status" to describe - relax the columns that were
      // NOT NULL under the triage-only assumption.
      statement.execute(
          """
              ALTER TABLE finding_triage_histories
                  ALTER COLUMN finding_triage_history_from_status DROP NOT NULL,
                  ALTER COLUMN finding_triage_history_to_status DROP NOT NULL;
              """);
    }
  }
}
