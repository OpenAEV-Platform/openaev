package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.processor.datapack.PresetTenantData;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.service.account.AgentServiceAccountPrivilegeService;
import io.openaev.service.account.ReservedServiceAccount;
import io.openaev.service.platform.groups.PlatformGroupService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A user-side {@code users_groups} writer that touches only the tenant it runs in must leave every
 * other tenant's membership row of the same user untouched. Each nested class pins one family of
 * writer rewired onto {@link UserGroupMembershipService}: auto-assign, SSO group mapping, a
 * privilege service, and a group delete (the sharpest site - a single group delete can otherwise
 * drop every other tenant's membership of each of its members at once).
 *
 * <p>NOT {@code @Transactional}: a group delete and a scoped writer each open their own transaction
 * through {@link TenantScopedTransaction}, so the row set is read back by raw JDBC, outside any
 * Hibernate session, the way the real regression (a silently recreated row set) would otherwise
 * hide behind a stale persistence context.
 */
@DisplayName("user-group membership write safety")
class UserGroupMembershipWriteSafetyTest extends IntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserGroupMembershipService userGroupMembershipService;
  @Autowired private UserService userService;
  @Autowired private UserMappingService userMappingService;
  @Autowired private TenantGroupService tenantGroupService;
  @Autowired private PlatformGroupService platformGroupService;
  @Autowired private AdminPrivilegeService adminPrivilegeService;
  @Autowired private AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService;
  @Autowired private GroupRepository groupRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbc;

  private String tenantA;
  private String tenantB;
  private User user;
  private Group groupB;

  @BeforeEach
  void setUp() throws Exception {
    userService.createAdminSession();
    try {
      tenantA = tenantHelper.createTenant("membership-a-" + UUID.randomUUID()).getId();
      tenantB = tenantHelper.createTenant("membership-b-" + UUID.randomUUID()).getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
    user =
        userRepository.save(
            UserFixture.getUser(
                "membership",
                "user",
                "membership-user-" + UUID.randomUUID() + "@unittests.invalid",
                false));
    tenantHelper.attachUserToTenant(user.getId(), tenantA);
    tenantHelper.attachUserToTenant(user.getId(), tenantB);
    groupB =
        tenantTx.executeAsPlatformWrite(
            TxCtx.forTenant(tenantB), () -> groupRepository.save(plainGroup(tenantB)));
    jdbc.update(
        "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", user.getId(), groupB.getId());
  }

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", user.getId());
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", user.getId());
    jdbc.update("DELETE FROM users WHERE user_id = ?", user.getId());
    // Groups deleted by the test itself (TenantGroupService.delete, PlatformGroupService.delete)
    // simply match zero rows here; every other test group is tenant-scoped to A or B.
    jdbc.update("DELETE FROM groups WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  private List<String> groupIdsOf(String userId) {
    return jdbc.queryForList(
        "SELECT group_id FROM users_groups WHERE user_id = ? ORDER BY group_id",
        String.class,
        userId);
  }

  private Group plainGroup(String tenantId) {
    Group group = new Group();
    group.setName("group-" + UUID.randomUUID());
    group.setTenant(new Tenant(tenantId));
    return group;
  }

  @Nested
  @DisplayName("the shared service itself")
  class SharedService {

    @Test
    @DisplayName("given_aUserNotYetAMember_should_addOneRowAndLeaveTheOtherTenantsRowUntouched")
    void given_aUserNotYetAMember_should_addOneRowAndLeaveTheOtherTenantsRowUntouched() {
      // Arrange
      Group groupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA), () -> groupRepository.save(plainGroup(tenantA)));

      // Act
      boolean changed =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () -> userGroupMembershipService.addMembership(user.getId(), groupA.getId()));

      // Assert
      assertThat(changed).isTrue();
      assertThat(groupIdsOf(user.getId()))
          .containsExactlyInAnyOrder(groupA.getId(), groupB.getId());
    }

    @Test
    @DisplayName("given_aMembership_should_removeOnlyItsOwnRow")
    void given_aMembership_should_removeOnlyItsOwnRow() {
      // Arrange
      Group groupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA), () -> groupRepository.save(plainGroup(tenantA)));
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          user.getId(),
          groupA.getId());

      // Act
      boolean changed =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () -> userGroupMembershipService.removeMembership(user.getId(), groupA.getId()));

      // Assert
      assertThat(changed).isTrue();
      assertThat(groupIdsOf(user.getId())).containsExactly(groupB.getId());
    }
  }

  @Nested
  @DisplayName("family: auto-assign")
  class AutoAssign {

    @Test
    @DisplayName("given_aDefaultAssignGroupOfOneTenant_should_grantItAndKeepTheOtherTenantsGroup")
    void given_aDefaultAssignGroupOfOneTenant_should_grantItAndKeepTheOtherTenantsGroup() {
      // Arrange: a default-assignable group of tenant A only
      Group defaultGroupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA),
              () -> {
                Group group = plainGroup(tenantA);
                group.setDefaultUserAssignation(true);
                return groupRepository.save(group);
              });

      // Act: the real production writer, scoped to tenant A only
      tenantTx.execute(
          TxCtx.forTenant(tenantA),
          () -> userService.assignAutoAssignGroups(user.getId(), List.of(tenantA)));

      // Assert: tenant A's group is granted, tenant B's row survives
      assertThat(groupIdsOf(user.getId()))
          .containsExactlyInAnyOrder(defaultGroupA.getId(), groupB.getId());
    }
  }

  @Nested
  @DisplayName("family: SSO group mapping")
  class SsoMapping {

    @Test
    @DisplayName("given_anSsoAdminMappingForOneTenant_should_grantTheAdminGroupAndKeepTheOther")
    void given_anSsoAdminMappingForOneTenant_should_grantTheAdminGroupAndKeepTheOther() {
      // Arrange: tenant A's Admin group, the name UserMappingService.mapAdminGroup looks up
      Group adminGroupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA),
              () -> tenantGroupService.createGroup(PresetTenantData.ADMIN, tenantA));

      // Act: the real production writer, scoped to tenant A only
      tenantTx.execute(
          TxCtx.forTenant(tenantA), () -> userMappingService.mapAdminGroup(user, tenantA, true));

      // Assert: the SSO-admin group is granted, tenant B's row survives
      assertThat(groupIdsOf(user.getId()))
          .containsExactlyInAnyOrder(adminGroupA.getId(), groupB.getId());
    }

    @Test
    @DisplayName("given_anSsoAdminDemotionForOneTenant_should_revokeOnlyItAndKeepTheOther")
    void given_anSsoAdminDemotionForOneTenant_should_revokeOnlyItAndKeepTheOther() {
      // Arrange: the user already holds tenant A's Admin group
      Group adminGroupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA),
              () -> tenantGroupService.createGroup(PresetTenantData.ADMIN, tenantA));
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          user.getId(),
          adminGroupA.getId());

      // Act: the real production writer, scoped to tenant A only, isAdmin = false
      tenantTx.execute(
          TxCtx.forTenant(tenantA), () -> userMappingService.mapAdminGroup(user, tenantA, false));

      // Assert: tenant A's admin group is revoked, tenant B's row survives
      assertThat(groupIdsOf(user.getId())).containsExactly(groupB.getId());
    }

    @Test
    @DisplayName(
        "given_aTokenGroupNoLongerPresent_should_revokeOnlyTheMappedGroupAndKeepTheOtherTenant")
    void given_aTokenGroupNoLongerPresent_should_revokeOnlyTheMappedGroupAndKeepTheOtherTenant() {
      // Arrange: a mapped group of tenant A, held by the user, the idp token no longer carries it
      Group mappedGroupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA),
              () -> tenantGroupService.createGroup("idp-mapped", tenantA));
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          user.getId(),
          mappedGroupA.getId());
      String mappingJson =
          """
          [{"idpGroup":"idp-group-a","userGroup":"idp-mapped","autoCreate":false,"tenantId":"%s"}]
          """
              .formatted(tenantA);

      // Act: the real production writer, scoped to tenant A only, the token carries no group.
      // Reloaded fresh: the SSO login always reads the user inside its own scope, never the
      // detached instance a previous, already-closed transaction happened to load.
      tenantTx.execute(
          TxCtx.forTenant(tenantA),
          () -> {
            User freshUser = userRepository.findById(user.getId()).orElseThrow();
            userMappingService.mapCurrentUserWithGroup(mappingJson, freshUser, List.of());
          });

      // Assert: the mapped group of tenant A is revoked, tenant B's row survives
      assertThat(groupIdsOf(user.getId())).containsExactly(groupB.getId());
    }
  }

  @Nested
  @DisplayName("family: tenant detach")
  class TenantDetach {

    @Test
    @DisplayName("given_aTenantDetached_should_revokeOnlyThatTenantsGroupsAndKeepTheOther")
    void given_aTenantDetached_should_revokeOnlyThatTenantsGroupsAndKeepTheOther() {
      // Arrange: the user holds a group of tenant A too, which the detach must revoke
      Group groupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA), () -> groupRepository.save(plainGroup(tenantA)));
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          user.getId(),
          groupA.getId());

      // Act: the real production writer, leaving tenant A
      tenantTx.execute(
          TxCtx.forTenants(List.of(tenantA, tenantB)),
          () -> {
            userService.revokeTenantGroups(user.getId(), List.of(tenantA));
            return null;
          });

      // Assert: tenant A's group is revoked, tenant B's row survives
      assertThat(groupIdsOf(user.getId())).containsExactly(groupB.getId());
    }
  }

  @Nested
  @DisplayName("family: privilege services")
  class PrivilegeServices {

    @Test
    @DisplayName("given_theAdminGroupOfOneTenantIsEnsured_should_enrollAndKeepTheOtherTenantsGroup")
    void given_theAdminGroupOfOneTenantIsEnsured_should_enrollAndKeepTheOtherTenantsGroup() {
      // Act: the real production writer, self-scoped to tenant A through its own TxCtx parameter
      Group adminGroupA =
          adminPrivilegeService.ensureAdminGroup(TxCtx.forTenant(tenantA), tenantA, user);

      // Assert: tenant A's admin group is granted, tenant B's row survives
      assertThat(groupIdsOf(user.getId()))
          .containsExactlyInAnyOrder(adminGroupA.getId(), groupB.getId());
    }

    @Test
    @DisplayName(
        "given_aServiceAccountReplacingItsStaleGroup_should_notDropABystandersRowOfTheSameGroup")
    void given_aServiceAccountReplacingItsStaleGroup_should_notDropABystandersRowOfTheSameGroup() {
      // Arrange: a stale group of tenant A, held by both the service account and a bystander;
      // makeOnlyMembershipOf is production-correct to drop the service account's own row of it
      // (its membership must be exactly its well-known group), but a whole-bag Group.users
      // rewrite would drop the BYSTANDER's row of the same group too.
      Group staleGroupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA), () -> groupRepository.save(plainGroup(tenantA)));
      String serviceEmail = ReservedServiceAccount.AGENT.emailPattern().formatted(tenantA);
      User serviceUser =
          userRepository.save(UserFixture.getUser("service", "account", serviceEmail, false));
      User bystander =
          userRepository.save(
              UserFixture.getUser(
                  "bystander",
                  "user",
                  "bystander-" + UUID.randomUUID() + "@unittests.invalid",
                  false));
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          serviceUser.getId(),
          staleGroupA.getId());
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          bystander.getId(),
          staleGroupA.getId());

      try {
        // Act: the real production writer; the account has no token yet, so
        // applyUserServiceAttributes -> makeOnlyMembershipOf runs
        agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(
            TxCtx.forTenant(tenantA), tenantA);

        // Assert: the service account's own row of the stale group is gone, the bystander's
        // survives
        assertThat(groupIdsOf(bystander.getId())).containsExactly(staleGroupA.getId());
        assertThat(groupIdsOf(serviceUser.getId())).doesNotContain(staleGroupA.getId());
      } finally {
        jdbc.update(
            "DELETE FROM users_groups WHERE user_id IN (?, ?)",
            serviceUser.getId(),
            bystander.getId());
        jdbc.update(
            "DELETE FROM users WHERE user_id IN (?, ?)", serviceUser.getId(), bystander.getId());
      }
    }
  }

  @Nested
  @DisplayName("family: group delete")
  class GroupDelete {

    @Test
    @DisplayName("given_aTenantGroupDeleted_should_keepTheMembersOtherTenantGroup")
    void given_aTenantGroupDeleted_should_keepTheMembersOtherTenantGroup() {
      // Arrange: a group of tenant A the user also belongs to
      Group groupA =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA), () -> groupRepository.save(plainGroup(tenantA)));
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          user.getId(),
          groupA.getId());

      // Act: delete tenant A's group
      tenantTx.execute(
          TxCtx.forTenant(tenantA), () -> tenantGroupService.delete(tenantA, groupA.getId()));

      // Assert: tenant A's row is gone, tenant B's row survives
      assertThat(groupIdsOf(user.getId())).containsExactly(groupB.getId());
    }

    @Test
    @DisplayName("given_aPlatformGroupDeleted_should_keepTheMembersTenantGroup")
    void given_aPlatformGroupDeleted_should_keepTheMembersTenantGroup() {
      // Arrange: a platform group the user also belongs to
      Group platformGroup =
          tenantTx.executeAsPlatformWrite(
              TxCtx.forTenant(tenantA),
              () -> {
                Group group = new Group();
                group.setName("platform-group-" + UUID.randomUUID());
                return groupRepository.save(group);
              });
      jdbc.update(
          "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
          user.getId(),
          platformGroup.getId());

      // Act: delete the platform group
      tenantTx.executeAsPlatformWrite(
          TxCtx.forTenant(tenantA),
          () -> {
            platformGroupService.delete(platformGroup.getId());
            return null;
          });

      // Assert: the platform group's row is gone, tenant B's row survives
      assertThat(groupIdsOf(user.getId())).containsExactly(groupB.getId());
    }
  }
}
