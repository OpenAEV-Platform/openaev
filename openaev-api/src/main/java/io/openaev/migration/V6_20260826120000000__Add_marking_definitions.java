package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260826120000000__Add_marking_definitions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS marking_definitions (
              marking_definition_id VARCHAR(255) NOT NULL CONSTRAINT marking_definitions_pkey PRIMARY KEY,
              marking_definition_type VARCHAR(255) NOT NULL,
              marking_definition_definition VARCHAR(255) NOT NULL,
              marking_definition_color VARCHAR(255),
              marking_definition_order INTEGER NOT NULL DEFAULT 0,
              marking_definition_protected BOOLEAN NOT NULL DEFAULT false,
              marking_definition_created_at TIMESTAMP NOT NULL DEFAULT now(),
              marking_definition_updated_at TIMESTAMP NOT NULL DEFAULT now(),
              tenant_id VARCHAR(255) NOT NULL CONSTRAINT marking_definitions_tenant_fk REFERENCES tenants (tenant_id) ON DELETE CASCADE
          )
          """);

      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_marking_definitions_type_definition_tenant_uq ON marking_definitions (marking_definition_type, marking_definition_definition, tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_marking_definitions_type_order_tenant ON marking_definitions (marking_definition_type, marking_definition_order, tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_marking_definitions_tenant ON marking_definitions (tenant_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_marking_definitions_created_at ON marking_definitions (marking_definition_created_at)");

      // Observer gets access to marking definitions.
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT r.role_id, 'ACCESS_MARKING_DEFINITION'
          FROM roles r
          WHERE r.role_name = 'Observer'
            AND r.tenant_id IS NOT NULL
            AND NOT EXISTS (
              SELECT 1
              FROM roles_capabilities rc
              WHERE rc.role_id = r.role_id
                AND rc.capability = 'ACCESS_MARKING_DEFINITION'
            )
          """);

      // Manager gets full marking definition and assignment capabilities.
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT r.role_id, c.capability
          FROM roles r
          JOIN (
            VALUES
              ('ACCESS_MARKING_DEFINITION'),
              ('MANAGE_MARKING_DEFINITION'),
              ('DELETE_MARKING_DEFINITION'),
              ('ACCESS_MARKING_ASSIGNMENT'),
              ('ASSIGN_MARKING'),
              ('DELETE_MARKING_ASSIGNMENT')
          ) AS c(capability) ON true
          WHERE r.role_name = 'Manager'
            AND r.tenant_id IS NOT NULL
            AND NOT EXISTS (
              SELECT 1
              FROM roles_capabilities rc
              WHERE rc.role_id = r.role_id
                AND rc.capability = c.capability
            )
          """);
    }
  }
}
