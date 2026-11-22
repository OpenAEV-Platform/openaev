package io.openaev.service.account;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.service.account.AbstractPrivilegeService.getUUIDFromName;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The membership of a service account, as the database holds it once its privilege service has
 * committed: its well-known group, and no other group of the scope the service runs in. Each
 * service writes the membership row by row in SQL, then attaches the account to its tenant, which
 * clears the persistence context, then saves the account it loaded before the writes: that save
 * must neither drop the group just granted nor write back the groups just revoked. A group of
 * another tenant is outside the scope the account's groups are read in, so it is left as it is,
 * like every other membership writer of that table.
 *
 * <p>Not transactional: each call opens and commits its own transaction, as the startup runner, the
 * onboarding and the request do, and the membership is read back through JDBC, outside any session.
 */
@WithMockUser(isAdmin = true)
@DisplayName("A service account holds exactly its well-known group once provisioned")
class ServiceAccountMembershipTest extends ProductionTablesIntegrationTest {

  private static final String PEER_EMAIL = "opencrq@xtm.openaev.filigran.io";

  /** One entry per privilege service that writes the membership, then saves the account. */
  enum Family {
    AGENT(ReservedServiceAccount.AGENT.access()),
    CATALOG_CONNECTOR(ReservedServiceAccount.CATALOG_CONNECTOR.access()),
    XTM_PEER(ReservedServiceAccount.XTM_PEER.access());

    final WellKnownAccess access;

    Family(WellKnownAccess access) {
      this.access = access;
    }
  }

  @Autowired private AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService;
  @Autowired private CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;
  @Autowired private XtmPeerPrivilegeService xtmPeerPrivilegeService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private GroupRepository groupRepository;
  @Autowired private JdbcTemplate jdbc;

  private String tenantA;
  private String tenantB;
  private final List<String> createdGroupIds = new ArrayList<>();
  private boolean peerGroupExistedBefore;
  private boolean peerRoleExistedBefore;

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenant("service-account-membership-a").getId();
    tenantB = tenantHelper.createTenant("service-account-membership-b").getId();
    assertThat(accountIdOf(PEER_EMAIL)).as("no peer account before the test").isEmpty();
    peerGroupExistedBefore = exists("groups", "group_id", wellKnownGroupId(Family.XTM_PEER));
    peerRoleExistedBefore = exists("roles", "role_id", wellKnownRoleId(Family.XTM_PEER));
  }

  @AfterEach
  void cleanup() {
    List<String> emails =
        List.of(
            ReservedServiceAccount.AGENT.emailPattern().formatted(tenantA),
            CatalogConnectorPrivilegeService.accountEmail(tenantA),
            PEER_EMAIL);
    for (String email : emails) {
      for (String userId : accountIdOf(email)) {
        jdbc.update("DELETE FROM users_groups WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM tokens WHERE token_user = ?", userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
      }
    }
    if (!peerGroupExistedBefore) {
      createdGroupIds.add(wellKnownGroupId(Family.XTM_PEER));
    }
    for (String groupId : createdGroupIds) {
      jdbc.update("DELETE FROM users_groups WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    if (!peerRoleExistedBefore) {
      String roleId = wellKnownRoleId(Family.XTM_PEER);
      jdbc.update("DELETE FROM groups_roles WHERE role_id = ?", roleId);
      jdbc.update("DELETE FROM roles_capabilities WHERE role_id = ?", roleId);
      jdbc.update("DELETE FROM roles WHERE role_id = ?", roleId);
    }
    jdbc.update("DELETE FROM groups WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  // -- FIXTURE --

  /** The tenant the account of the family lives in: the peer account is in the default tenant. */
  private String tenantOf(Family family) {
    return family == Family.XTM_PEER ? DEFAULT_TENANT_UUID : tenantA;
  }

  private String emailOf(Family family) {
    return switch (family) {
      case AGENT -> ReservedServiceAccount.AGENT.emailPattern().formatted(tenantA);
      case CATALOG_CONNECTOR -> CatalogConnectorPrivilegeService.accountEmail(tenantA);
      case XTM_PEER -> PEER_EMAIL;
    };
  }

  private String wellKnownGroupId(Family family) {
    return getUUIDFromName(family.access.groupId(), tenantOf(family));
  }

  private String wellKnownRoleId(Family family) {
    return getUUIDFromName(family.access.roleId(), tenantOf(family));
  }

  /** Runs the privilege service of the family, the way its production caller does. */
  private void provision(Family family) {
    TxCtx scope = TxCtx.forTenant(tenantOf(family));
    switch (family) {
      case AGENT -> agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(scope, tenantA);
      case CATALOG_CONNECTOR -> catalogConnectorPrivilegeService.getOrCreateToken(scope, tenantA);
      case XTM_PEER ->
          xtmPeerPrivilegeService.issueToken(scope, "opencrq", PEER_EMAIL, "OpenCRQ peer");
    }
  }

  /** An account with the email of the family, no token, holding the given groups. */
  private String accountWithoutTokenIn(Family family, String... groupIds) {
    String userId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        userId,
        emailOf(family));
    tenantHelper.attachUserToTenant(userId, tenantOf(family));
    for (String groupId : groupIds) {
      jdbc.update("INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", userId, groupId);
    }
    return userId;
  }

  /** A group of {@code tenantId}, or a platform group when it is null. */
  private String group(String tenantId) {
    Group saved =
        tenantTx.executeAsPlatformWrite(
            TxCtx.forTenant(tenantId == null ? tenantA : tenantId),
            () -> {
              Group group = new Group();
              group.setName("service-account-membership-" + UUID.randomUUID());
              group.setTenant(tenantId == null ? null : new Tenant(tenantId));
              return groupRepository.save(group);
            });
    createdGroupIds.add(saved.getId());
    return saved.getId();
  }

  private List<String> accountIdOf(String email) {
    return jdbc.queryForList("SELECT user_id FROM users WHERE user_email = ?", String.class, email);
  }

  private List<String> groupIdsOf(String userId) {
    return jdbc.queryForList(
        "SELECT group_id FROM users_groups WHERE user_id = ?", String.class, userId);
  }

  private boolean exists(String table, String idColumn, String id) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = ?", Integer.class, id);
    return count != null && count > 0;
  }

  // -- TESTS --

  @Nested
  @DisplayName("an account the service creates")
  class NewAccount {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Family.class)
    @DisplayName("given no account yet, should leave the new account in its well-known group")
    void given_noAccountYet_should_leaveTheNewAccountInItsWellKnownGroup(Family family) {
      // Arrange
      assertThat(accountIdOf(emailOf(family))).isEmpty();

      // Act
      provision(family);

      // Assert
      List<String> accountIds = accountIdOf(emailOf(family));
      assertThat(accountIds).hasSize(1);
      assertThat(groupIdsOf(accountIds.getFirst())).containsExactly(wellKnownGroupId(family));
    }
  }

  @Nested
  @DisplayName("an account the service reuses")
  class ReusedAccount {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Family.class)
    @DisplayName(
        "given an account holding other groups, should leave it in its well-known group and the"
            + " groups out of scope")
    void given_accountHoldingOtherGroups_should_leaveItInItsGroupAndTheGroupsOutOfScope(
        Family family) {
      // Arrange: a group of the account's tenant and a platform group, both in the scope the
      // service runs in, and a group of another tenant, outside it
      String groupOfAnotherTenant = group(tenantB);
      String userId =
          accountWithoutTokenIn(family, group(tenantOf(family)), group(null), groupOfAnotherTenant);

      // Act
      provision(family);

      // Assert
      assertThat(accountIdOf(emailOf(family))).containsExactly(userId);
      assertThat(groupIdsOf(userId))
          .containsExactlyInAnyOrder(wellKnownGroupId(family), groupOfAnotherTenant);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Family.class)
    @DisplayName(
        "given an account already in its well-known group, should keep it there and drop the"
            + " other group of its scope")
    void given_accountAlreadyInItsGroup_should_keepItThereAndDropTheOtherGroupOfItsScope(
        Family family) {
      // Arrange: a first provisioning creates the group and the account, then the account loses
      // its token and gains another group of its scope, so the next provisioning reuses it
      provision(family);
      String userId = accountIdOf(emailOf(family)).getFirst();
      jdbc.update("DELETE FROM tokens WHERE token_user = ?", userId);
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          userId,
          group(tenantOf(family)));

      // Act
      provision(family);

      // Assert
      assertThat(groupIdsOf(userId)).containsExactly(wellKnownGroupId(family));
    }
  }
}
