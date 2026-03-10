package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_77__Add_inject_injector_column extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // 1. Add the new column (nullable initially for backfill)
      stmt.execute("ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_injector VARCHAR(255);");

      // 2. Backfill from the existing contract→injector FK
      //    (injectors_contracts.injector_id still exists at this stage)
      stmt.execute(
          """
          UPDATE injects i
          SET inject_injector = ic.injector_id
          FROM injectors_contracts ic
          WHERE i.inject_injector_contract = ic.injector_contract_id
            AND ic.injector_id IS NOT NULL
            AND i.inject_injector IS NULL;
          """);

      // 3. Remove orphan injects that could not be backfilled
      //    (contract is set but the contract has no injector — should not happen in practice)
      stmt.execute(
          """
          DELETE FROM injects
          WHERE inject_injector IS NULL
            AND inject_injector_contract IS NOT NULL;
          """);

      // 4. For injects without a contract (manual injects), inject_injector stays NULL.
      //    We cannot make the column NOT NULL if such rows exist.
      //    So we only set NOT NULL if there are no NULLs remaining.
      //    In practice, all injects with a contract now have an injector.
      //    Injects without a contract are "orphan" channel/manual injects — leave them.

      // 5. Add FK constraint (allows NULLs — injects without contract have no injector)
      stmt.execute(
          """
          ALTER TABLE injects
          ADD CONSTRAINT fk_inject_injector
          FOREIGN KEY (inject_injector) REFERENCES injectors(injector_id) ON DELETE CASCADE;
          """);
    }
  }
}
