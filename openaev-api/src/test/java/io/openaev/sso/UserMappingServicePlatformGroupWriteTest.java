package io.openaev.sso;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
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
class UserMappingServicePlatformGroupWriteTest extends IntegrationTest {

  @Autowired private UserMappingService userMappingService;
  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private JdbcTemplate jdbc;

  private String userId;
  private String groupId;
  private final List<String> twinGroupIds = new java.util.ArrayList<>();

  @AfterEach
  void cleanup() {
    twinGroupIds.forEach(
        id -> {
          jdbc.update("DELETE FROM users_groups WHERE group_id = ?", id);
          jdbc.update("DELETE FROM groups WHERE group_id = ?", id);
        });
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

  @Test
  @DisplayName("given_duplicatePlatformGroupNames_should_revokeEveryOneOnIdpRemoval")
  void given_duplicatePlatformGroupNames_should_revokeEveryOneOnIdpRemoval() {
    // Arrange: the name is not unique, so the user can hold two platform groups carrying it
    User user =
        userRepository.save(
            UserFixture.getUser(
                "sso",
                "platform-twins",
                "sso-platform-twins-" + UUID.randomUUID() + "@unittests.invalid"));
    userId = user.getId();
    String name = "sso-twin-group-" + UUID.randomUUID();
    for (int i = 0; i < 2; i++) {
      String id = UUID.randomUUID().toString();
      twinGroupIds.add(id);
      jdbc.update(
          "INSERT INTO groups (group_id, group_name, tenant_id) VALUES (?, ?, NULL)", id, name);
      jdbc.update("INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", userId, id);
    }
    String mapping =
        "[{\"idpGroup\": \"idp-group\",\"userGroup\": \"%s\",\"autoCreate\": \"false\"}]"
            .formatted(name);

    // Act: the idp no longer lists the user in the mapped group
    tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          userMappingService.mapCurrentUserWithGroup(mapping, user, List.of("other-idp-group"));
          return null;
        });

    // Assert
    Integer remaining =
        jdbc.queryForObject(
            "SELECT count(*) FROM users_groups WHERE user_id = ?", Integer.class, userId);
    assertThat(remaining).isZero();
  }

  @Test
  @DisplayName("given_duplicatePlatformGroupNamesAndAMembership_should_keepItOnIdpGrant")
  void given_duplicatePlatformGroupNamesAndAMembership_should_keepItOnIdpGrant() {
    // Arrange: two platform groups share the name and the user already holds the higher id one
    User user = saveUser("platform-twins-member");
    String name = "sso-twin-group-" + UUID.randomUUID();
    List<String> ids = insertPlatformTwins(name);
    String held = ids.getLast();
    jdbc.update("INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", userId, held);

    // Act: the idp still lists the user in the mapped group
    mapInTransaction(name, user, List.of("idp-group"));

    // Assert: the login went through and the membership is the one the user already had
    assertThat(groupIdsOf(userId)).containsExactly(held);
  }

  @Test
  @DisplayName("given_duplicatePlatformGroupNamesAndNoMembership_should_joinTheLowestId")
  void given_duplicatePlatformGroupNamesAndNoMembership_should_joinTheLowestId() {
    // Arrange: two platform groups share the name, the user holds neither
    User user = saveUser("platform-twins-new");
    String name = "sso-twin-group-" + UUID.randomUUID();
    List<String> ids = insertPlatformTwins(name);

    // Act
    mapInTransaction(name, user, List.of("idp-group"));

    // Assert: one membership, on the same group whatever the row order
    assertThat(groupIdsOf(userId)).containsExactly(ids.getFirst());
  }

  private User saveUser(String lastname) {
    User user =
        userRepository.save(
            UserFixture.getUser(
                "sso",
                lastname,
                "sso-" + lastname + "-" + UUID.randomUUID() + "@unittests.invalid"));
    userId = user.getId();
    return user;
  }

  /** Two platform groups of {@code name}, their ids in ascending order. */
  private List<String> insertPlatformTwins(String name) {
    List<String> ids =
        java.util.stream.Stream.generate(() -> UUID.randomUUID().toString())
            .limit(2)
            .sorted()
            .toList();
    for (String id : ids) {
      twinGroupIds.add(id);
      jdbc.update(
          "INSERT INTO groups (group_id, group_name, tenant_id) VALUES (?, ?, NULL)", id, name);
    }
    return ids;
  }

  private void mapInTransaction(String groupName, User user, List<String> tokenGroups) {
    String mapping =
        "[{\"idpGroup\": \"idp-group\",\"userGroup\": \"%s\",\"autoCreate\": \"false\"}]"
            .formatted(groupName);
    tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          userMappingService.mapCurrentUserWithGroup(mapping, user, tokenGroups);
          return null;
        });
  }

  private List<String> groupIdsOf(String user) {
    return jdbc.queryForList(
        "SELECT group_id FROM users_groups WHERE user_id = ?", String.class, user);
  }
}
