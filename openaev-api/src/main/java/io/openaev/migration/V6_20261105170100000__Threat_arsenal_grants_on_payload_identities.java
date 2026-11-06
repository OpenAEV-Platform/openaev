package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261105170100000__Threat_arsenal_grants_on_payload_identities
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // A contract id repeats across tenants: a tenant group's grant follows its own tenant's.
      statement.execute(
          """
          UPDATE grants g SET grant_resource = p.payload_identity
          FROM groups gr, injectors_contracts ic, payloads p
          WHERE g.grant_resource_type = 'THREAT_ARSENAL'
            AND gr.group_id = g.grant_group AND gr.tenant_id IS NOT NULL
            AND ic.injector_contract_id = g.grant_resource AND ic.tenant_id = gr.tenant_id
            AND p.payload_id = ic.injector_contract_payload AND p.tenant_id = ic.tenant_id
            AND NOT EXISTS (
              SELECT 1 FROM grants same WHERE same.grant_group = g.grant_group
                AND same.grant_name = g.grant_name AND same.grant_resource = p.payload_identity);
          """);
      // A platform group's grant applies in every tenant: one grant per tenant's payload.
      statement.execute(
          """
          INSERT INTO grants (grant_id, grant_group, grant_name, grant_resource, grant_resource_type)
          SELECT gen_random_uuid()::text, g.grant_group, g.grant_name, p.payload_identity,
            'THREAT_ARSENAL'
          FROM grants g
          JOIN groups gr ON gr.group_id = g.grant_group AND gr.tenant_id IS NULL
          JOIN injectors_contracts ic ON ic.injector_contract_id = g.grant_resource
          JOIN payloads p ON p.payload_id = ic.injector_contract_payload
            AND p.tenant_id = ic.tenant_id
          WHERE g.grant_resource_type = 'THREAT_ARSENAL'
          ON CONFLICT (grant_group, grant_resource, grant_name) DO NOTHING;
          """);
      statement.execute(
          """
          DELETE FROM grants g USING groups gr
          WHERE gr.group_id = g.grant_group AND gr.tenant_id IS NULL
            AND g.grant_resource_type = 'THREAT_ARSENAL'
            AND EXISTS (SELECT 1 FROM injectors_contracts ic
              WHERE ic.injector_contract_id = g.grant_resource
                AND ic.injector_contract_payload IS NOT NULL)
            AND NOT EXISTS (SELECT 1 FROM injectors_contracts ic
              WHERE ic.injector_contract_id = g.grant_resource
                AND ic.injector_contract_payload IS NULL);
          """);
    }
  }
}
