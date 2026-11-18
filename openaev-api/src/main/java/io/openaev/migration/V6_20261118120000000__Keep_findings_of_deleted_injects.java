package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * A finding describes a real asset or user: it outlives the inject, simulation or scenario that
 * produced it. The legacy {@code finding_inject_id} keeps pointing at the creating inject until
 * every source writes occurrences, but its foreign key no longer deletes the finding: it is set to
 * NULL, like {@code finding_occurrences.finding_occurrence_inject_id}. A finding is deleted only
 * with its location (asset or user) or explicitly.
 */
@Component
public class V6_20261118120000000__Keep_findings_of_deleted_injects extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE findings DROP CONSTRAINT IF EXISTS finding_inject_id_fk");
      statement.execute(
          "ALTER TABLE findings ADD CONSTRAINT finding_inject_id_fk"
              + " FOREIGN KEY (finding_inject_id) REFERENCES injects (inject_id)"
              + " ON DELETE SET NULL");
    }
  }
}
