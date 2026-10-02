package io.openaev.runner;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.database.model.Token.ADMIN_TOKEN_UUID;
import static io.openaev.database.model.User.ADMIN_UUID;
import static io.openaev.database.specification.TokenSpecification.fromUser;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.service.UserService;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InitAdminCommandLineRunnerTest extends IntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private TokenRepository tokenRepository;

  @Autowired private AdminPrivilegeService adminPrivilegeService;

  @Autowired private InitAdminCommandLineRunner initAdminCommandLineRunner;

  @Autowired private UserService userService;

  @PersistenceContext private EntityManager entityManager;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String configuredAdminToken;

  @DisplayName("Test if admin user is created")
  @Test
  void adminUserExistTest() {
    Optional<User> adminUser = this.userRepository.findById(ADMIN_UUID);
    assertThat(adminUser.isPresent()).isTrue();
  }

  @DisplayName("Test if admin token is created")
  @Test
  void adminTokenExistTest() {
    Optional<Token> adminToken = this.tokenRepository.findById(ADMIN_TOKEN_UUID);
    assertThat(adminToken.isPresent()).isTrue();
  }

  @DisplayName("Admin user is enrolled in the default-tenant admin group at bootstrap")
  @Test
  void given_platform_bootstrap_should_enroll_admin_in_default_tenant_admin_group() {
    // Arrange
    String adminGroupId =
        AbstractPrivilegeService.getUUIDFromName(
            AdminPrivilegeService.ADMIN_GROUP_ID, DEFAULT_TENANT_UUID);

    // Act
    User adminUser = this.userRepository.findById(ADMIN_UUID).orElseThrow();
    List<Group> adminGroups =
        adminUser.getUnscopedGroups().stream()
            .filter(group -> adminGroupId.equals(group.getId()))
            .toList();

    // Assert
    assertThat(adminGroups).hasSize(1);
    Group adminGroup = adminGroups.getFirst();
    assertThat(adminGroup.getTenant()).isNotNull();
    assertThat(adminGroup.getTenant().getId()).isEqualTo(DEFAULT_TENANT_UUID);
    assertThat(adminGroup.getRoles())
        .anyMatch(role -> role.getCapabilities().contains(Capability.BYPASS));
  }

  @DisplayName("Ensuring the admin group twice keeps a single membership")
  @Test
  void given_admin_group_ensured_twice_should_keep_single_membership() {
    // Arrange
    String adminGroupId =
        AbstractPrivilegeService.getUUIDFromName(
            AdminPrivilegeService.ADMIN_GROUP_ID, DEFAULT_TENANT_UUID);

    // Act
    this.adminPrivilegeService.ensureAdminGroup(
        DEFAULT_TENANT_UUID, this.userRepository.findById(ADMIN_UUID).orElseThrow());
    this.adminPrivilegeService.ensureAdminGroup(
        DEFAULT_TENANT_UUID, this.userRepository.findById(ADMIN_UUID).orElseThrow());

    // Assert
    User adminUser = this.userRepository.findById(ADMIN_UUID).orElseThrow();
    long adminGroupCount =
        adminUser.getUnscopedGroups().stream()
            .filter(group -> adminGroupId.equals(group.getId()))
            .count();

    assertThat(adminGroupCount).isEqualTo(1);
  }

  @DisplayName("Admin user is enrolled in the platform admin group at bootstrap")
  @Test
  void given_platform_bootstrap_should_enroll_admin_in_platform_admin_group() {
    // Act
    User adminUser = this.userRepository.findById(ADMIN_UUID).orElseThrow();
    List<Group> platformAdminGroups =
        adminUser.getUnscopedGroups().stream()
            .filter(group -> AdminPrivilegeService.PLATFORM_ADMIN_GROUP_ID.equals(group.getId()))
            .toList();

    // Assert
    assertThat(platformAdminGroups).hasSize(1);
    Group platformAdminGroup = platformAdminGroups.getFirst();
    assertThat(platformAdminGroup.getTenant()).isNull();
    assertThat(platformAdminGroup.getRoles())
        .anyMatch(role -> role.getCapabilities().contains(Capability.BYPASS));
    assertThat(adminUser.hasPlatformBypass()).isTrue();
  }

  @DisplayName("A renewed admin token is reused at bootstrap instead of minting a second one")
  @Test
  @Transactional
  void given_renewedAdminToken_should_reuseItInsteadOfMintingASecondOne() throws Exception {
    // Arrange — renewUserToken() acts on behalf of the authenticated user, so the admin must own
    // the security context. Cleared in the finally block: the other tests of this class run
    // unauthenticated and the context is thread-bound.
    Token renewedAdminToken;
    this.userService.createAdminSession();
    try {
      renewedAdminToken = this.userService.renewUserToken(ADMIN_TOKEN_UUID);
    } finally {
      SecurityContextHolder.clearContext();
    }
    this.entityManager.flush();

    // The rotation is a hard delete followed by an insert, so the well-known bootstrap id is gone.
    assertThat(renewedAdminToken.getId()).isNotEqualTo(ADMIN_TOKEN_UUID);

    // Act
    this.initAdminCommandLineRunner.run();
    this.entityManager.flush();
    this.entityManager.clear();

    // Assert
    List<Token> adminTokens = this.tokenRepository.findAll(fromUser(ADMIN_UUID));
    assertThat(adminTokens)
        .as("the bootstrap must not mint an extra admin token on every restart")
        .hasSize(1);
    assertThat(adminTokens.getFirst().getId()).isEqualTo(renewedAdminToken.getId());
    assertThat(adminTokens.getFirst().getValue()).isEqualTo(this.configuredAdminToken);
  }
}
