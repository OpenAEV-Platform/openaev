package io.openaev.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.processor.datapack.PresetTenantData;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
@TestPropertySource(
    properties = {
      "openaev.provider.oidc.tenant_id=2cffad3a-0001-4078-b0e2-ef74274022c3", // Default tenant ID
      "openaev.provider.oidc-admin.roles_admin=sso-admin",
      "openaev.provider.oidc.groups_management=[{\"idpGroup\":\"idp-mapped-group\",\"userGroup\":"
          + "\"Mapped Group\",\"autoCreate\":true,\"tenantId\":"
          + "\"2cffad3a-0001-4078-b0e2-ef74274022c3\"}]"
    })
class SecurityServiceTest extends ProductionTablesIntegrationTest {

  private static final String REGISTRATION_ID = "oidc";
  private static final String SSO_EMAIL = "sso-user@integration.test";
  private static final String ADMIN_REGISTRATION_ID = "oidc-admin";
  private static final String ADMIN_ROLE = "sso-admin";
  private static final String MAPPED_IDP_GROUP = "idp-mapped-group";
  private static final String MAPPED_GROUP_NAME = "Mapped Group";

  @Autowired private SecurityService securityService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private ApplicationEvents applicationEvents;

  @BeforeEach
  void setup() {
    userComposer.reset();
    tenantGroupComposer.reset();
  }

  @Nested
  class UserCreation {

    @Test
    void given_unknownEmail_should_createUser() {
      // -- ARRANGE --

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.getEmail()).isEqualTo(SSO_EMAIL);
      assertThat(result.getFirstname()).isEqualTo("John");
      assertThat(result.getLastname()).isEqualTo("Doe");

      Optional<User> persisted = userRepository.findByEmailIgnoreCase(SSO_EMAIL);
      assertThat(persisted).isPresent();
    }
  }

  @Nested
  class UserUpdate {

    @Test
    void given_existingUser_should_updateName() {
      // -- ARRANGE --
      User user = UserFixture.getUser("Old", "Name", SSO_EMAIL);
      userComposer.forUser(user).persist();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "New", "Name");

      // -- ASSERT --
      assertThat(result.getFirstname()).isEqualTo("New");
      assertThat(result.getLastname()).isEqualTo("Name");
    }

    @Test
    void given_emptyEmail_should_returnNull() {
      // -- ARRANGE --

      // -- ACT --
      User result =
          securityService.userManagement("", REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result).isNull();
    }
  }

  @Nested
  class AttachTenant {

    @Test
    void given_newUserWithTenantConfigured_should_attachTenant() {
      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result.getTenants()).hasSize(1);
      assertThat(result.getTenants().getFirst().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    void given_existingUserWithTenantAlreadyAttached_should_notDuplicate() {
      // -- ARRANGE --
      User user = UserFixture.getUser("John", "Doe", SSO_EMAIL);
      user.getTenants().add(tenantRepository.getReferenceById(Tenant.DEFAULT_TENANT_UUID));
      userComposer.forUser(user).persist();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result.getTenants()).hasSize(1);
    }

    @Test
    void given_noTenantIdConfigured_should_fallbackToDefaultTenant() {
      // -- ARRANGE --
      // "unknown_provider" has no openaev.provider.unknown_provider.tenant_id configured

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, "unknown_provider", List.of(), List.of(), "Jane", "Doe");

      // -- ASSERT --
      assertThat(result.getTenants()).hasSize(1);
      assertThat(result.getTenants().getFirst().getId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    void given_newUser_should_notKeepCachedTenantsWithoutTheAttachedTenant() {
      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");
      // Writes the membership as the commit would, so an uncached read sees it.
      entityManager.flush();

      // -- ASSERT --
      // Requests resolve the user's tenants through this cache: a stale list denies them all.
      assertThat(tenantMembershipCacheManager.findTenantIdsByUserId(result.getId()))
          .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    }
  }

  @Nested
  class AdminRole {

    @Test
    void given_newUserWithAdminRole_should_joinAdminGroupWithoutUserAdmin() {
      // -- ARRANGE --
      Group adminGroup = adminGroup();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, ADMIN_REGISTRATION_ID, List.of(ADMIN_ROLE), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result.isAdmin()).isFalse();
      assertThat(memberIds(adminGroup)).contains(result.getId());
    }

    @Test
    void given_userAdminLosingAdminRole_should_leaveAdminGroupAndUserAdmin() {
      // -- ARRANGE --
      Group adminGroup = adminGroup();
      User user = UserFixture.getUser("John", "Doe", SSO_EMAIL);
      user.setAdmin(true);
      user.getUnscopedGroups().add(adminGroup);
      userComposer.forUser(user).persist();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, ADMIN_REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result.isAdmin()).isFalse();
      assertThat(memberIds(adminGroup)).doesNotContain(result.getId());
    }

    @Test
    void given_firstAdminWithoutAdminRole_should_keepUserAdmin() {
      // -- ARRANGE --
      User firstAdmin = userRepository.findById(User.ADMIN_UUID).orElseThrow();

      // -- ACT --
      User result =
          securityService.userManagement(
              firstAdmin.getEmail(),
              ADMIN_REGISTRATION_ID,
              List.of(),
              List.of(),
              firstAdmin.getFirstname(),
              firstAdmin.getLastname());

      // -- ASSERT --
      assertThat(result.isAdmin()).isTrue();
    }

    private Group adminGroup() {
      return groupRepository
          .findByNameAndTenantId(PresetTenantData.ADMIN, Tenant.DEFAULT_TENANT_UUID)
          .orElseGet(
              () ->
                  tenantGroupComposer
                      .forGroup(TenantGroupFixture.getGroup(PresetTenantData.ADMIN))
                      .persist()
                      .get());
    }

    // The membership is written one row at a time from the group side, so the group is the one
    // that lists it: the groups held by the user returned are not updated in memory.
    private List<String> memberIds(Group group) {
      entityManager.flush();
      return groupRepository.findUserIdsByGroupId(group.getId());
    }
  }

  /**
   * The group part of the login reaches no ambient v2 scope of its own: {@code @WithoutTenantScope}
   * strips the test's default ambient scope so the transaction carries none at all, the way a
   * production SSO callback does, while fixture setup still runs under the v1 ambient tenant {@code
   * DefaultTenantExtension} sets regardless (every {@code groups} read it does is through the
   * v1-unfiltered {@code Group} entity, not the inspector). Before the fix, every one of these
   * reads a {@code groups} row of an active table with no scope and fails closed.
   */
  @Nested
  @WithoutTenantScope
  @DisplayName("group scope under SSO login (no ambient v2 scope)")
  class GroupScopeUnderSso {

    @Test
    void given_noAmbientScope_should_grantTheMappedTenantGroup() {
      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(MAPPED_IDP_GROUP), "John", "Doe");

      // -- ASSERT --
      entityManager.flush();
      Group mapped =
          groupRepository
              .findByNameAndTenantId(MAPPED_GROUP_NAME, Tenant.DEFAULT_TENANT_UUID)
              .orElseThrow();
      assertThat(groupRepository.findUserIdsByGroupId(mapped.getId())).contains(result.getId());
    }

    @Test
    void given_noAmbientScope_should_revokeAGroupTheTokenNoLongerCarries() {
      // -- ARRANGE --
      User user = UserFixture.getUser("John", "Doe", SSO_EMAIL);
      Group mapped =
          underDefaultScopeForArrange(
              () -> {
                userComposer.forUser(user).persist();
                Group g =
                    tenantGroupComposer
                        .forGroup(TenantGroupFixture.getGroup(MAPPED_GROUP_NAME))
                        .persist()
                        .get();
                entityManager.flush();
                jdbcAddMembership(user.getId(), g.getId());
                return g;
              });
      // The row above is written outside Hibernate: user and mapped stay managed in this
      // session with their EAGER collections already initialized from before the insert, so a
      // later read of either would return the same stale, pre-insert Java list. Clearing forces
      // userManagement's own lookup to reload fresh, the way a brand new login session would.
      entityManager.clear();
      clearAmbientScope();

      // -- ACT --
      securityService.userManagement(
          SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      entityManager.flush();
      assertThat(groupRepository.findUserIdsByGroupId(mapped.getId())).doesNotContain(user.getId());
    }

    @Test
    void given_noAmbientScope_should_enrollTheSsoAdmin() {
      // -- ARRANGE --
      Group adminGroup =
          underDefaultScopeForArrange(() -> adminGroupOf(Tenant.DEFAULT_TENANT_UUID));
      clearAmbientScope();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, ADMIN_REGISTRATION_ID, List.of(ADMIN_ROLE), List.of(), "John", "Doe");

      // -- ASSERT --
      entityManager.flush();
      assertThat(groupRepository.findUserIdsByGroupId(adminGroup.getId())).contains(result.getId());
    }

    @Test
    void given_noAmbientScope_should_demoteTheSsoAdmin() {
      // -- ARRANGE --
      User user = UserFixture.getUser("John", "Doe", SSO_EMAIL);
      user.setAdmin(true);
      Group adminGroup =
          underDefaultScopeForArrange(
              () -> {
                Group g = adminGroupOf(Tenant.DEFAULT_TENANT_UUID);
                userComposer.forUser(user).persist();
                entityManager.flush();
                jdbcAddMembership(user.getId(), g.getId());
                return g;
              });
      entityManager.clear();
      clearAmbientScope();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, ADMIN_REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      entityManager.flush();
      assertThat(result.isAdmin()).isFalse();
      assertThat(groupRepository.findUserIdsByGroupId(adminGroup.getId()))
          .doesNotContain(user.getId());
    }

    @Test
    void given_noAmbientScope_should_grantTheDefaultAssignGroupOfTheProviderTenant() {
      // -- ARRANGE --
      Group defaultAssignGroup =
          underDefaultScopeForArrange(
              () -> {
                Group g =
                    tenantGroupComposer
                        .forGroup(TenantGroupFixture.getGroup("Auto Assigned"))
                        .persist()
                        .get();
                g.setDefaultUserAssignation(true);
                groupRepository.save(g);
                entityManager.flush();
                return g;
              });
      clearAmbientScope();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      entityManager.flush();
      assertThat(groupRepository.findUserIdsByGroupId(defaultAssignGroup.getId()))
          .contains(result.getId());
    }

    @Test
    @DisplayName("given_anExistingUserInTenantB_should_loginMappedToTenantAWithoutLosingTenantB")
    void given_anExistingUserInTenantB_should_loginMappedToTenantAWithoutLosingTenantB() {
      // -- ARRANGE -- a plain row, not TenantIsolationTestHelper.createTenant: this class carries
      // no @WithMockUser, and that helper's onboarding chain attaches the current (anonymous)
      // principal, which has no row in users. tenants and users_tenants are outside v2 (D8), so
      // no scope widening is needed to attach the user to it.
      Tenant tenantB = tenantRepository.save(TenantFixture.getTenant("sso-end-to-end-tenant-b"));
      User user = UserFixture.getUser("John", "Doe", SSO_EMAIL);
      user.getTenants().add(tenantB);
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();

      // -- ACT -- end to end: the login is mapped to the provider's own tenant (the default one),
      // the scope-widening must find tenant B through the email projection (no ambient scope and
      // no in-memory User to read getTenants() from beforehand), not lose it.
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      entityManager.flush();
      assertThat(result.getTenants().stream().map(Tenant::getId))
          .containsExactlyInAnyOrder(tenantB.getId(), Tenant.DEFAULT_TENANT_UUID);
    }

    private Group adminGroupOf(String tenantId) {
      return groupRepository
          .findByNameAndTenantId(PresetTenantData.ADMIN, tenantId)
          .orElseGet(
              () ->
                  tenantGroupComposer
                      .forGroup(TenantGroupFixture.getGroup(PresetTenantData.ADMIN))
                      .persist()
                      .get());
    }

    private void jdbcAddMembership(String userId, String groupId) {
      entityManager
          .createNativeQuery(
              "INSERT INTO users_groups (user_id, group_id) VALUES (:userId, :groupId)")
          .setParameter("userId", userId)
          .setParameter("groupId", groupId)
          .executeUpdate();
    }

    /**
     * Runs fixture setup under the default tenant's scope: a write of a tenant-scoped {@code
     * groups} row is refused outside the scope of the transaction (the write-attribution guard, not
     * the one this test targets), and {@code @WithoutTenantScope} leaves the transaction with none.
     * Cleared again with {@link #clearAmbientScope} before the production call under test.
     */
    private <T> T underDefaultScopeForArrange(Supplier<T> arrange) {
      DefaultTenantScopeTestListener.setAmbientScope(entityManager, Tenant.DEFAULT_TENANT_UUID);
      try {
        return arrange.get();
      } finally {
        clearAmbientScope();
      }
    }

    private void clearAmbientScope() {
      DefaultTenantScopeTestListener.setAmbientScope(entityManager, "");
    }
  }

  /**
   * The login writes the membership rows in SQL; the event of the user, sent to the stream, must
   * carry the groups those writes left, not the ones loaded before them.
   */
  @Nested
  @DisplayName("event of the user after the login")
  class EventOfTheLogin {

    @Test
    @DisplayName("a login granting a mapped group publishes the user with that group")
    void given_loginGrantingAMappedGroup_should_publishTheUserWithTheMappedGroup() {
      // -- ARRANGE --
      userComposer.forUser(UserFixture.getUser("Old", "Name", SSO_EMAIL)).persist();
      entityManager.flush();
      long eventsBefore = applicationEvents.stream(BaseEvent.class).count();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(MAPPED_IDP_GROUP), "New", "Name");

      // -- ASSERT --
      List<String> inDatabase = groupIdsInTheDatabase(result.getId());
      assertThat(inDatabase).isNotEmpty();
      assertEveryEventCarries(result.getId(), eventsBefore, inDatabase);
    }

    @Test
    @DisplayName("a login removing the admin role publishes the user without the Admin group")
    void given_loginRemovingTheAdminRole_should_publishTheUserWithoutTheAdminGroup() {
      // -- ARRANGE --
      Group adminGroup =
          groupRepository
              .findByNameAndTenantId(PresetTenantData.ADMIN, Tenant.DEFAULT_TENANT_UUID)
              .orElseGet(
                  () ->
                      tenantGroupComposer
                          .forGroup(TenantGroupFixture.getGroup(PresetTenantData.ADMIN))
                          .persist()
                          .get());
      User user = UserFixture.getUser("John", "Doe", SSO_EMAIL);
      user.setAdmin(true);
      user.getUnscopedGroups().add(adminGroup);
      userComposer.forUser(user).persist();
      entityManager.flush();
      long eventsBefore = applicationEvents.stream(BaseEvent.class).count();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, ADMIN_REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      List<String> inDatabase = groupIdsInTheDatabase(result.getId());
      assertThat(inDatabase).doesNotContain(adminGroup.getId());
      assertEveryEventCarries(result.getId(), eventsBefore, inDatabase);
    }

    private List<String> groupIdsInTheDatabase(String userId) {
      entityManager.flush();
      @SuppressWarnings("unchecked")
      List<String> ids =
          entityManager
              .createNativeQuery("SELECT group_id FROM users_groups WHERE user_id = :userId")
              .setParameter("userId", userId)
              .getResultList();
      return ids;
    }

    private void assertEveryEventCarries(String userId, long eventsBefore, List<String> expected) {
      List<BaseEvent> events =
          applicationEvents.stream(BaseEvent.class)
              .skip(eventsBefore)
              .filter(event -> event.getInstance() instanceof User u && userId.equals(u.getId()))
              .toList();
      assertThat(events).as("events of the user").isNotEmpty();
      for (BaseEvent event : events) {
        assertThat(((User) event.getInstance()).getUnscopedGroups())
            .as(event.getType() + " event groups")
            .extracting(Group::getId)
            .containsExactlyInAnyOrderElementsOf(expected);
      }
    }
  }
}
