package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Single migration that decouples detection_remediations and payloads from collector instance
 * identity, and drops the uniqueness constraints on collector/injector/executor types.
 *
 * <p>Four sequential actions:
 *
 * <ol>
 *   <li>Create {@code collector_types} reference table with a technical UUID primary key ({@code
 *       collector_type_id}) and a UNIQUE natural key ({@code collector_type_name}). Populate from
 *       existing collectors, re-target the {@code detection_remediations} FK and add a FK on {@code
 *       collectors.collector_type}.
 *   <li>Replace {@code payload_collector} (FK → {@code collectors.collector_id}, ON DELETE CASCADE)
 *       with {@code payload_collector_type} (FK → {@code collector_types.collector_type_name}).
 *   <li>Drop the UNIQUE indexes on {@code collectors}, {@code injectors}, and {@code executors}
 *       type columns (now safe because no FK depends on them).
 * </ol>
 */
@Component
public class V4_77__Decouple_collector_type_uniqueness extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // =========================================================================
      // ACTION 1 — Create collector_types + re-target detection_remediations FK
      // =========================================================================

      // 1a. Create the reference table with UUID PK and UNIQUE natural key
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS collector_types (
              collector_type_id VARCHAR(255) NOT NULL,
              collector_type_name VARCHAR(255) NOT NULL,
              tenant_id VARCHAR(255) NOT NULL,
              CONSTRAINT collector_types_pkey PRIMARY KEY (collector_type_id),
              CONSTRAINT collector_types_name_unique UNIQUE (collector_type_name),
              CONSTRAINT fk_collector_type_tenant
                  FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
          );
          """);

      // 1b. Populate from existing collector types (one row per distinct type, tenant from
      // collector)
      stmt.execute(
          """
          INSERT INTO collector_types (collector_type_id, collector_type_name, tenant_id)
          SELECT DISTINCT gen_random_uuid(), collector_type, tenant_id FROM collectors
          ON CONFLICT DO NOTHING;
          """);

      // 1c. Drop old FK (detection_remediations → collectors.collector_type)
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              DROP CONSTRAINT IF EXISTS fk_remediation_collector_type;
          """);

      // 1d. Add new FK (detection_remediations → collector_types.collector_type_name)
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ADD CONSTRAINT fk_remediation_collector_type
                  FOREIGN KEY (detection_remediation_collector_type)
                  REFERENCES collector_types(collector_type_name);
          """);

      // 1e. Add FK on collectors.collector_type → collector_types.collector_type_name
      stmt.execute(
          """
          ALTER TABLE collectors
              ADD CONSTRAINT fk_collector_type_ref
                  FOREIGN KEY (collector_type)
                  REFERENCES collector_types(collector_type_name);
          """);

      // =========================================================================
      // ACTION 2 — Replace payload_collector with payload_collector_type
      // =========================================================================

      // 2a. Add the new column
      stmt.execute(
          """
          ALTER TABLE payloads
              ADD COLUMN IF NOT EXISTS payload_collector_type VARCHAR(255);
          """);

      // 2b. Backfill from existing collector → collector_type
      stmt.execute(
          """
          UPDATE payloads p
          SET payload_collector_type = c.collector_type
          FROM collectors c
          WHERE p.payload_collector = c.collector_id;
          """);

      // 2c. Add FK to collector_types (no cascade — types are never deleted)
      stmt.execute(
          """
          ALTER TABLE payloads
              ADD CONSTRAINT fk_payload_collector_type
                  FOREIGN KEY (payload_collector_type)
                  REFERENCES collector_types(collector_type_name);
          """);

      // 2d. Drop the old column and its FK
      stmt.execute(
          """
          ALTER TABLE payloads
              DROP CONSTRAINT IF EXISTS collector_fk;
          """);

      stmt.execute(
          """
          ALTER TABLE payloads
              DROP COLUMN IF EXISTS payload_collector;
          """);

      // =========================================================================
      // ACTION 3 — Drop UNIQUE indexes on type columns
      // =========================================================================

      stmt.execute("DROP INDEX IF EXISTS collectors_unique;");
      stmt.execute("DROP INDEX IF EXISTS injectors_unique;");
      stmt.execute("DROP INDEX IF EXISTS executors_unique;");

      // Create non-unique indexes for query performance
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_collectors_type
              ON collectors (collector_type);
          """);

      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_injectors_type
              ON injectors (injector_type);
          """);

      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_executors_type
              ON executors (executor_type);
          """);
    }
  }
}
