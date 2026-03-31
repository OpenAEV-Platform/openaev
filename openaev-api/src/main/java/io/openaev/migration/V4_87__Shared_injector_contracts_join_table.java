package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_87__Shared_injector_contracts_join_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // 1. Create the join table for the ManyToMany relationship
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS injectors_injector_contracts (
            injector_id VARCHAR(255) NOT NULL
              REFERENCES injectors(injector_id) ON DELETE CASCADE,
            injector_contract_id VARCHAR(255) NOT NULL
              REFERENCES injectors_contracts(injector_contract_id) ON DELETE CASCADE,
            PRIMARY KEY (injector_id, injector_contract_id)
          );
          """);

      // 2. Populate the join table from the existing FK column
      stmt.execute(
          """
          INSERT INTO injectors_injector_contracts (injector_id, injector_contract_id)
          SELECT injector_id, injector_contract_id
          FROM injectors_contracts
          WHERE injector_id IS NOT NULL
          ON CONFLICT DO NOTHING;
          """);

      // 3. Drop the old composite unique index that references injector_id
      stmt.execute("DROP INDEX IF EXISTS injector_contract_payload_unique;");

      // 4. Drop the injector_id column from injectors_contracts
      //    (the join table is now the single source of truth)
      stmt.execute("ALTER TABLE injectors_contracts DROP COLUMN IF EXISTS injector_id;");

      // 5. Recreate payload unique index on injector_contract_payload alone
      //    (one contract per payload, regardless of injector)
      stmt.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS injector_contract_payload_unique
          ON injectors_contracts (injector_contract_payload)
          WHERE injector_contract_payload IS NOT NULL;
          """);
    }
  }
}
