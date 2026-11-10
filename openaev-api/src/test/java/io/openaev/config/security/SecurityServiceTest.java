package io.openaev.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "openaev.provider.oidc.tenant_id=2cffad3a-0001-4078-b0e2-ef74274022c3", // Default tenant ID
      "openaev.provider.oidc-admin.roles_admin=sso-admin"
    })
class SecurityServiceTest extends IntegrationTest {

  private static final String REGISTRATION_ID = "oidc";
  private static final String SSO_EMAIL = "sso-user@integration.test";
  private static final String ADMIN_REGISTRATION_ID = "oidc-admin";
  private static final String ADMIN_ROLE = "sso-admin";

  @Autowired private SecurityService securityService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;

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
      assertThat(groupIds(result)).contains(adminGroup.getId());
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
      assertThat(groupIds(result)).doesNotContain(adminGroup.getId());
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

    private List<String> groupIds(User user) {
      return user.getUnscopedGroups().stream().map(Group::getId).toList();
    }
  }
}
