package io.openaev.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
class SecurityServiceTest extends IntegrationTest {

  private static final String REGISTRATION_ID = "oidc";
  private static final String SSO_EMAIL = "sso-user@integration.test";
  private static final String SSO_TENANT_ID = "sso-test-tenant-id";

  @Autowired private SecurityService securityService;
  @Autowired private UserRepository userRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired protected EntityManager entityManager;

  @BeforeEach
  void setup() {
    userComposer.reset();
    tenantComposer.reset();
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
      entityManager.flush();
      entityManager.clear();

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
      // -- ARRANGE --
      Tenant tenant = TenantFixture.getTenant();
      tenant.setId(SSO_TENANT_ID);
      tenantComposer.forTenant(tenant).persist();
      entityManager.flush();
      entityManager.clear();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result.getTenants()).hasSize(1);
      assertThat(result.getTenants().getFirst().getId()).isEqualTo(SSO_TENANT_ID);
    }

    @Test
    void given_existingUserWithTenantAlreadyAttached_should_notDuplicate() {
      // -- ARRANGE --
      Tenant tenant = TenantFixture.getTenant();
      tenant.setId(SSO_TENANT_ID);
      tenantComposer.forTenant(tenant).persist();
      User user = UserFixture.getUser("John", "Doe", SSO_EMAIL);
      user.getTenants().add(tenant);
      userComposer.forUser(user).persist();
      entityManager.flush();
      entityManager.clear();

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result.getTenants()).hasSize(1);
    }

    @Test
    void given_tenantNotFoundInDb_should_notError() {
      // -- ARRANGE --
      // tenant_id is configured in test application.properties but tenant is not persisted

      // -- ACT --
      User result =
          securityService.userManagement(
              SSO_EMAIL, REGISTRATION_ID, List.of(), List.of(), "John", "Doe");

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.getTenants()).isEmpty();
    }
  }
}
