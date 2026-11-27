package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The platform-write marker of {@link UserGroupMembershipService} guards the membership statement
 * only: a pending edit of the same platform group is written before the marker is set, so the
 * database still refuses it.
 *
 * <p>NOT {@code @Transactional}: the edit and the membership write share one transaction opened
 * here, and the rows are read back by raw JDBC.
 */
@WithoutTenantScope
@DisplayName("user-group membership platform-write marker")
class UserGroupMembershipPlatformMarkerTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserGroupMembershipService userGroupMembershipService;
  @Autowired private GroupRepository groupRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbc;

  private String userId;
  private String groupId;

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
  }

  @Test
  @DisplayName("given_aPendingEditOfAPlatformGroup_should_refuseItInsteadOfWritingItUnderTheMarker")
  void given_aPendingEditOfAPlatformGroup_should_refuseItInsteadOfWritingItUnderTheMarker() {
    // Arrange: a platform group the user belongs to
    User user =
        userRepository.save(
            UserFixture.getUser(
                "marker",
                "user",
                "marker-user-" + UUID.randomUUID() + "@unittests.invalid",
                false));
    userId = user.getId();
    Group platformGroup = new Group();
    platformGroup.setName("platform-group-" + UUID.randomUUID());
    platformGroup.setDescription("original");
    Group saved =
        tenantTx.executeAsPlatformWrite(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), () -> groupRepository.save(platformGroup));
    groupId = saved.getId();
    jdbc.update("INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", userId, groupId);

    // Act: a tenant transaction edits the platform group, then removes the membership
    Runnable editThenRemove =
        () ->
            tenantTx.execute(
                TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                () -> {
                  Group managed = groupRepository.findById(groupId).orElseThrow();
                  managed.setDescription("edited");
                  userGroupMembershipService.removeMembership(userId, groupId);
                  return null;
                });

    // Assert: the edit is refused, not flushed under the marker
    assertThatThrownBy(editThenRemove::run)
        .hasStackTraceContaining("platform-write")
        .isInstanceOf(RuntimeException.class);
    String description =
        jdbc.queryForObject(
            "SELECT group_description FROM groups WHERE group_id = ?", String.class, groupId);
    assertThat(description).isEqualTo("original");
  }
}
