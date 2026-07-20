package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Attributes authorless, custom, payload-less contracts that belong to an external injector to that
 * injector's publisher organization (named after the injector, e.g. "Nuclei").
 *
 * <p>Background: connectors such as Nuclei/nmap create one contract per CVE through the generic
 * {@code POST /injector_contracts} CRUD endpoint (marked {@code custom = true}), not through
 * injector registration. The earlier author backfill ({@code V6_20260719120000000}) deliberately
 * skipped custom contracts, so these read as "No author" in the Threat Arsenal even though they are
 * clearly published by the connector. This heals the existing rows in one pass; new/updated
 * contracts are handled in {@code InjectorContractService}.
 *
 * <p>Idempotent: organizations are created only when missing, and only authorless rows are updated
 * (once attributed, they no longer match).
 */
@Component
public class V6_20260720192000000__Backfill_external_injector_contract_author
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1. Ensure a publisher organization (named after the injector) exists per tenant, but only
      // for external injectors that actually own authorless custom payload-less contracts.
      statement.executeUpdate(
          "INSERT INTO organizations (organization_id, organization_name, tenant_id,"
              + " organization_created_at, organization_updated_at) "
              + "SELECT gen_random_uuid(), x.injector_name, x.tenant_id, now(), now() "
              + "FROM ("
              + "  SELECT DISTINCT i.injector_name, i.tenant_id "
              + "  FROM injectors i "
              + "  WHERE i.injector_external IS TRUE "
              + "    AND i.injector_name IS NOT NULL "
              + "    AND EXISTS ("
              + "      SELECT 1 FROM injectors_injector_contracts jic "
              + "      JOIN injectors_contracts ic"
              + "        ON ic.injector_contract_id = jic.injector_contract_id "
              + "      WHERE jic.injector_id = i.injector_id "
              + "        AND ic.injector_contract_author_user IS NULL "
              + "        AND ic.injector_contract_author_team IS NULL "
              + "        AND ic.injector_contract_author_organization IS NULL "
              + "        AND ic.injector_contract_custom IS TRUE "
              + "        AND ic.injector_contract_payload IS NULL)"
              + ") x "
              + "WHERE NOT EXISTS ("
              + "  SELECT 1 FROM organizations o "
              + "  WHERE o.tenant_id = x.tenant_id AND o.organization_name = x.injector_name);");

      // 2. Attribute the authorless custom payload-less contracts to their injector's organization.
      statement.executeUpdate(
          "UPDATE injectors_contracts ic "
              + "SET injector_contract_author_organization = o.organization_id "
              + "FROM injectors_injector_contracts jic "
              + "JOIN injectors i"
              + "  ON i.injector_id = jic.injector_id AND i.injector_external IS TRUE "
              + "JOIN organizations o"
              + "  ON o.tenant_id = i.tenant_id AND o.organization_name = i.injector_name "
              + "WHERE jic.injector_contract_id = ic.injector_contract_id "
              + "  AND ic.tenant_id = i.tenant_id "
              + "  AND ic.injector_contract_author_user IS NULL "
              + "  AND ic.injector_contract_author_team IS NULL "
              + "  AND ic.injector_contract_author_organization IS NULL "
              + "  AND ic.injector_contract_custom IS TRUE "
              + "  AND ic.injector_contract_payload IS NULL;");
    }
  }
}
