package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_16__Duplicate_tenant_id_for_assoc_injector_contract extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
            ALTER TABLE injectors_injector_contracts
            ADD COLUMN injector_tenant_id VARCHAR(255)
                REFERENCES tenants (tenant_id)
                ON DELETE CASCADE;
            """);
      stmt.execute(
          """
            UPDATE injectors_injector_contracts
            SET injector_tenant_id = tenant_id;
            """);
      stmt.execute(
          """
            ALTER TABLE injectors_injector_contracts
            ALTER COLUMN injector_tenant_id SET NOT NULL;
            """);

      stmt.execute(
          """
            ALTER TABLE injectors_injector_contracts
            RENAME tenant_id TO injector_contract_tenant_id;
            """);
      stmt.execute(
          """
            ALTER TABLE injectors_injector_contracts
            ALTER COLUMN injector_contract_tenant_id SET NOT NULL;
            """);

      stmt.execute(
          """
            ALTER TABLE injectors_injector_contracts
            ADD CONSTRAINT ck_tenants_ids_are_equal CHECK (injector_tenant_id = injector_contract_tenant_id);
            """);

      stmt.execute(
          """
            ALTER TABLE injectors_injector_contracts
            DROP CONSTRAINT injectors_injector_contracts_pkey;
            """);
      stmt.execute(
          """
            ALTER TABLE injectors_injector_contracts
            ADD PRIMARY KEY (injector_id, injector_contract_id, injector_tenant_id, injector_contract_tenant_id);
            """);
    }
  }
}
