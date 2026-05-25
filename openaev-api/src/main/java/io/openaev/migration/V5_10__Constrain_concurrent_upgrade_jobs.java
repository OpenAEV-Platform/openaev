package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_10__Constrain_concurrent_upgrade_jobs extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      // -- Prevent multiple concurrent upgrade jobs for same asset --
      statement.execute(
          """
              DELETE from asset_agent_jobs WHERE asset_agent_inject IS NULL;
              CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_upgrade_job_per_asset ON asset_agent_jobs (asset_agent_agent) WHERE (asset_agent_inject IS NULL);
              """);

      /* rollback
       * DROP INDEX IF EXISTS idx_unique_upgrade_job_per_asset;
       */
    }
  }
}
