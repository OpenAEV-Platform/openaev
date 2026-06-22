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
    Optional<Token> adminToken = this.tokenRepository.findById(ADMIN_TOKEN_UUID);
    assertThat(adminToken.isPresent()).isTrue();
  }

  @DisplayName("Admin user is enrolled in the default-tenant admin group at bootstrap")
  @Test
  void adminUserBelongsToAdminGroupTest() {
    User adminUser = this.userRepository.findById(ADMIN_UUID).orElseThrow();

    List<Group> adminGroups =
        adminUser.getUnscopedGroups().stream()
            .filter(group -> AdminPrivilegeService.ADMIN_GROUP_NAME.equals(group.getName()))
            .toList();

    assertThat(adminGroups).hasSize(1);
    Group adminGroup = adminGroups.getFirst();
    assertThat(adminGroup.getTenant()).isNotNull();
    assertThat(adminGroup.getTenant().getId()).isEqualTo(DEFAULT_TENANT_UUID);
    assertThat(adminGroup.getRoles())
        .anyMatch(role -> role.getCapabilities().contains(Capability.BYPASS));
  }

  @DisplayName("Ensuring the admin group twice is idempotent")
  @Test
  void ensureAdminGroupIsIdempotentTest() {
    this.adminPrivilegeService.ensureAdminGroup(DEFAULT_TENANT_UUID, ADMIN_UUID);
    this.adminPrivilegeService.ensureAdminGroup(DEFAULT_TENANT_UUID, ADMIN_UUID);

    User adminUser = this.userRepository.findById(ADMIN_UUID).orElseThrow();
    long adminGroupCount =
        adminUser.getUnscopedGroups().stream()
            .filter(group -> AdminPrivilegeService.ADMIN_GROUP_NAME.equals(group.getName()))
            .count();

    assertThat(adminGroupCount).isEqualTo(1);
  }
}
