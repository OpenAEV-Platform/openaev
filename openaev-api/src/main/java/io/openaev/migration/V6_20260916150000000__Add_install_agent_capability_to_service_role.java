package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Backfills the {@code INSTALL_AGENT} capability on the per-tenant "Service integration" role
 * created by {@code ServiceAccountPrivilegeService}, so that the service-account token it already
 * holds can also call the (now capability-gated) installer command/token/download endpoints.
 *
 * <p>Idempotent by construction: the insert is guarded with a NOT EXISTS check on {@code
 * roles_capabilities(role_id, capability)}.
 */
@Component
public class V6_20260916150000000__Add_install_agent_capability_to_service_role
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT r.role_id, 'INSTALL_AGENT'
          FROM roles r
          WHERE r.role_name = 'Service integration'
            AND NOT EXISTS (
              SELECT 1
              FROM roles_capabilities rc
              WHERE rc.role_id = r.role_id
                AND rc.capability = 'INSTALL_AGENT'
            )
          """);
    }
  }
}
