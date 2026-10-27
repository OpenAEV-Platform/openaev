package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * One instance per installed catalogue scenario (ADR-011): the base content each update merges
 * against, a soft uninstall, and the revisions that keep past contents.
 */
@Component
public class V6_20261021030000000__Scenario_revisions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE scenarios
            ADD COLUMN IF NOT EXISTS scenario_catalog_repository VARCHAR(255),
            ADD COLUMN IF NOT EXISTS scenario_uninstalled BOOLEAN NOT NULL DEFAULT false,
            ADD COLUMN IF NOT EXISTS scenario_base_pending BOOLEAN NOT NULL DEFAULT false,
            ADD COLUMN IF NOT EXISTS scenario_base_content JSONB;
          """);
      // Branch databases installed versions side by side: they cannot become one instance.
      try (ResultSet duplicates =
          statement.executeQuery(
              """
              SELECT 1 FROM scenarios WHERE scenario_catalog_id IS NOT NULL
              GROUP BY scenario_catalog_id, tenant_id HAVING count(*) > 1 LIMIT 1
              """)) {
        if (duplicates.next()) {
          throw new IllegalStateException(
              "Several installs of the same catalogue scenario: database to reset");
        }
      }
      statement.execute(
          """
          UPDATE scenarios SET scenario_catalog_repository = 'filigran/openaev-scenarios'
          WHERE scenario_catalog_id IS NOT NULL AND scenario_catalog_repository IS NULL;
          CREATE UNIQUE INDEX IF NOT EXISTS scenarios_catalog_instance_unique
            ON scenarios (scenario_catalog_repository, scenario_catalog_id, tenant_id)
            WHERE scenario_catalog_id IS NOT NULL;

          ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_base_content JSONB;
          CREATE UNIQUE INDEX IF NOT EXISTS injects_scenario_catalog_unique
            ON injects (inject_scenario, inject_catalog_id)
            WHERE inject_catalog_id IS NOT NULL;

          ALTER TABLE steps
            ADD COLUMN IF NOT EXISTS step_catalog_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS step_base_content JSONB;
          CREATE UNIQUE INDEX IF NOT EXISTS steps_workflow_catalog_unique
            ON steps (step_workflow_id, step_catalog_id)
            WHERE step_catalog_id IS NOT NULL;

          CREATE TABLE IF NOT EXISTS scenario_revisions (
            scenario_revision_id VARCHAR(255) NOT NULL PRIMARY KEY,
            scenario_id VARCHAR(255) NOT NULL REFERENCES scenarios (scenario_id) ON DELETE CASCADE,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            scenario_revision_number INTEGER NOT NULL,
            scenario_revision_catalog_version VARCHAR(255),
            scenario_revision_reason VARCHAR(64) NOT NULL,
            scenario_revision_author VARCHAR(255) REFERENCES users (user_id) ON DELETE SET NULL,
            scenario_revision_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            scenario_revision_snapshot JSONB NOT NULL,
            CONSTRAINT scenario_revisions_number_unique
              UNIQUE (scenario_id, scenario_revision_number)
          );
          CREATE INDEX IF NOT EXISTS idx_scenario_revisions_tenant
            ON scenario_revisions (tenant_id);
          """);
    }
  }
}
