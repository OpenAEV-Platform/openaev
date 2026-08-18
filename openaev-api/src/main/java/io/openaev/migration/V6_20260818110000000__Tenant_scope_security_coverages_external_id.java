package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260818110000000__Tenant_scope_security_coverages_external_id
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE security_coverages ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);");

      // Prefer the scenario tenant when available.
      statement.execute(
          """
          UPDATE security_coverages sc
          SET tenant_id = s.tenant_id
          FROM scenarios s
          WHERE sc.security_coverage_scenario = s.scenario_id
            AND sc.tenant_id IS NULL;
          """);

      // Fallback: infer tenant from linked exercises for legacy rows without a scenario.
      statement.execute(
          """
          UPDATE security_coverages sc
          SET tenant_id = ex.tenant_id
          FROM (
            SELECT exercise_security_coverage AS security_coverage_id,
                   MIN(tenant_id) AS tenant_id
            FROM exercises
            WHERE exercise_security_coverage IS NOT NULL
            GROUP BY exercise_security_coverage
          ) ex
          WHERE sc.security_coverage_id = ex.security_coverage_id
            AND sc.tenant_id IS NULL;
          """);

      // Last-resort fallback for orphan legacy rows.
      statement.execute(
          """
          UPDATE security_coverages
          SET tenant_id = '%s'
          WHERE tenant_id IS NULL;
          """
              .formatted(DEFAULT_TENANT_UUID));

      statement.execute(
          """
          DELETE FROM security_coverages sc
          USING (
            SELECT security_coverage_id
            FROM (
              SELECT security_coverage_id,
                     ROW_NUMBER() OVER (
                       PARTITION BY security_coverage_external_id, tenant_id
                       ORDER BY (security_coverage_scenario IS NOT NULL) DESC,
                                security_coverage_updated_at DESC NULLS LAST,
                                security_coverage_id ASC
                     ) AS rn
              FROM security_coverages
            ) ranked
            WHERE ranked.rn > 1
          ) dup
          WHERE sc.security_coverage_id = dup.security_coverage_id;
          """);

      statement.execute("ALTER TABLE security_coverages ALTER COLUMN tenant_id SET NOT NULL;");

      statement.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1
              FROM pg_constraint
              WHERE conname = 'fk_security_coverages_tenant_id'
                AND conrelid = 'security_coverages'::regclass
            ) THEN
              ALTER TABLE security_coverages
                ADD CONSTRAINT fk_security_coverages_tenant_id
                FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE;
            END IF;
          END $$;
          """);

      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_security_coverages_tenant_id ON security_coverages(tenant_id);");

      statement.execute(
          """
          DO $$
          BEGIN
            IF EXISTS (
              SELECT 1
              FROM pg_constraint
              WHERE conname = 'security_coverages_external_id_unique'
                AND conrelid = 'security_coverages'::regclass
            ) THEN
              ALTER TABLE security_coverages
                DROP CONSTRAINT security_coverages_external_id_unique;
            END IF;

            IF NOT EXISTS (
              SELECT 1
              FROM pg_constraint
              WHERE conname = 'security_coverages_external_id_tenant_id_unique'
                AND conrelid = 'security_coverages'::regclass
            ) THEN
              ALTER TABLE security_coverages
                ADD CONSTRAINT security_coverages_external_id_tenant_id_unique
                UNIQUE (security_coverage_external_id, tenant_id);
            END IF;
          END $$;
          """);
    }
  }
}
