package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Drops the redundant single-column index {@code idx_ap_exec_sim} on {@code
 * attackpath_execution(simulation_id)} (issue 6647). The composite {@code
 * idx_ap_exec_sim_targetkey} also leads with {@code simulation_id}, so it already serves the
 * simulation-only graph read as a leftmost-prefix scan; the benchmark measured the same read time
 * with or without the single-column index. Keeping it only cost an extra index to maintain on every
 * insert. Additive and idempotent ({@code IF EXISTS}), so it applies cleanly on top of the original
 * migration without rewriting it.
 */
@Component
public class V6_20260711100000000__Drop_redundant_attackpath_exec_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DROP INDEX IF EXISTS idx_ap_exec_sim;");
    }
  }
}
