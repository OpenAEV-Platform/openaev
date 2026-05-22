package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_09__Migrate_Payload_To_Threat_Arsenal extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      // -- Migration 1: Replace PAYLOAD capabilities by THREAT_ARSENAL in roles --
      statement.execute(
          """
              UPDATE roles_capabilities
              SET capability = 'ACCESS_THREAT_ARSENALS'
              WHERE capability = 'ACCESS_PAYLOADS';
              """);
      statement.execute(
          """
              UPDATE roles_capabilities
              SET capability = 'MANAGE_THREAT_ARSENALS'
              WHERE capability = 'MANAGE_PAYLOADS';
              """);
      statement.execute(
          """
              UPDATE roles_capabilities
              SET capability = 'DELETE_THREAT_ARSENALS'
              WHERE capability = 'DELETE_PAYLOADS';
              """);

      // -- Migration 2: Migrate grants from PAYLOAD to THREAT_ARSENAL --
      // Update grant_resource from payload ID to injector contract ID,
      // and change grant_resource_type from PAYLOAD to THREAT_ARSENAL.
      // The injector contract ID is found via the injectors_contracts table
      // where injector_contract_payload = grant_resource (the payload ID).
      statement.execute(
          """
              UPDATE grants g
              SET grant_resource = ic.injector_contract_id,
                  grant_resource_type = 'THREAT_ARSENAL'
              FROM injectors_contracts ic
              WHERE g.grant_resource_type = 'PAYLOAD'
                AND ic.injector_contract_payload = g.grant_resource;
              """);

      // Delete any remaining PAYLOAD grants that could not be mapped
      // (orphan grants pointing to payloads without an injector contract)
      statement.execute(
          """
              DELETE FROM grants
              WHERE grant_resource_type = 'PAYLOAD';
              """);
    }
  }
}
