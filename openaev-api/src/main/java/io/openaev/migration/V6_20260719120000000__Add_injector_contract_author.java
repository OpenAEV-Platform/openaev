package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Gives every injector contract a stored author so the Threat Arsenal can display and filter by
 * author even for payload-less contracts.
 *
 * <p>The author is polymorphic (user / team / organization), mirroring the payload author added in
 * {@code V6_20260719070000000}. It is stored directly on the contract; the entity getters still
 * fall back to the payload's author for payload-based contracts.
 *
 * <p>Backfill: built-in, payload-less contracts (Email, Manual, Media pressure, Challenges,
 * OpenCTI, OVH SMS...) are authored by Filigran, so we ensure a "Filigran" organization exists per
 * tenant and attribute those contracts to it. Idempotent and re-runnable.
 */
@Component
public class V6_20260719120000000__Add_injector_contract_author extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Polymorphic author for injector contracts: mutually exclusive nullable FKs. Nullable + ON
      // DELETE SET NULL so removing the actor never blocks or cascades onto the contract.
      statement.executeUpdate(
          """
          ALTER TABLE injectors_contracts
            ADD COLUMN IF NOT EXISTS injector_contract_author_user VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS injector_contract_author_team VARCHAR(255)
              REFERENCES teams(team_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS injector_contract_author_organization VARCHAR(255)
              REFERENCES organizations(organization_id) ON DELETE SET NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_injectors_contracts_author_user"
              + " ON injectors_contracts(injector_contract_author_user);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_injectors_contracts_author_team"
              + " ON injectors_contracts(injector_contract_author_team);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_injectors_contracts_author_organization"
              + " ON injectors_contracts(injector_contract_author_organization);");

      // Ensure a "Filigran" organization exists for every tenant (idempotent).
      statement.executeUpdate(
          "INSERT INTO organizations (organization_id, organization_name, tenant_id,"
              + " organization_created_at, organization_updated_at) "
              + "SELECT gen_random_uuid(), 'Filigran', t.tenant_id, now(), now() "
              + "FROM tenants t "
              + "WHERE NOT EXISTS ("
              + "  SELECT 1 FROM organizations o "
              + "  WHERE o.tenant_id = t.tenant_id AND o.organization_name = 'Filigran');");

      // Attribute built-in, payload-less contracts to their tenant's Filigran organization.
      // Custom contracts are authored by their creator (set at creation time) and payload-based
      // contracts resolve their author from the payload, so both are left untouched here.
      statement.executeUpdate(
          "UPDATE injectors_contracts ic "
              + "SET injector_contract_author_organization = o.organization_id "
              + "FROM organizations o "
              + "WHERE o.tenant_id = ic.tenant_id "
              + "  AND o.organization_name = 'Filigran' "
              + "  AND ic.injector_contract_author_user IS NULL "
              + "  AND ic.injector_contract_author_team IS NULL "
              + "  AND ic.injector_contract_author_organization IS NULL "
              + "  AND (ic.injector_contract_custom IS NOT TRUE) "
              + "  AND ic.injector_contract_payload IS NULL;");
    }
  }
}
