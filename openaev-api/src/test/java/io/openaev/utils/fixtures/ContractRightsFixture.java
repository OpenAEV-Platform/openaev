package io.openaev.utils.fixtures;

import static io.openaev.utils.PlatformWriteJdbc.inPlatformWriteTransaction;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.injectors.manual.ManualContract;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Committed rights and contract copies for the tests that decide, per tenant copy of a contract,
 * what a caller may see: groups of a tenant or of the platform, the roles and grants they hold, and
 * copies of one contract id under several tenants. Every row it creates is removed by {@link
 * #cleanup}, the groups of the mock user included, so a class leaves {@code groups}, {@code
 * grants}, {@code users_groups}, {@code users_tenants} and {@code tenants} as it found them.
 */
public class ContractRightsFixture {

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final List<String> groups = new ArrayList<>();
  private final List<String> roles = new ArrayList<>();
  private final List<String> users = new ArrayList<>();
  private final List<String[]> rows = new ArrayList<>();

  public ContractRightsFixture(DataSource dataSource) {
    this.dataSource = dataSource;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  /**
   * A group of {@code tenantId} (a platform group when null) the user is a member of, holding one
   * role with {@code capabilities}; no role when there are none.
   */
  public String group(String tenantId, String userId, Set<Capability> capabilities) {
    String groupId = "rights-group-" + UUID.randomUUID();
    groups.add(groupId);
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, tenant_id) VALUES (?, ?, ?)",
        groupId,
        groupId,
        tenantId);
    jdbc.update("INSERT INTO users_groups (group_id, user_id) VALUES (?, ?)", groupId, userId);
    if (!capabilities.isEmpty()) {
      String roleId = "rights-role-" + UUID.randomUUID();
      roles.add(roleId);
      // A platform role and its capabilities are written in a platform-write transaction only
      if (tenantId == null) {
        inPlatformWriteTransaction(
            dataSource, writer -> insertRole(writer, roleId, tenantId, capabilities));
      } else {
        insertRole(jdbc, roleId, tenantId, capabilities);
      }
      jdbc.update("INSERT INTO groups_roles (role_id, group_id) VALUES (?, ?)", roleId, groupId);
    }
    return groupId;
  }

  private static void insertRole(
      JdbcTemplate writer, String roleId, String tenantId, Set<Capability> capabilities) {
    writer.update(
        "INSERT INTO roles (role_id, role_name, tenant_id) VALUES (?, ?, ?)",
        roleId,
        roleId,
        tenantId);
    capabilities.forEach(
        capability ->
            writer.update(
                "INSERT INTO roles_capabilities (role_id, capability) VALUES (?, ?)",
                roleId,
                capability.name()));
  }

  /** An OBSERVER grant on a threat arsenal resource (a contract id or a payload identity). */
  public void grantObserver(String groupId, String resourceId) {
    grant(groupId, resourceId, Grant.GRANT_TYPE.OBSERVER, Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL);
  }

  /** A grant of {@code name} on a resource of {@code type}, held by the group. */
  public void grant(
      String groupId, String resourceId, Grant.GRANT_TYPE name, Grant.GRANT_RESOURCE_TYPE type) {
    jdbc.update(
        "INSERT INTO grants (grant_id, grant_group, grant_name, grant_resource,"
            + " grant_resource_type) VALUES (?, ?, ?, ?, ?)",
        "rights-grant-" + UUID.randomUUID(),
        groupId,
        name.name(),
        resourceId,
        type.name());
  }

  /** A user that is not the caller, removed by {@link #cleanup}; returns its id. */
  public String otherUser() {
    String userId = "rights-user-" + UUID.randomUUID();
    users.add(userId);
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 1)",
        userId,
        userId + "@example.invalid");
    return userId;
  }

  /**
   * Copies the tenant's built-in manual contract under {@code contractId}, labelled {@code label}.
   */
  public void cloneManualContract(String contractId, String tenantId, String label) {
    List<String> columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_name = 'injectors_contracts' AND table_schema = current_schema()",
            String.class);
    Set<String> replaced =
        Set.of(
            "injector_contract_id",
            "injector_contract_external_id",
            "injector_contract_labels",
            "injector_contract_custom");
    String copied =
        columns.stream().filter(c -> !replaced.contains(c)).collect(Collectors.joining(", "));
    jdbc.update(
        "INSERT INTO injectors_contracts ("
            + copied
            + ", injector_contract_id, injector_contract_external_id, injector_contract_labels,"
            + " injector_contract_custom) SELECT "
            + copied
            + ", ?, NULL, hstore('en', ?), true"
            + " FROM injectors_contracts WHERE injector_contract_id = ? AND tenant_id = ?",
        contractId,
        label,
        ManualContract.MANUAL_DEFAULT,
        tenantId);
    rows.add(new String[] {"injectors_contracts", "injector_contract_id", contractId});
  }

  /**
   * Gives the tenant's copy of {@code contractId} a payload of its own, returns its identity id.
   */
  public String withPayloadIdentity(String contractId, String tenantId) {
    String identityId = "rights-identity-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_source)"
            + " VALUES (?, ?, 'MANUAL')",
        identityId,
        tenantId);
    rows.add(new String[] {"payload_identities", "payload_identity_id", identityId});
    jdbc.update(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id,"
            + " payload_identity, payload_version) VALUES (?, 'Command', 'rights', ?, ?, '1.0.0')",
        identityId,
        tenantId,
        identityId);
    jdbc.update(
        "UPDATE injectors_contracts SET injector_contract_payload = ?"
            + " WHERE injector_contract_id = ? AND tenant_id = ?",
        identityId,
        contractId,
        tenantId);
    return identityId;
  }

  /** Maps one attack pattern of the tenant to its copy of {@code contractId}, returns its code. */
  public String attackPatternOf(String contractId, String tenantId) {
    String suffix = UUID.randomUUID().toString();
    String id = "rights-ap-" + suffix;
    String externalId = "T" + Math.abs(suffix.hashCode()) + ".9";
    jdbc.update(
        "INSERT INTO attack_patterns (attack_pattern_id, attack_pattern_stix_id,"
            + " attack_pattern_name, attack_pattern_external_id, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?)",
        id,
        "attack-pattern--" + suffix,
        id,
        externalId,
        tenantId);
    rows.add(new String[] {"attack_patterns", "attack_pattern_id", id});
    jdbc.update(
        "INSERT INTO injectors_contracts_attack_patterns (injector_contract_id, tenant_id,"
            + " attack_pattern_id) VALUES (?, ?, ?)",
        contractId,
        tenantId,
        id);
    return externalId;
  }

  /** Sets the platforms of the tenant's copy, for the facets that count them. */
  public void platformsOf(String contractId, String tenantId, String platform) {
    jdbc.update(
        "UPDATE injectors_contracts SET injector_contract_platforms = ?::text[]"
            + " WHERE injector_contract_id = ? AND tenant_id = ?",
        "{" + platform + "}",
        contractId,
        tenantId);
  }

  /** Rows of the built-in contract id in the tenant, ground truth outside any scope. */
  public long builtinRowsIn(String tenantId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM injectors_contracts WHERE injector_contract_id = ?"
                + " AND tenant_id = ?",
            Long.class,
            ManualContract.MANUAL_DEFAULT,
            tenantId);
    return count == null ? 0 : count;
  }

  /**
   * Removes what this fixture created and what the mock user's own setup left committed: its groups
   * with their roles and grants, its tenant memberships, the other users it made.
   */
  public void cleanup(String mockUserId) {
    List<String> userGroups =
        jdbc.queryForList(
            "SELECT group_id FROM users_groups WHERE user_id = ?", String.class, mockUserId);
    List<String> userRoles =
        userGroups.isEmpty()
            ? List.of()
            : jdbc.queryForList(
                "SELECT role_id FROM groups_roles WHERE group_id IN ("
                    + placeholders(userGroups.size())
                    + ")",
                String.class,
                userGroups.toArray());
    // the platform groups of this fixture hold the mock user too, so the user's groups cover them
    groups.addAll(userGroups);
    roles.addAll(userRoles);
    rows.forEach(row -> jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]));
    rows.clear();
    for (String groupId : groups) {
      jdbc.update("DELETE FROM grants WHERE grant_group = ?", groupId);
      jdbc.update("DELETE FROM users_groups WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    for (String roleId : roles) {
      inPlatformWriteTransaction(
          dataSource,
          writer -> {
            writer.update("DELETE FROM roles_capabilities WHERE role_id = ?", roleId);
            writer.update("DELETE FROM roles WHERE role_id = ?", roleId);
          });
    }
    for (String otherUserId : users) {
      jdbc.update("DELETE FROM users WHERE user_id = ?", otherUserId);
    }
    groups.clear();
    roles.clear();
    users.clear();
    if (mockUserId != null) {
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", mockUserId);
    }
  }

  private static String placeholders(int count) {
    return String.join(",", java.util.Collections.nCopies(count, "?"));
  }
}
