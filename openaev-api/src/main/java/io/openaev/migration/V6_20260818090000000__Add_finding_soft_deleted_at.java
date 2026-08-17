package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the soft-deletion timestamp used by FindingSoftDeleteJob: a finding that has sat manually
 * archived (finding_archived_at) for more than the configured grace period (30 days by default) is
 * marked here, dropping it out of the main Finding page (Active and Archived tabs alike), while it
 * stays fully visible from its originating inject/simulation/scenario views (which never apply this
 * filter) - product spec: archived findings should not accumulate forever on the main list, but
 * must remain traceable from the simulation that found them.
 */
@Component
public class V6_20260818090000000__Add_finding_soft_deleted_at extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE findings
                  ADD COLUMN IF NOT EXISTS finding_soft_deleted_at TIMESTAMP;
              """);
      statement.execute(
          """
              CREATE INDEX IF NOT EXISTS idx_findings_soft_deleted_at
                  ON findings (finding_soft_deleted_at);
              """);
    }
  }
}
