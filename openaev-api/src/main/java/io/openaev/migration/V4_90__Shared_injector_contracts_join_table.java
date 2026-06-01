package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_90__Shared_injector_contracts_join_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
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
    }
  }
}
