package io.openaev.migration;

import io.openaev.database.model.Capability;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Gives "Approve content" to every tenant role that can manage the threat arsenal, so existing
 * authors keep being auto-approved after the upgrade: maker-checker becomes opt-in, by removing the
 * capability from a role. Runs once; roles created or edited afterwards are never ticked.
 *
 * <p>{@code DELETE_THREAT_ARSENALS} implies Manage and is matched too, for roles whose parents were
 * never stored. {@code tenant_id IS NOT NULL} keeps platform roles out: the capability is
 * tenant-scoped. {@code ON CONFLICT DO NOTHING} makes a re-run a no-op.
 */
@Component
@Slf4j
public class V6_20261008160000000__Grant_approve_content_to_threat_arsenal_managers
    extends BaseJavaMigration {

  private static final List<Capability> MANAGERS =
      List.of(Capability.MANAGE_THREAT_ARSENALS, Capability.DELETE_THREAT_ARSENALS);

  @Override
  public void migrate(Context context) throws Exception {
    grant(context.getConnection());
  }

  /** Returns the ids of the roles that received "Approve content" (empty on a re-run). */
  List<String> grant(Connection connection) throws Exception {
    Set<String> updated = new LinkedHashSet<>();
    updated.addAll(insert(connection, Capability.APPROVE_THREAT_ARSENALS));
    // Parent of Approve content: a role reaching Manage only through Delete may lack it
    insert(connection, Capability.ACCESS_THREAT_ARSENALS);
    List<String> roleIds = new ArrayList<>(updated);
    if (roleIds.isEmpty()) {
      log.info("Approve content: no tenant role to update");
    } else {
      log.info(
          "Approve content granted to {} tenant role(s) that can manage the threat arsenal: {}",
          roleIds.size(),
          describe(connection, roleIds));
    }
    return roleIds;
  }

  private List<String> insert(Connection connection, Capability granted) throws Exception {
    String sql =
        "INSERT INTO roles_capabilities (role_id, capability)"
            + " SELECT DISTINCT rc.role_id, ?"
            + " FROM roles_capabilities rc"
            + " JOIN roles r ON r.role_id = rc.role_id"
            + " WHERE r.tenant_id IS NOT NULL"
            + " AND rc.capability IN (?, ?)"
            + " ON CONFLICT DO NOTHING"
            + " RETURNING role_id";
    List<String> roleIds = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, granted.name());
      statement.setString(2, MANAGERS.get(0).name());
      statement.setString(3, MANAGERS.get(1).name());
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          roleIds.add(rows.getString(1));
        }
      }
    }
    return roleIds;
  }

  /** "name (id, tenant)" for each role, for the upgrade log. */
  private String describe(Connection connection, List<String> roleIds) throws Exception {
    List<String> descriptions = new ArrayList<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT role_id, role_name, tenant_id FROM roles WHERE role_id = ANY (?)"
                + " ORDER BY tenant_id, role_name")) {
      statement.setArray(1, connection.createArrayOf("varchar", roleIds.toArray()));
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          descriptions.add(
              "%s (id %s, tenant %s)"
                  .formatted(rows.getString(2), rows.getString(1), rows.getString(3)));
        }
      }
    }
    return String.join(", ", descriptions);
  }
}
