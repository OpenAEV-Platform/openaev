package io.openaev.migration;

import static io.openaev.database.model.User.ADMIN_UUID;
import static io.openaev.service.account.AbstractPrivilegeService.getUUIDFromName;
import static io.openaev.service.account.AdminPrivilegeService.ADMIN_ACCESS;
import static io.openaev.service.account.AdminPrivilegeService.PLATFORM_ADMIN_ACCESS;

import io.openaev.database.model.Capability;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Moves the users other than the root user who hold the admin flag, a hidden RBAC bypass, to the
 * platform Administrators group and to the Admin group of each of their tenants, then clears their
 * flag. A tenant whose Admin group is missing or does not grant {@code BYPASS} is skipped and
 * logged.
 *
 * <p>The platform group is created when missing: the bootstrap creates it after Flyway, so an
 * instance upgraded from a version older than 2.260623.0 has never had it.
 */
@Slf4j
@Component
public class V6_20261126110000000__Move_legacy_admins_to_admin_groups extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    List<String> legacyAdmins =
        queryIds(
            connection,
            "SELECT user_id FROM users WHERE user_admin AND user_id <> ?",
            ADMIN_UUID);
    if (legacyAdmins.isEmpty()) {
      return;
    }
    ensurePlatformAdminGroup(connection);
    for (String userId : legacyAdmins) {
      addMembership(connection, userId, PLATFORM_ADMIN_ACCESS.groupId());
      for (String tenantId :
          queryIds(connection, "SELECT tenant_id FROM users_tenants WHERE user_id = ?", userId)) {
        Optional<String> adminGroupId = tenantAdminGroupId(connection, tenantId);
        if (adminGroupId.isPresent()) {
          addMembership(connection, userId, adminGroupId.get());
        } else {
          log.warn(
              "No Admin group granting BYPASS in tenant {}: user {} is not made an admin of it",
              tenantId,
              userId);
        }
      }
    }
    update(
        connection,
        "UPDATE users SET user_admin = false WHERE user_admin AND user_id <> ?",
        ADMIN_UUID);
    log.info("Admin flag replaced by admin group memberships for users {}", legacyAdmins);
  }

  private static void ensurePlatformAdminGroup(Connection connection) throws SQLException {
    List<String> existing =
        queryIds(
            connection,
            "SELECT group_id FROM groups WHERE group_id = ?",
            PLATFORM_ADMIN_ACCESS.groupId());
    if (!existing.isEmpty()) {
      return;
    }
    try (Statement statement = connection.createStatement()) {
      statement.execute("SELECT set_config('app.platform_write', 'true', true)");
    }
    update(
        connection,
        "INSERT INTO roles (role_id, role_name, role_description, role_created_at, role_updated_at,"
            + " tenant_id) VALUES (?, ?, ?, now(), now(), NULL) ON CONFLICT (role_id) DO NOTHING",
        PLATFORM_ADMIN_ACCESS.roleId(),
        PLATFORM_ADMIN_ACCESS.roleName(),
        PLATFORM_ADMIN_ACCESS.roleDescription());
    for (Capability capability : PLATFORM_ADMIN_ACCESS.capabilities()) {
      update(
          connection,
          "INSERT INTO roles_capabilities (role_id, capability) VALUES (?, ?)"
              + " ON CONFLICT DO NOTHING",
          PLATFORM_ADMIN_ACCESS.roleId(),
          capability.name());
    }
    update(
        connection,
        "INSERT INTO groups (group_id, group_name, group_description, group_default_user_assign,"
            + " tenant_id) VALUES (?, ?, ?, false, NULL)",
        PLATFORM_ADMIN_ACCESS.groupId(),
        PLATFORM_ADMIN_ACCESS.groupName(),
        PLATFORM_ADMIN_ACCESS.groupDescription());
    update(
        connection,
        "INSERT INTO groups_roles (group_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        PLATFORM_ADMIN_ACCESS.groupId(),
        PLATFORM_ADMIN_ACCESS.roleId());
  }

  /**
   * Same resolution as {@code AdminPrivilegeService}: the well-known group of the tenant, else its
   * group named Admin with a role granting {@code BYPASS}, never a group that only shares the name.
   */
  private static Optional<String> tenantAdminGroupId(Connection connection, String tenantId)
      throws SQLException {
    String wellKnownId = getUUIDFromName(ADMIN_ACCESS.groupId(), tenantId);
    return queryIds(
            connection,
            "SELECT g.group_id FROM groups g WHERE g.tenant_id = ? AND (g.group_id = ?"
                + " OR (g.group_name = ? AND EXISTS (SELECT 1 FROM groups_roles gr"
                + " JOIN roles_capabilities rc ON rc.role_id = gr.role_id"
                + " WHERE gr.group_id = g.group_id AND rc.capability = ?)))"
                + " ORDER BY g.group_id = ? DESC, g.group_id LIMIT 1",
            tenantId,
            wellKnownId,
            ADMIN_ACCESS.groupName(),
            Capability.BYPASS.name(),
            wellKnownId)
        .stream()
        .findFirst();
  }

  private static void addMembership(Connection connection, String userId, String groupId)
      throws SQLException {
    update(
        connection,
        "INSERT INTO users_groups (group_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        groupId,
        userId);
  }

  private static List<String> queryIds(Connection connection, String sql, String... params)
      throws SQLException {
    try (PreparedStatement statement = prepare(connection, sql, params);
        ResultSet result = statement.executeQuery()) {
      List<String> ids = new ArrayList<>();
      while (result.next()) {
        ids.add(result.getString(1));
      }
      return ids;
    }
  }

  private static void update(Connection connection, String sql, String... params)
      throws SQLException {
    try (PreparedStatement statement = prepare(connection, sql, params)) {
      statement.executeUpdate();
    }
  }

  private static PreparedStatement prepare(Connection connection, String sql, String... params)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    for (int i = 0; i < params.length; i++) {
      statement.setString(i + 1, params[i]);
    }
    return statement;
  }
}
