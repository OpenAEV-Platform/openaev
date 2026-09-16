package io.openaev.migration;

import io.openaev.database.model.Capability;
import java.sql.PreparedStatement;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Purges {@code roles_capabilities} rows a role must not hold: a tenant role (non-null {@code
 * tenant_id}) keeps only tenant-scoped capabilities, a platform role only platform-scoped ones
 */
@Component
public class V6_20260811100000000__Purge_out_of_scope_role_capabilities extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    // Tenant roles keep only tenant-scoped capabilities, platform roles only platform-scoped ones.
    deleteAllExcept(context, "IS NOT NULL", allowed(Capability.allTenantScoped()));
    deleteAllExcept(context, "IS NULL", allowed(Capability.allPlatformScoped()));
  }

  /** Both helpers exclude BYPASS, which is legitimate in either scope - add it back. */
  private static Set<Capability> allowed(Set<Capability> scoped) {
    Set<Capability> allowed = EnumSet.of(Capability.BYPASS);
    allowed.addAll(scoped);
    return allowed;
  }

  private void deleteAllExcept(Context context, String tenantIdCondition, Set<Capability> allowed)
      throws Exception {
    // Never empty (BYPASS is always in): an empty whitelist would wipe every row of the scope.
    String placeholders = allowed.stream().map(c -> "?").collect(Collectors.joining(","));
    String sql =
        "DELETE FROM roles_capabilities rc USING roles r"
            + " WHERE rc.role_id = r.role_id"
            + " AND r.tenant_id "
            + tenantIdCondition
            + " AND rc.capability NOT IN ("
            + placeholders
            + ");";
    try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
      int index = 1;
      for (Capability capability : allowed) {
        statement.setString(index++, capability.name());
      }
      statement.execute();
    }
  }
}
