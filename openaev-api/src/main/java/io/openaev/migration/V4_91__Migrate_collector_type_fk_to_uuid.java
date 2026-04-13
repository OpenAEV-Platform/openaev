package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Migrates FK references from {@code collector_type_name} to {@code collector_type_id} (UUID PK) so
 * that {@code collector_type_name} can be unique per tenant instead of globally unique.
 *
 * <p>Tables affected:
 *
 * <ul>
 *   <li>{@code detection_remediations} — column {@code detection_remediation_collector_type}
 *   <li>{@code payloads} — column {@code payload_collector_type}
 *   <li>{@code collectors} — new {@code collector_type_id} column
 *   <li>{@code collector_types} — uniqueness changed from global to per-tenant
 * </ul>
 */
@Component
public class V4_91__Migrate_collector_type_fk_to_uuid extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // ---- detection_remediations: name → UUID ----

      // 1a. Drop old FK
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              DROP CONSTRAINT IF EXISTS fk_remediation_collector_type;
          """);

      // 1b. Add temporary UUID column
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ADD COLUMN IF NOT EXISTS detection_remediation_collector_type_id VARCHAR(255);
          """);

      // 1c. Backfill UUID from collector_types by matching name
      stmt.execute(
          """
          UPDATE detection_remediations dr
          SET detection_remediation_collector_type_id = ct.collector_type_id
          FROM collector_types ct
          WHERE dr.detection_remediation_collector_type = ct.collector_type_name;
          """);

      // 1d. Drop old name column
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              DROP COLUMN IF EXISTS detection_remediation_collector_type;
          """);

      // 1e. Rename new column to original name
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              RENAME COLUMN detection_remediation_collector_type_id
              TO detection_remediation_collector_type;
          """);

      // 1f. Add FK to collector_types PK
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ADD CONSTRAINT fk_remediation_collector_type
                  FOREIGN KEY (detection_remediation_collector_type)
                  REFERENCES collector_types(collector_type_id);
          """);

      // 1g. Recreate index
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_detection_remediation_collector_type
              ON detection_remediations(detection_remediation_collector_type);
          """);

      // ---- payloads: name → UUID ----

      // 2a. Drop old FK
      stmt.execute(
          """
          ALTER TABLE payloads
              DROP CONSTRAINT IF EXISTS fk_payload_collector_type;
          """);

      // 2b. Add temporary UUID column
      stmt.execute(
          """
          ALTER TABLE payloads
              ADD COLUMN IF NOT EXISTS payload_collector_type_id VARCHAR(255);
          """);

      // 2c. Backfill UUID from collector_types by matching name
      stmt.execute(
          """
          UPDATE payloads p
          SET payload_collector_type_id = ct.collector_type_id
          FROM collector_types ct
          WHERE p.payload_collector_type = ct.collector_type_name;
          """);

      // 2d. Drop old name column
      stmt.execute(
          """
          ALTER TABLE payloads
              DROP COLUMN IF EXISTS payload_collector_type;
          """);

      // 2e. Rename new column to original name
      stmt.execute(
          """
          ALTER TABLE payloads
              RENAME COLUMN payload_collector_type_id
              TO payload_collector_type;
          """);

      // 2f. Add FK to collector_types PK
      stmt.execute(
          """
          ALTER TABLE payloads
              ADD CONSTRAINT fk_payload_collector_type
                  FOREIGN KEY (payload_collector_type)
                  REFERENCES collector_types(collector_type_id);
          """);

      // ---- collectors: drop FK on name, add collector_type_id column with FK ----

      // 3a. Drop the FK from collectors.collector_type → collector_types.collector_type_name
      stmt.execute(
          """
          ALTER TABLE collectors
              DROP CONSTRAINT IF EXISTS fk_collector_type_ref;
          """);

      // 3b. Add new column for the UUID reference
      stmt.execute(
          """
          ALTER TABLE collectors
              ADD COLUMN IF NOT EXISTS collector_type_id VARCHAR(255);
          """);

      // 3c. Backfill UUID from collector_types by matching name
      stmt.execute(
          """
          UPDATE collectors c
          SET collector_type_id = ct.collector_type_id
          FROM collector_types ct
          WHERE c.collector_type = ct.collector_type_name
              AND c.tenant_id = ct.tenant_id;
          """);

      // 3d. Add FK to collector_types PK
      stmt.execute(
          """
          ALTER TABLE collectors
              ADD CONSTRAINT fk_collector_type_ref
                  FOREIGN KEY (collector_type_id)
                  REFERENCES collector_types(collector_type_id);
          """);

      // ---- Change collector_type_name uniqueness: global → per-tenant ----

      // 4a. Drop the global unique constraint
      stmt.execute(
          """
          ALTER TABLE collector_types
              DROP CONSTRAINT IF EXISTS collector_types_name_unique;
          """);

      // 4b. Add per-tenant unique constraint
      stmt.execute(
          """
          ALTER TABLE collector_types
              ADD CONSTRAINT collector_types_name_tenant_unique
                  UNIQUE (collector_type_name, tenant_id);
          """);
    }
  }
}
