package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_78__Add_inject_injector_column extends BaseJavaMigration {

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

      // 3. Add FK constraint (allows NULLs — injects without contract have no injector)
      stmt.execute(
          """
          ALTER TABLE injects
          ADD CONSTRAINT fk_inject_injector
          FOREIGN KEY (inject_injector) REFERENCES injectors(injector_id) ON DELETE CASCADE;
          """);
    }
  }
}
