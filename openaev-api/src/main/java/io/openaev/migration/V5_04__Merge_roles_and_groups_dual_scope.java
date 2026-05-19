package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_04__Merge_roles_and_groups_dual_scope extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement statement = context.getConnection().createStatement();

    // =====================================================================
    // ROLES: make dual-scope (nullable tenant_id)
    // =====================================================================
    statement.execute("ALTER TABLE roles ALTER COLUMN tenant_id DROP NOT NULL");

    // =====================================================================
    // GROUPS: make dual-scope (nullable tenant_id)
    // =====================================================================
    statement.execute("ALTER TABLE groups ALTER COLUMN tenant_id DROP NOT NULL");

    // =====================================================================
    // CLEANUP: drop old platform tables (no production data to migrate)
    // =====================================================================
    statement.execute("DROP TABLE IF EXISTS platform_groups_platform_roles");
    statement.execute("DROP TABLE IF EXISTS platform_groups_users");
    statement.execute("DROP TABLE IF EXISTS platform_roles_capabilities");
    statement.execute("DROP TABLE IF EXISTS platform_groups");
    statement.execute("DROP TABLE IF EXISTS platform_roles");
  }
}
