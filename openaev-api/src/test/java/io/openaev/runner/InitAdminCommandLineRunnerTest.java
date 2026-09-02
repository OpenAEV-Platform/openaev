package io.openaev.runner;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.database.model.Token.ADMIN_TOKEN_UUID;
import static io.openaev.database.model.User.ADMIN_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InitAdminCommandLineRunnerTest extends IntegrationTest {

  @Autowired private UserRepository userRepository;

  @Autowired private TokenRepository tokenRepository;

  @Autowired private AdminPrivilegeService adminPrivilegeService;

  @DisplayName("Test if admin user is created")
  @Test
  void adminUserExistTest() {
    Optional<User> adminUser = this.userRepository.findById(ADMIN_UUID);
    assertThat(adminUser.isPresent()).isTrue();
  }

  @DisplayName("Test if admin token is created")
  @Test
  void adminTokenExistTest() {
    Optional<Token> adminToken = this.tokenRepository.findByIdIncludingDeleted(ADMIN_TOKEN_UUID);
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
}
