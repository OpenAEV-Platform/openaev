package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Freezes the source endpoint's hostname/ip/platform on {@code attackpath_execution} (issue 5048,
 * #203). For an agent-based run the source is the agent's endpoint; the target's frozen info was
 * already captured, but the source endpoint's was not, so a source-only endpoint (an agent host
 * never itself a target) had no attributes to display. Captured at run time as a snapshot (it
 * cannot be recovered later). Additive and nullable: injector-sourced rows and the seed carry none.
 */
@Component
public class V6_20260716150000000__Add_attackpath_execution_source_endpoint
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_execution "
              + "ADD COLUMN IF NOT EXISTS attackpath_execution_source_hostname text, "
              + "ADD COLUMN IF NOT EXISTS attackpath_execution_source_ip text, "
              + "ADD COLUMN IF NOT EXISTS attackpath_execution_source_platform text;");
    }
  }
}
