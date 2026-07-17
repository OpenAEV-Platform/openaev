package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the queryable inject id to {@code attackpath_execution} (issue 5048, #203). The execution
 * row's primary key already embeds the inject id, but it is not queryable on its own (it also
 * embeds the target key), so #204's per-output update — which arrives with an inject id + an agent
 * id and must find all of that run's target rows — needs a plain column. Filled at creation by
 * #203; additive and nullable, so rows created before this migration (and the synthetic seed) carry
 * no inject id.
 */
@Component
public class V6_20260716140000000__Add_attackpath_execution_inject_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_execution "
              + "ADD COLUMN IF NOT EXISTS attackpath_execution_inject_id varchar(255);");
      // #204 looks the run's rows up by (inject id, agent id) to attach the terminal and status.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_ap_exec_inject_agent "
              + "ON attackpath_execution (attackpath_execution_inject_id, "
              + "attackpath_execution_agent_id);");
    }
  }
}
