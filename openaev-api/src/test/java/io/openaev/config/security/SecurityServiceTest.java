package io.openaev.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.fixtures.UserFixture;
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
    properties =
        "openaev.provider.oidc.tenant_id=2cffad3a-0001-4078-b0e2-ef74274022c3") // Default tenant ID
class SecurityServiceTest extends IntegrationTest {

  private static final String REGISTRATION_ID = "oidc";
  private static final String SSO_EMAIL = "sso-user@integration.test";

  @Autowired private SecurityService securityService;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserComposer userComposer;

  @BeforeEach
  void setup() {
    userComposer.reset();
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
  }
}
