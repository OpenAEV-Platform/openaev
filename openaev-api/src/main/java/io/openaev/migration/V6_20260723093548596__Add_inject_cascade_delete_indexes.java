package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Adds missing FK-supporting indexes used when injects are deleted in large cascades. */
@Component
public class V6_20260723093548596__Add_inject_cascade_delete_indexes extends BaseJavaMigration {

  /** Concurrent index creation cannot execute inside a PostgreSQL transaction. */
  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_injects_depends_from_another_delete "
              + "ON injects (inject_depends_from_another) "
              + "WHERE inject_depends_from_another IS NOT NULL");
      statement.execute(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_asset_agent_jobs_inject "
              + "ON asset_agent_jobs (asset_agent_inject) "
              + "WHERE asset_agent_inject IS NOT NULL");
    }
  }
}
