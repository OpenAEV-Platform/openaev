package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Backfills credentials capabilities on predefined tenant roles created by legacy migrations.
 *
 * <p>Idempotent by construction: each insert is guarded with a NOT EXISTS check on {@code
 * roles_capabilities(role_id, capability)}.
 */
@Component
public class V6_20260811100000000__Add_credentials_capabilities_to_default_roles
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Observer gets read/search access for credentials.
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT r.role_id, 'ACCESS_CREDENTIALS'
          FROM roles r
          WHERE r.role_name = 'Observer'
            AND NOT EXISTS (
              SELECT 1
              FROM roles_capabilities rc
              WHERE rc.role_id = r.role_id
                AND rc.capability = 'ACCESS_CREDENTIALS'
            )
          """);

      // Manager gets full credential management access.
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT r.role_id, c.capability
          FROM roles r
          JOIN (
            VALUES
              ('ACCESS_CREDENTIALS'),
              ('MANAGE_CREDENTIALS'),
              ('DELETE_CREDENTIALS')
          ) AS c(capability) ON true
          WHERE r.role_name = 'Manager'
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
