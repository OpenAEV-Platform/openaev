package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Makes {@code update_injector_contract_updated_at()} bump only the copy of the contract that
 * belongs to the tenant of the removed link row.
 *
 * <p>A contract id is shared by every tenant ({@code injectors_contracts} is keyed on {@code
 * (injector_contract_id, tenant_id)}), and the trigger of the attack pattern and vulnerability link
 * tables matched the contract on its id alone, so removing a link row in one tenant also changed
 * {@code injector_contract_updated_at} of the copies held by every other tenant.
 *
 * <p>The triggers are unchanged; only the function body is replaced, which is idempotent.
 */
@Component
public class V6_20261112140000002__Scope_injector_contract_updated_at_trigger_to_tenant
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION update_injector_contract_updated_at()
              RETURNS TRIGGER AS $$
          BEGIN
              UPDATE injectors_contracts
              SET injector_contract_updated_at = now()
              WHERE injector_contract_id = OLD.injector_contract_id
                AND tenant_id = OLD.tenant_id;

              RETURN OLD;
          END;
          $$ LANGUAGE plpgsql;
          """);
    }
  }
}
