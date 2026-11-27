package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Cascades a scenario delete to its reply-to addresses, so the tenant delete, which cascades to the
 * tenant's scenarios, removes them too. Same rule as {@code exercise_mails_reply_to} in V5_21, plus
 * the index a cascading foreign key needs.
 */
@Component
public class V6_20261027140000000__Cascade_scenario_reply_to_on_scenario_delete
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE scenario_mails_reply_to DROP CONSTRAINT IF EXISTS fk_scenario_id;
          ALTER TABLE scenario_mails_reply_to
            ADD CONSTRAINT fk_scenario_id
            FOREIGN KEY (scenario_id) REFERENCES scenarios(scenario_id) ON DELETE CASCADE;
          CREATE INDEX IF NOT EXISTS idx_scenario_mails_reply_to_scenario_id
            ON scenario_mails_reply_to(scenario_id);
          """);
    }
  }
}
