package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds missing FK-supporting indexes used when injects are deleted in large cascades.
 *
 * <p>Re-dated (2026-07-24, formerly 6.20260723093548596): the original timestamp sorted BEFORE
 * migrations already applied on deployed databases, so Flyway validation failed with "resolved
 * migration not applied" and out-of-order disabled. Idempotent (IF NOT EXISTS), so databases that
 * already ran the old version re-run this one as a no-op; their orphaned old history row is covered
 * by the default {@code ignore-migration-patterns=*:missing}.
 */
@Component
public class V6_20260724120000000__Add_inject_cascade_delete_indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_depends_from_another_delete "
              + "ON injects (inject_depends_from_another) "
              + "WHERE inject_depends_from_another IS NOT NULL");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_asset_agent_jobs_inject "
              + "ON asset_agent_jobs (asset_agent_inject) "
              + "WHERE asset_agent_inject IS NOT NULL");
    }
  }
}
