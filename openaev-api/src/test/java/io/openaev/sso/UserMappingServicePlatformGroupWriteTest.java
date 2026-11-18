package io.openaev.sso;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserMappingService;
import io.openaev.utils.fixtures.UserFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The SSO auto-create path writes a platform group ({@code tenant_id IS NULL}) the same way {@code
 * PlatformGroupApi} does. With {@code groups} among the active tables, that write needs the
 * platform-write marker ({@code UserMappingService.findOrCreateGroup}, through {@code
 * executeNewAsPlatformWrite}) or the database refuses it.
 *
 * <p>NOT {@code @Transactional}: {@code findOrCreateGroup} opens a nested, platform-write
 * transaction for the write (it must sit inside an active transaction, as it does in production
 * under {@code SecurityService.userManagement}), which commits independently of a surrounding test
 * transaction, so the row is cleaned up explicitly instead.
 */
class UserMappingServicePlatformGroupWriteTest extends ProductionTablesIntegrationTest {

  @Autowired private UserMappingService userMappingService;
  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private JdbcTemplate jdbc;

  private String userId;
  private String groupId;

  @AfterEach
  void cleanup() {
    if (groupId != null) {
      jdbc.update("DELETE FROM users_groups WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    if (userId != null) {
      jdbc.update("DELETE FROM users_groups WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
  }

  @Test
  @DisplayName("given_noMatchingPlatformGroupAndAutoCreate_should_createIt")
  void given_noMatchingPlatformGroupAndAutoCreate_should_createIt() {
    // Arrange
    User user =
        userRepository.save(
            UserFixture.getUser(
                "sso",
                "platform-group",
                "sso-platform-group-" + UUID.randomUUID() + "@unittests.invalid"));
    userId = user.getId();
    String mappingName = "sso-auto-group-" + UUID.randomUUID();
    String mapping =
        "[{\"idpGroup\": \"idp-group\",\"userGroup\": \"%s\",\"autoCreate\": \"true\"}]"
            .formatted(mappingName);

    // Act: a transaction is open, as it is under SecurityService.userManagement in production
    tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          userMappingService.mapCurrentUserWithGroup(mapping, user, List.of("idp-group"));
          return null;
        });

    // Assert
    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getUnscopedGroups()).hasSize(1);
    Group created = reloaded.getUnscopedGroups().getFirst();
    groupId = created.getId();
    assertThat(created.getTenant()).isNull();
    assertThat(created.getName()).isEqualTo(mappingName);
    assertThat(groupRepository.findByIdAndTenantIsNull(groupId)).isPresent();
  }
}
