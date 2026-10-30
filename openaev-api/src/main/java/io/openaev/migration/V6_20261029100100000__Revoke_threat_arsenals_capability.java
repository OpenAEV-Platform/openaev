package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Revoking an action needed the delete right; roles that have it keep revoking. */
@Component
public class V6_20261029100100000__Revoke_threat_arsenals_capability extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT role_id, 'REVOKE_THREAT_ARSENALS' FROM roles_capabilities
          WHERE capability = 'DELETE_THREAT_ARSENALS'
          ON CONFLICT DO NOTHING;
          """);
    }
  }
}
