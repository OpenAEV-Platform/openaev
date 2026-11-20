package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261120170000000__Scenario_revisions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_catalog_id VARCHAR(255);");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS scenario_revisions (
            scenario_revision_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            scenario_id VARCHAR(255) NOT NULL
              REFERENCES scenarios (scenario_id) ON DELETE CASCADE,
            scenario_revision_reason VARCHAR(255) NOT NULL,
            scenario_revision_catalog_version VARCHAR(255) NOT NULL,
            scenario_revision_content JSONB NOT NULL,
            scenario_revision_user VARCHAR(255) REFERENCES users (user_id) ON DELETE SET NULL,
            scenario_revision_created_at TIMESTAMPTZ NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenario_revisions_tenant"
              + " ON scenario_revisions (tenant_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenario_revisions_scenario"
              + " ON scenario_revisions (scenario_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenario_revisions_user"
              + " ON scenario_revisions (scenario_revision_user);");
    }
  }
}
