package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public
class V6_20260817100000000__Add_tags_capabilities_to_existing_roles_with_tenant_settings_capability
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT DISTINCT rc.role_id, mapping.tags_capability
          FROM roles_capabilities rc
          JOIN roles r ON r.role_id = rc.role_id
          JOIN (
              VALUES
                ('ACCESS_TENANT_SETTINGS', 'ACCESS_TAGS'),
                ('MANAGE_TENANT_SETTINGS', 'ACCESS_TAGS'),
                ('MANAGE_TENANT_SETTINGS', 'MANAGE_TAGS'),
                ('DELETE_TENANT_SETTINGS', 'ACCESS_TAGS'),
                ('DELETE_TENANT_SETTINGS', 'MANAGE_TAGS'),
                ('DELETE_TENANT_SETTINGS', 'DELETE_TAGS')
          ) AS mapping(tenant_settings_capability, tags_capability)
            ON mapping.tenant_settings_capability = rc.capability
          WHERE rc.capability IN (
              'ACCESS_TENANT_SETTINGS',
              'MANAGE_TENANT_SETTINGS',
              'DELETE_TENANT_SETTINGS'
          )
            AND r.tenant_id IS NOT NULL
          ON CONFLICT (role_id, capability) DO NOTHING;
          """);
    }
  }
}
