package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Combines two related multi-tenancy migrations:
 *
 * <p><b>Part A</b> — Shared injector-contracts join table: creates an explicit join table for the
 * ManyToMany between injectors and injectors_contracts (now tenant-scoped).
 *
 * <p><b>Part B</b> — Migrates FK references from {@code collector_type_name} to {@code
 * collector_type_id} (UUID PK) so that {@code collector_type_name} can be unique per tenant instead
 * of globally unique.
 *
 * <p>Tables affected (Part B):
 *
 * <ul>
 *   <li>{@code detection_remediations} — column {@code detection_remediation_collector_type}
 *   <li>{@code payloads} — column {@code payload_collector_type}
 *   <li>{@code collectors} — column {@code collector_type} (FK dropped, new {@code
 *       collector_type_id} column added)
 * </ul>
 */
@Component
public class V4_89__Shared_injector_contracts_join_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // =========================================================================
      // Part A — Shared injector-contracts join table
      // =========================================================================

      // 1. Create the join table for the ManyToMany relationship
      //    injectors_contracts now has a composite PK (injector_contract_id, tenant_id),
      //    so the join table must include tenant_id and use a composite FK.
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS injectors_injector_contracts (
            injector_id VARCHAR(255) NOT NULL
              REFERENCES injectors(injector_id) ON DELETE CASCADE,
            injector_contract_id VARCHAR(255) NOT NULL,
            tenant_id VARCHAR(255) NOT NULL,
            PRIMARY KEY (injector_id, injector_contract_id, tenant_id),
            FOREIGN KEY (injector_contract_id, tenant_id)
              REFERENCES injectors_contracts(injector_contract_id, tenant_id) ON DELETE CASCADE
          );
          """);

      // 2. Populate the join table from the existing FK column (include tenant_id)
      stmt.execute(
          """
          INSERT INTO injectors_injector_contracts (injector_id, injector_contract_id, tenant_id)
          SELECT injector_id, injector_contract_id, tenant_id
          FROM injectors_contracts
          WHERE injector_id IS NOT NULL
          ON CONFLICT DO NOTHING;
          """);

      // 3. Drop the old composite unique index that references injector_id
      stmt.execute("DROP INDEX IF EXISTS injector_contract_payload_unique;");

      // 4. Drop the injector_id column from injectors_contracts
      //    (the join table is now the single source of truth)
      stmt.execute("ALTER TABLE injectors_contracts DROP COLUMN IF EXISTS injector_id;");

      // 5. Recreate payload unique index on (injector_contract_payload, tenant_id)
      //    so each tenant can independently have a contract for a given payload
      stmt.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS injector_contract_payload_unique
          ON injectors_contracts (injector_contract_payload, tenant_id)
          WHERE injector_contract_payload IS NOT NULL;
          """);

      // =========================================================================
      // Part B — Migrate collector_type FK from name to UUID
      // =========================================================================

      // ---- detection_remediations: name → UUID ----

      // B1a. Drop old FK
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              DROP CONSTRAINT IF EXISTS fk_remediation_collector_type;
          """);

      // B1b. Add temporary UUID column
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ADD COLUMN detection_remediation_collector_type_id VARCHAR(255);
          """);

      // B1c. Backfill UUID from collector_types by matching name
      stmt.execute(
          """
          UPDATE detection_remediations dr
          SET detection_remediation_collector_type_id = ct.collector_type_id
          FROM collector_types ct
          WHERE dr.detection_remediation_collector_type = ct.collector_type_name;
          """);

      // B1d. Drop old name column
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              DROP COLUMN detection_remediation_collector_type;
          """);

      // B1e. Rename new column to original name
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              RENAME COLUMN detection_remediation_collector_type_id
              TO detection_remediation_collector_type;
          """);

      // B1f. Add FK to collector_types PK
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ADD CONSTRAINT fk_remediation_collector_type
                  FOREIGN KEY (detection_remediation_collector_type)
                  REFERENCES collector_types(collector_type_id);
          """);

      // B1g. Recreate index
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_detection_remediation_collector_type
              ON detection_remediations(detection_remediation_collector_type);
          """);

      // ---- payloads: name → UUID ----

      // B2a. Drop old FK
      stmt.execute(
          """
          ALTER TABLE payloads
              DROP CONSTRAINT IF EXISTS fk_payload_collector_type;
          """);

      // B2b. Add temporary UUID column
      stmt.execute(
          """
          ALTER TABLE payloads
              ADD COLUMN payload_collector_type_id VARCHAR(255);
          """);

      // B2c. Backfill UUID from collector_types by matching name
      stmt.execute(
          """
          UPDATE payloads p
          SET payload_collector_type_id = ct.collector_type_id
          FROM collector_types ct
          WHERE p.payload_collector_type = ct.collector_type_name;
          """);

      // B2d. Drop old name column
      stmt.execute(
          """
          ALTER TABLE payloads
              DROP COLUMN payload_collector_type;
          """);

      // B2e. Rename new column to original name
      stmt.execute(
          """
          ALTER TABLE payloads
              RENAME COLUMN payload_collector_type_id
              TO payload_collector_type;
          """);

      // B2f. Add FK to collector_types PK
      stmt.execute(
          """
          ALTER TABLE payloads
              ADD CONSTRAINT fk_payload_collector_type
                  FOREIGN KEY (payload_collector_type)
                  REFERENCES collector_types(collector_type_id);
          """);

      // ---- collectors: drop FK on name, add collector_type_id column with FK ----

      // B3a. Drop the FK from collectors.collector_type → collector_types.collector_type_name
      stmt.execute(
          """
          ALTER TABLE collectors
              DROP CONSTRAINT IF EXISTS fk_collector_type_ref;
          """);

      // B3b. Add new column for the UUID reference
      stmt.execute(
          """
          ALTER TABLE collectors
              ADD COLUMN collector_type_id VARCHAR(255);
          """);

      // B3c. Backfill UUID from collector_types by matching name
      stmt.execute(
          """
          UPDATE collectors c
          SET collector_type_id = ct.collector_type_id
          FROM collector_types ct
          WHERE c.collector_type = ct.collector_type_name
              AND c.tenant_id = ct.tenant_id;
          """);

      // B3d. Add FK to collector_types PK
      stmt.execute(
          """
          ALTER TABLE collectors
              ADD CONSTRAINT fk_collector_type_ref
                  FOREIGN KEY (collector_type_id)
                  REFERENCES collector_types(collector_type_id);
          """);

      // ---- Change collector_type_name uniqueness: global → per-tenant ----

      // B4a. Drop the global unique constraint
      stmt.execute(
          """
          ALTER TABLE collector_types
              DROP CONSTRAINT IF EXISTS collector_types_name_unique;
          """);

      // B4b. Add per-tenant unique constraint
      stmt.execute(
          """
          ALTER TABLE collector_types
              ADD CONSTRAINT collector_types_name_tenant_unique
                  UNIQUE (collector_type_name, tenant_id);
          """);
    }
  }
}
