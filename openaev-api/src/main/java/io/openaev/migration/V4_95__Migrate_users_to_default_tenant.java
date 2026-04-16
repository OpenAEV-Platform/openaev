package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_95__Migrate_users_to_default_tenant extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          String.format(
              """
                INSERT INTO users_tenants
                SELECT u.user_id, '%s' FROM users u
        """,
              DEFAULT_TENANT_UUID));
    }
  }
}
