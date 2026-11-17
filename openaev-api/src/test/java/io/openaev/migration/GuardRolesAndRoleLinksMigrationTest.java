package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.service.TenantGroupService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies, on the live schema, the triggers that guard {@code roles}, {@code roles_capabilities}
 * and {@code groups_roles} against every client of the connection: a platform role and its
 * capabilities are written only by a transaction that set {@code app.platform_write}, and a role is
 * linked to a group of its own tenant only.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: each write is a committed JDBC statement
 * in a transaction of its own, so the trigger sees exactly what a client of the connection sends,
 * and every row it commits is removed in {@code @AfterEach}.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Guard of roles, their capabilities and their links to groups")
class GuardRolesAndRoleLinksMigrationTest extends IntegrationTest {

  private static final String PLATFORM_REFUSAL = "platform-write";
  private static final String TENANT_REFUSAL = "not of the same tenant";
  private static final String PLATFORM_WRITE = "app.platform_write=true";
  private static final String REPLICA = "session_replication_role=replica";

  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private TenantGroupService tenantGroupService;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String platformGroup;
  private String groupA;
  private String groupB;
  private final List<String> roles = new ArrayList<>();
  private final List<String> groups = new ArrayList<>();

  // -- FIXTURE --

  @BeforeEach
  void twoTenantsAndAGroupInEachScope() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = tenantIsolationHelper.createTenant("role-guard-a").getId();
    tenantB = tenantIsolationHelper.createTenant("role-guard-b").getId();
    platformGroup = seedGroup(null);
    groupA = seedGroup(tenantA);
    groupB = seedGroup(tenantB);
  }

  @AfterEach
  void removeEveryCommittedRow() throws Exception {
    // A transaction that sets the marker may delete any of the rows, platform or tenant
    for (String group : groups) {
      execute(PLATFORM_WRITE, "DELETE FROM groups_roles WHERE group_id = ?", group);
      execute(PLATFORM_WRITE, "DELETE FROM groups WHERE group_id = ?", group);
    }
    for (String role : roles) {
      execute(PLATFORM_WRITE, "DELETE FROM roles_capabilities WHERE role_id = ?", role);
      execute(PLATFORM_WRITE, "DELETE FROM roles WHERE role_id = ?", role);
    }
    tenantIsolationHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  /**
   * One committed transaction that first applies the transaction-local settings, each written
   * {@code name=value}, then runs the statement. A refused statement rolls the transaction back and
   * rethrows the database error.
   */
  private int execute(String setting, String sql, Object... arguments) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        if (setting != null) {
          String[] nameAndValue = setting.split("=", 2);
          try (PreparedStatement apply =
              connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            apply.setString(1, nameAndValue[0]);
            apply.setString(2, nameAndValue[1]);
            apply.execute();
          }
        }
        int rows;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
          for (int i = 0; i < arguments.length; i++) {
            statement.setObject(i + 1, arguments[i]);
          }
          rows = statement.executeUpdate();
        }
        connection.commit();
        return rows;
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      }
    }
  }

  private String seedRole(String tenantId) throws SQLException {
    String roleId = UUID.randomUUID().toString();
    insertRole(PLATFORM_WRITE, roleId, tenantId);
    return roleId;
  }

  private String seedGroup(String tenantId) throws SQLException {
    String groupId = UUID.randomUUID().toString();
    execute(
        null,
        "INSERT INTO groups (group_id, group_name, tenant_id) VALUES (?, ?, ?)",
        groupId,
        "guard-group-" + groupId,
        tenantId);
    groups.add(groupId);
    return groupId;
  }

  private int insertRole(String setting, String roleId, String tenantId) throws SQLException {
    roles.add(roleId);
    return execute(
        setting,
        "INSERT INTO roles (role_id, role_name, role_created_at, role_updated_at, tenant_id)"
            + " VALUES (?, ?, now(), now(), ?)",
        roleId,
        "guard-role-" + roleId,
        tenantId);
  }

  private int insertCapability(String setting, String roleId) throws SQLException {
    return execute(
        setting,
        "INSERT INTO roles_capabilities (role_id, capability) VALUES (?, 'ACCESS_ASSESSMENT')",
        roleId);
  }

  private int link(String setting, String roleId, String groupId) throws SQLException {
    return execute(
        setting, "INSERT INTO groups_roles (group_id, role_id) VALUES (?, ?)", groupId, roleId);
  }

  private int count(String sql, Object... arguments) {
    Integer rows = jdbc.queryForObject(sql, Integer.class, arguments);
    return rows == null ? 0 : rows;
  }

  // -- ROLES --

  @Nested
  @DisplayName("a role")
  class Roles {

    @Test
    @DisplayName("given_noMarker_should_refuseInsertingAPlatformRole")
    void given_noMarker_should_refuseInsertingAPlatformRole() {
      // Arrange
      String roleId = UUID.randomUUID().toString();

      // Act + Assert
      assertThatThrownBy(() -> insertRole(null, roleId, null))
          .hasMessageContaining(PLATFORM_REFUSAL);
      assertThat(count("SELECT count(*) FROM roles WHERE role_id = ?", roleId)).isZero();
    }

    @Test
    @DisplayName("given_theMarker_should_acceptInsertingAPlatformRole")
    void given_theMarker_should_acceptInsertingAPlatformRole() throws Exception {
      // Arrange
      String roleId = UUID.randomUUID().toString();

      // Act
      int inserted = insertRole(PLATFORM_WRITE, roleId, null);

      // Assert
      assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("given_noMarker_should_refuseUpdatingAPlatformRole")
    void given_noMarker_should_refuseUpdatingAPlatformRole() throws Exception {
      // Arrange
      String roleId = seedRole(null);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  execute(null, "UPDATE roles SET role_name = 'renamed' WHERE role_id = ?", roleId))
          .hasMessageContaining(PLATFORM_REFUSAL);
      assertThat(
              count(
                  "SELECT count(*) FROM roles WHERE role_id = ? AND role_name = 'renamed'", roleId))
          .isZero();
    }

    @Test
    @DisplayName("given_theMarker_should_acceptUpdatingAPlatformRole")
    void given_theMarker_should_acceptUpdatingAPlatformRole() throws Exception {
      // Arrange
      String roleId = seedRole(null);

      // Act
      int updated =
          execute(
              PLATFORM_WRITE, "UPDATE roles SET role_name = 'renamed' WHERE role_id = ?", roleId);

      // Assert
      assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("given_noMarker_should_refuseMovingATenantRoleToThePlatform")
    void given_noMarker_should_refuseMovingATenantRoleToThePlatform() throws Exception {
      // Arrange
      String roleId = seedRole(tenantA);

      // Act + Assert
      assertThatThrownBy(
              () -> execute(null, "UPDATE roles SET tenant_id = NULL WHERE role_id = ?", roleId))
          .hasMessageContaining(PLATFORM_REFUSAL);
      assertThat(
              count(
                  "SELECT count(*) FROM roles WHERE role_id = ? AND tenant_id = ?",
                  roleId,
                  tenantA))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("given_noMarker_should_refuseDeletingAPlatformRole")
    void given_noMarker_should_refuseDeletingAPlatformRole() throws Exception {
      // Arrange
      String roleId = seedRole(null);

      // Act + Assert
      assertThatThrownBy(() -> execute(null, "DELETE FROM roles WHERE role_id = ?", roleId))
          .hasMessageContaining(PLATFORM_REFUSAL);
      assertThat(count("SELECT count(*) FROM roles WHERE role_id = ?", roleId)).isEqualTo(1);
    }

    @Test
    @DisplayName("given_theMarker_should_acceptDeletingAPlatformRole")
    void given_theMarker_should_acceptDeletingAPlatformRole() throws Exception {
      // Arrange
      String roleId = seedRole(null);

      // Act
      int deleted = execute(PLATFORM_WRITE, "DELETE FROM roles WHERE role_id = ?", roleId);

      // Assert
      assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("given_noMarker_should_writeATenantRole")
    void given_noMarker_should_writeATenantRole() throws Exception {
      // Arrange
      String roleId = UUID.randomUUID().toString();

      // Act + Assert
      assertThat(insertRole(null, roleId, tenantA)).isEqualTo(1);
      assertThat(execute(null, "UPDATE roles SET role_name = 'renamed' WHERE role_id = ?", roleId))
          .isEqualTo(1);
      assertThat(execute(null, "DELETE FROM roles WHERE role_id = ?", roleId)).isEqualTo(1);
    }
  }

  // -- ROLES CAPABILITIES --

  @Nested
  @DisplayName("the capabilities of a role")
  class Capabilities {

    @Test
    @DisplayName("given_noMarker_should_refuseAddingACapabilityToAPlatformRole")
    void given_noMarker_should_refuseAddingACapabilityToAPlatformRole() throws Exception {
      // Arrange
      String roleId = seedRole(null);

      // Act + Assert
      assertThatThrownBy(() -> insertCapability(null, roleId))
          .hasMessageContaining(PLATFORM_REFUSAL);
      assertThat(count("SELECT count(*) FROM roles_capabilities WHERE role_id = ?", roleId))
          .isZero();
    }

    @Test
    @DisplayName("given_theMarker_should_acceptAddingACapabilityToAPlatformRole")
    void given_theMarker_should_acceptAddingACapabilityToAPlatformRole() throws Exception {
      // Arrange
      String roleId = seedRole(null);

      // Act
      int inserted = insertCapability(PLATFORM_WRITE, roleId);

      // Assert
      assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("given_noMarker_should_refuseChangingOrRemovingACapabilityOfAPlatformRole")
    void given_noMarker_should_refuseChangingOrRemovingACapabilityOfAPlatformRole()
        throws Exception {
      // Arrange
      String roleId = seedRole(null);
      insertCapability(PLATFORM_WRITE, roleId);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  execute(
                      null,
                      "UPDATE roles_capabilities SET capability = 'ACCESS_PAYLOADS'"
                          + " WHERE role_id = ?",
                      roleId))
          .hasMessageContaining(PLATFORM_REFUSAL);
      assertThatThrownBy(
              () -> execute(null, "DELETE FROM roles_capabilities WHERE role_id = ?", roleId))
          .hasMessageContaining(PLATFORM_REFUSAL);
      assertThat(
              count(
                  "SELECT count(*) FROM roles_capabilities WHERE role_id = ?"
                      + " AND capability = 'ACCESS_ASSESSMENT'",
                  roleId))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("given_noMarker_should_writeTheCapabilitiesOfATenantRole")
    void given_noMarker_should_writeTheCapabilitiesOfATenantRole() throws Exception {
      // Arrange
      String roleId = seedRole(tenantA);

      // Act + Assert
      assertThat(insertCapability(null, roleId)).isEqualTo(1);
      assertThat(
              execute(
                  null,
                  "UPDATE roles_capabilities SET capability = 'ACCESS_PAYLOADS' WHERE role_id = ?",
                  roleId))
          .isEqualTo(1);
      assertThat(execute(null, "DELETE FROM roles_capabilities WHERE role_id = ?", roleId))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("given_noRole_should_leaveTheRefusalToTheForeignKey")
    void given_noRole_should_leaveTheRefusalToTheForeignKey() {
      // Act + Assert
      assertThatThrownBy(() -> insertCapability(null, UUID.randomUUID().toString()))
          .hasMessageContaining("foreign key");
    }
  }

  // -- GROUPS ROLES --

  @Nested
  @DisplayName("the link of a role to a group")
  class Links {

    @Test
    @DisplayName("given_aTenantRoleAndAGroupOfAnotherTenant_should_refuseTheLink")
    void given_aTenantRoleAndAGroupOfAnotherTenant_should_refuseTheLink() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);

      // Act + Assert
      assertThatThrownBy(() -> link(null, roleOfA, groupB)).hasMessageContaining(TENANT_REFUSAL);
      assertThat(count("SELECT count(*) FROM groups_roles WHERE role_id = ?", roleOfA)).isZero();
    }

    @Test
    @DisplayName("given_aTenantRoleAndAPlatformGroup_should_refuseTheLink")
    void given_aTenantRoleAndAPlatformGroup_should_refuseTheLink() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);

      // Act + Assert
      assertThatThrownBy(() -> link(null, roleOfA, platformGroup))
          .hasMessageContaining(TENANT_REFUSAL);
    }

    @Test
    @DisplayName("given_aPlatformRoleAndAGroupOfATenant_should_refuseTheLink")
    void given_aPlatformRoleAndAGroupOfATenant_should_refuseTheLink() throws Exception {
      // Arrange
      String platformRole = seedRole(null);

      // Act + Assert
      assertThatThrownBy(() -> link(null, platformRole, groupA))
          .hasMessageContaining(TENANT_REFUSAL);
    }

    @Test
    @DisplayName("given_aPlatformRoleAndAPlatformGroup_should_acceptTheLink")
    void given_aPlatformRoleAndAPlatformGroup_should_acceptTheLink() throws Exception {
      // Arrange
      String platformRole = seedRole(null);

      // Act + Assert: a link is not a write of the platform role, so no marker is needed
      assertThat(link(null, platformRole, platformGroup)).isEqualTo(1);
    }

    @Test
    @DisplayName("given_aTenantRoleAndAGroupOfTheSameTenant_should_acceptTheLink")
    void given_aTenantRoleAndAGroupOfTheSameTenant_should_acceptTheLink() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);

      // Act + Assert
      assertThat(link(null, roleOfA, groupA)).isEqualTo(1);
    }

    @Test
    @DisplayName("given_aLinkMovedToAGroupOfAnotherTenant_should_refuseTheUpdate")
    void given_aLinkMovedToAGroupOfAnotherTenant_should_refuseTheUpdate() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);
      link(null, roleOfA, groupA);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  execute(
                      null,
                      "UPDATE groups_roles SET group_id = ? WHERE role_id = ?",
                      groupB,
                      roleOfA))
          .hasMessageContaining(TENANT_REFUSAL);
      assertThat(
              count(
                  "SELECT count(*) FROM groups_roles WHERE role_id = ? AND group_id = ?",
                  roleOfA,
                  groupA))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("given_noRoleOrNoGroup_should_leaveTheRefusalToTheForeignKey")
    void given_noRoleOrNoGroup_should_leaveTheRefusalToTheForeignKey() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);

      // Act + Assert
      assertThatThrownBy(() -> link(null, UUID.randomUUID().toString(), groupA))
          .hasMessageContaining("foreign key");
      assertThatThrownBy(() -> link(null, roleOfA, UUID.randomUUID().toString()))
          .hasMessageContaining("foreign key");
    }

    @Test
    @DisplayName("given_replicaSessionRole_should_letASeedCommitACrossTenantLink")
    void given_replicaSessionRole_should_letASeedCommitACrossTenantLink() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);

      // Act: the bypass a test uses to seed what the guard refuses, as DatabaseSnapshotManager does
      int linked = link(REPLICA, roleOfA, groupB);

      // Assert
      assertThat(linked).isEqualTo(1);
    }
  }

  // -- THE TENANT OF A LINKED ROLE OR GROUP --

  @Nested
  @DisplayName("the tenant of a role or a group that has links")
  class TenantOfLinkedParents {

    @Test
    @DisplayName("given_aLinkedTenantRole_should_refuseMovingItToAnotherTenant")
    void given_aLinkedTenantRole_should_refuseMovingItToAnotherTenant() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);
      link(null, roleOfA, groupA);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  execute(
                      null, "UPDATE roles SET tenant_id = ? WHERE role_id = ?", tenantB, roleOfA))
          .hasMessageContaining(TENANT_REFUSAL);
      assertThat(
              count(
                  "SELECT count(*) FROM roles WHERE role_id = ? AND tenant_id = ?",
                  roleOfA,
                  tenantA))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("given_aLinkedTenantRoleAndTheMarker_should_refuseMovingItToThePlatform")
    void given_aLinkedTenantRoleAndTheMarker_should_refuseMovingItToThePlatform() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);
      link(null, roleOfA, groupA);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  execute(
                      PLATFORM_WRITE,
                      "UPDATE roles SET tenant_id = NULL WHERE role_id = ?",
                      roleOfA))
          .hasMessageContaining(TENANT_REFUSAL);
    }

    @Test
    @DisplayName("given_aLinkedTenantGroup_should_refuseMovingItToThePlatform")
    void given_aLinkedTenantGroup_should_refuseMovingItToThePlatform() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);
      link(null, roleOfA, groupA);

      // Act + Assert
      assertThatThrownBy(
              () -> execute(null, "UPDATE groups SET tenant_id = NULL WHERE group_id = ?", groupA))
          .hasMessageContaining(TENANT_REFUSAL);
      assertThat(
              count(
                  "SELECT count(*) FROM groups WHERE group_id = ? AND tenant_id = ?",
                  groupA,
                  tenantA))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("given_aLinkedTenantGroup_should_refuseMovingItToAnotherTenant")
    void given_aLinkedTenantGroup_should_refuseMovingItToAnotherTenant() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);
      link(null, roleOfA, groupA);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  execute(
                      null, "UPDATE groups SET tenant_id = ? WHERE group_id = ?", tenantB, groupA))
          .hasMessageContaining(TENANT_REFUSAL);
    }

    @Test
    @DisplayName("given_aLinkedPlatformGroup_should_refuseMovingItToATenant")
    void given_aLinkedPlatformGroup_should_refuseMovingItToATenant() throws Exception {
      // Arrange
      String platformRole = seedRole(null);
      link(PLATFORM_WRITE, platformRole, platformGroup);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  execute(
                      null,
                      "UPDATE groups SET tenant_id = ? WHERE group_id = ?",
                      tenantA,
                      platformGroup))
          .hasMessageContaining(TENANT_REFUSAL);
    }

    @Test
    @DisplayName("given_aRoleAndAGroupWithNoLink_should_acceptMovingThemToAnotherTenant")
    void given_aRoleAndAGroupWithNoLink_should_acceptMovingThemToAnotherTenant() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);

      // Act
      int movedRole =
          execute(null, "UPDATE roles SET tenant_id = ? WHERE role_id = ?", tenantB, roleOfA);
      int movedGroup =
          execute(null, "UPDATE groups SET tenant_id = ? WHERE group_id = ?", tenantB, groupA);

      // Assert
      assertThat(movedRole).isEqualTo(1);
      assertThat(movedGroup).isEqualTo(1);
    }

    @Test
    @DisplayName("given_aLinkedGroup_should_acceptAWriteThatLeavesItsTenantAsItIs")
    void given_aLinkedGroup_should_acceptAWriteThatLeavesItsTenantAsItIs() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);
      link(null, roleOfA, groupA);

      // Act
      int renamed =
          execute(null, "UPDATE groups SET group_name = 'renamed' WHERE group_id = ?", groupA);

      // Assert
      assertThat(renamed).isEqualTo(1);
    }

    @Test
    @DisplayName("given_replicaSessionRole_should_letASeedMoveALinkedGroup")
    void given_replicaSessionRole_should_letASeedMoveALinkedGroup() throws Exception {
      // Arrange
      String roleOfA = seedRole(tenantA);
      link(null, roleOfA, groupA);

      // Act
      int moved = execute(REPLICA, "UPDATE groups SET tenant_id = NULL WHERE group_id = ?", groupA);

      // Assert
      assertThat(moved).isEqualTo(1);
    }
  }

  // -- APPLICATION WRITES --

  @Nested
  @DisplayName("the writes of the application")
  class ApplicationWrites {

    @Test
    @DisplayName("given_theDefaultGroupsOfATenantCreated_should_linkThemToRolesOfThatTenant")
    void given_theDefaultGroupsOfATenantCreated_should_linkThemToRolesOfThatTenant() {
      // Arrange
      int linksBefore =
          count(
              "SELECT count(*) FROM groups_roles gr JOIN groups g ON g.group_id = gr.group_id"
                  + " WHERE g.tenant_id = ?",
              tenantA);

      // Act: the onboarding step that creates the default roles and groups of a tenant, in the
      // tenant-scoped transaction the data pack processor opens around it
      tenantScopedTransaction.execute(
          TxCtx.forTenant(tenantA), () -> tenantGroupService.findOrCreateDefaultGroups(tenantA));

      // Assert
      int links =
          count(
              "SELECT count(*) FROM groups_roles gr JOIN groups g ON g.group_id = gr.group_id"
                  + " WHERE g.tenant_id = ?",
              tenantA);
      int linksToAnotherScope =
          count(
              "SELECT count(*) FROM groups_roles gr JOIN groups g ON g.group_id = gr.group_id"
                  + " JOIN roles r ON r.role_id = gr.role_id"
                  + " WHERE g.tenant_id = ? AND r.tenant_id IS DISTINCT FROM g.tenant_id",
              tenantA);

      assertThat(linksBefore).isZero();
      assertThat(links).isPositive();
      assertThat(linksToAnotherScope).isZero();
    }
  }

  // -- CASCADES --

  @Nested
  @DisplayName("a cascade")
  class Cascades {

    @Test
    @DisplayName("given_aTenantDeleted_should_cascadeItsRolesGroupsAndLinksWithNoMarker")
    void given_aTenantDeleted_should_cascadeItsRolesGroupsAndLinksWithNoMarker() throws Exception {
      // Arrange
      String roleOfB = seedRole(tenantB);
      insertCapability(null, roleOfB);
      link(null, roleOfB, groupB);

      // Act
      tenantIsolationHelper.deleteCommittedTenants(tenantB);

      // Assert
      assertThat(count("SELECT count(*) FROM roles WHERE role_id = ?", roleOfB)).isZero();
      assertThat(count("SELECT count(*) FROM roles_capabilities WHERE role_id = ?", roleOfB))
          .isZero();
      assertThat(count("SELECT count(*) FROM groups_roles WHERE role_id = ?", roleOfB)).isZero();
      assertThat(count("SELECT count(*) FROM groups WHERE group_id = ?", groupB)).isZero();
    }

    @Test
    @DisplayName("given_aGroupDeleted_should_cascadeItsLinksWithNoMarker")
    void given_aGroupDeleted_should_cascadeItsLinksWithNoMarker() throws Exception {
      // Arrange
      String platformRole = seedRole(null);
      link(null, platformRole, platformGroup);

      // Act
      execute(null, "DELETE FROM groups WHERE group_id = ?", platformGroup);

      // Assert
      assertThat(count("SELECT count(*) FROM groups_roles WHERE role_id = ?", platformRole))
          .isZero();
    }

    @Test
    @DisplayName("given_aPlatformRoleDeletedUnderTheMarker_should_cascadeItsCapabilitiesAndLinks")
    void given_aPlatformRoleDeletedUnderTheMarker_should_cascadeItsCapabilitiesAndLinks()
        throws Exception {
      // Arrange
      String platformRole = seedRole(null);
      insertCapability(PLATFORM_WRITE, platformRole);
      link(null, platformRole, platformGroup);

      // Act
      execute(PLATFORM_WRITE, "DELETE FROM roles WHERE role_id = ?", platformRole);

      // Assert
      assertThat(count("SELECT count(*) FROM roles_capabilities WHERE role_id = ?", platformRole))
          .isZero();
      assertThat(count("SELECT count(*) FROM groups_roles WHERE role_id = ?", platformRole))
          .isZero();
    }
  }

  // -- CONCURRENT TRANSACTIONS --

  /** A connection with a transaction of its own, left open until the test commits it. */
  private final class OpenTransaction implements AutoCloseable {
    private final Connection connection;
    private final int backendPid;

    OpenTransaction() throws SQLException {
      connection = dataSource.getConnection();
      connection.setAutoCommit(false);
      try (PreparedStatement bound =
          connection.prepareStatement(
              "SELECT set_config('lock_timeout', '30s', true), pg_backend_pid()")) {
        try (var result = bound.executeQuery()) {
          result.next();
          backendPid = result.getInt(2);
        }
      }
    }

    void run(String sql, Object... arguments) throws SQLException {
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        for (int i = 0; i < arguments.length; i++) {
          statement.setObject(i + 1, arguments[i]);
        }
        statement.execute();
      }
    }

    void commit() throws SQLException {
      connection.commit();
    }

    void rollback() throws SQLException {
      connection.rollback();
    }

    boolean isWaitingOnALock() {
      Boolean waiting =
          jdbc.queryForObject(
              "SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, backendPid);
      return Boolean.TRUE.equals(waiting);
    }

    @Override
    public void close() throws SQLException {
      connection.close();
    }
  }

  /**
   * Runs {@code first} on a connection and leaves its transaction open, starts {@code second} on
   * another connection, waits until {@code second} is blocked or finished, then commits {@code
   * first}. Returns the error {@code second} ended with, or null when it committed.
   */
  private SQLException raceOpenFirst(
      String firstSql, Object[] firstArguments, String secondSql, Object[] secondArguments)
      throws Exception {
    try (OpenTransaction first = new OpenTransaction();
        OpenTransaction second = new OpenTransaction()) {
      first.run(firstSql, firstArguments);
      CompletableFuture<SQLException> outcome =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  second.run(secondSql, secondArguments);
                  second.commit();
                  return null;
                } catch (SQLException e) {
                  try {
                    second.rollback();
                  } catch (SQLException ignored) {
                    // the connection is closed with the transaction
                  }
                  return e;
                }
              });
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (!outcome.isDone() && !second.isWaitingOnALock() && System.nanoTime() < deadline) {
        TimeUnit.MILLISECONDS.sleep(50);
      }
      first.commit();
      try {
        return outcome.get(30, TimeUnit.SECONDS);
      } catch (ExecutionException | TimeoutException e) {
        throw new IllegalStateException("The second transaction did not end", e);
      }
    }
  }

  @Nested
  @DisplayName("a link and a tenant change of a parent in concurrent transactions")
  class ConcurrentTenantChange {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
      "a role moves while a link to it is written, role, true",
      "a role moves after a link to it is written, role, false",
      "a group moves while a link to it is written, group, true",
      "a group moves after a link to it is written, group, false"
    })
    @DisplayName("given_aParentMoveAndALink_should_refuseTheSecondAndLeaveNoLinkAcrossTenants")
    void given_aParentMoveAndALink_should_refuseTheSecondAndLeaveNoLinkAcrossTenants(
        String ignoredName, String parent, boolean linkFirst) throws Exception {
      // Arrange: a role and a group of tenant A, with no link, so either can change tenant
      String role = seedRole(tenantA);
      String movedId = parent.equals("role") ? role : groupA;
      String moveSql =
          parent.equals("role")
              ? "UPDATE roles SET tenant_id = ? WHERE role_id = ?"
              : "UPDATE groups SET tenant_id = ? WHERE group_id = ?";
      Object[] moveArguments = {tenantB, movedId};
      String linkSql = "INSERT INTO groups_roles (group_id, role_id) VALUES (?, ?)";
      Object[] linkArguments = {groupA, role};

      // Act: the transaction that goes first holds its statement open; the other one is blocked
      // by it, or runs after it
      SQLException refused =
          linkFirst
              ? raceOpenFirst(linkSql, linkArguments, moveSql, moveArguments)
              : raceOpenFirst(moveSql, moveArguments, linkSql, linkArguments);

      // Assert: the second transaction is refused and the link joins parents of one tenant
      assertThat((Throwable) refused).isNotNull().hasMessageContaining(TENANT_REFUSAL);
      assertThat(
              count(
                  "SELECT count(*) FROM groups_roles gr"
                      + " JOIN roles r ON r.role_id = gr.role_id"
                      + " JOIN groups g ON g.group_id = gr.group_id"
                      + " WHERE gr.role_id = ? AND r.tenant_id IS DISTINCT FROM g.tenant_id",
                  role))
          .isZero();
    }

    @Test
    @DisplayName("given_aRoleMovedToThePlatform_should_refuseACapabilityWrittenWithNoMarker")
    void given_aRoleMovedToThePlatform_should_refuseACapabilityWrittenWithNoMarker()
        throws Exception {
      // Arrange
      String role = seedRole(tenantA);

      // Act: a transaction that holds the marker moves the role to the platform, and a capability
      // is written in a transaction that does not hold it
      SQLException refused;
      try (OpenTransaction mover = new OpenTransaction();
          OpenTransaction writer = new OpenTransaction()) {
        mover.run("SELECT set_config('app.platform_write', 'true', true)");
        mover.run("UPDATE roles SET tenant_id = NULL WHERE role_id = ?", role);
        CompletableFuture<SQLException> outcome =
            CompletableFuture.supplyAsync(
                () -> {
                  try {
                    writer.run(
                        "INSERT INTO roles_capabilities (role_id, capability)"
                            + " VALUES (?, 'ACCESS_ASSESSMENT')",
                        role);
                    writer.commit();
                    return null;
                  } catch (SQLException e) {
                    return e;
                  }
                });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!outcome.isDone() && !writer.isWaitingOnALock() && System.nanoTime() < deadline) {
          TimeUnit.MILLISECONDS.sleep(50);
        }
        mover.commit();
        refused = outcome.get(30, TimeUnit.SECONDS);
      }

      // Assert
      assertThat((Throwable) refused).isNotNull().hasMessageContaining(PLATFORM_REFUSAL);
      assertThat(count("SELECT count(*) FROM roles_capabilities WHERE role_id = ?", role)).isZero();
    }
  }
}
