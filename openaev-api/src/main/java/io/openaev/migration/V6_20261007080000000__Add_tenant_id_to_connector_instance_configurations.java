package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Adds OPENAEV_TENANT_ID to connector instances deployed before it was set at creation. */
@Component
public class V6_20261007080000000__Add_tenant_id_to_connector_instance_configurations
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          "INSERT INTO connector_instance_configurations "
              + "(connector_instance_configuration_id, connector_instance_id, "
              + "connector_instance_configuration_key, connector_instance_configuration_value, "
              + "connector_instance_configuration_is_encrypted) "
              + "SELECT gen_random_uuid()::text, ci.connector_instance_id, 'OPENAEV_TENANT_ID', "
              + "to_jsonb(ci.tenant_id), false "
              + "FROM connector_instances ci "
              + "WHERE ci.tenant_id IS NOT NULL "
              + "AND NOT EXISTS ("
              + "  SELECT 1 FROM connector_instance_configurations c "
              + "  WHERE c.connector_instance_id = ci.connector_instance_id "
              + "  AND c.connector_instance_configuration_key = 'OPENAEV_TENANT_ID'"
              + ");");
    }
  }
}
