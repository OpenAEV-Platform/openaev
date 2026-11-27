package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Finding lifecycle: a status (every existing finding stays OPEN), the date of the last re-test,
 * the inject whose run set the status, and the re-test inject still pending. The inject columns
 * carry no foreign key: a relaunched inject is deleted, the status keeps the id it was set by.
 */
@Component
public class V6_20261121140000000__Add_finding_lifecycle extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_status VARCHAR(32)"
              + " NOT NULL DEFAULT 'OPEN'");
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_last_tested TIMESTAMPTZ");
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_status_inject_id VARCHAR(255)");
      statement.execute(
          "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_retest_inject_id VARCHAR(255)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_retest_inject"
              + " ON findings (finding_retest_inject_id)"
              + " WHERE finding_retest_inject_id IS NOT NULL");
    }
  }
}
