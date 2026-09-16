package io.openaev.migration;

import io.openaev.database.model.Capability;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Grants the tenant users/groups/roles triad to every tenant role that reached user, group and role
 * management through the TENANT_SETTINGS triad, which no longer carries those resources. Without
 * this, splitting the capabilities silently revokes that access from existing roles.
 *
 * <p>Each tier is granted from its own tier and the ones above it, so a role holding only MANAGE
 * ends up with ACCESS+MANAGE even if its parent grants were never materialized.
 */
@Component
public class V6_20260818100000000__Grant_tenant_users_groups_and_roles_capabilities
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    grant(
        context,
        Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
        List.of(
            Capability.ACCESS_TENANT_SETTINGS,
            Capability.MANAGE_TENANT_SETTINGS,
            Capability.DELETE_TENANT_SETTINGS));
    grant(
        context,
        Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
        List.of(Capability.MANAGE_TENANT_SETTINGS, Capability.DELETE_TENANT_SETTINGS));
    grant(
        context,
        Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES,
        List.of(Capability.DELETE_TENANT_SETTINGS));
  }

  /**
   * {@code tenant_id IS NOT NULL} keeps platform roles out: the new triad is tenant-scoped, and
   * granting it to a platform role would be purged by the scope guard anyway.
   */
  private void grant(Context context, Capability granted, List<Capability> heldByRole)
      throws Exception {
    String placeholders = String.join(",", heldByRole.stream().map(c -> "?").toList());
    String sql =
        "INSERT INTO roles_capabilities (role_id, capability)"
            + " SELECT DISTINCT rc.role_id, ?"
            + " FROM roles_capabilities rc"
            + " JOIN roles r ON r.role_id = rc.role_id"
            + " WHERE r.tenant_id IS NOT NULL"
            + " AND rc.capability IN ("
            + placeholders
            + ")"
            + " ON CONFLICT DO NOTHING";
    try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
      int index = 1;
      statement.setString(index++, granted.name());
      for (Capability capability : heldByRole) {
        statement.setString(index++, capability.name());
      }
      statement.execute();
    }
  }
}
