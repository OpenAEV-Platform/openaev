package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.service.platform.groups.PlatformGroupService;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.object.ObjectDiffUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Integration tests verifying that {@code @AuditDiffTracked} entities produce field-level diffs in
 * {@link AuditLogContext} when modified or deleted through the JPA lifecycle.
 *
 * <p>These tests verify the diff mechanism at the service/entity level (not the full HTTP audit
 * pipeline) to avoid flush-timing issues inherent to {@code @Transactional} tests.
 */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
@DisplayName("@AuditDiffTracked entity diff capture")
@WithMockUser(isAdmin = true)
class AuditDiffTrackedTest extends IntegrationTest {

  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private PlatformRoleComposer platformRoleComposer;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private UserComposer userComposer;
  @Autowired private PlatformGroupService platformGroupService;

  @BeforeEach
  void setup() {
    // Ensure a RequestContext is available so AuditLogContext uses request-scoped storage
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    AuditLogContext.clear();
  }

  @AfterEach
  void tearDown() {
    AuditLogContext.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  @Nested
  @DisplayName("Group update diffs")
  class GroupUpdateDiffs {

    @Test
    @DisplayName("Given group name change, should capture diff with old and new values")
    void given_groupNameChange_should_captureDiff() {
      // Arrange
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("OriginalName"))
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();

      // Act — reload and modify (triggers @PostLoad → snapshot, then @PreUpdate → diff)
      Group loaded = entityManager.find(Group.class, group.getId());
      loaded.setName("UpdatedName");
      entityManager.flush(); // triggers @PreUpdate → computes diff

      // Assert
      Map<String, AuditLogContext.EntitySnapshot> snapshots = AuditLogContext.consumeAllSnapshots();
      assertThat(snapshots).containsKey(group.getId());

      AuditLogContext.EntitySnapshot snapshot = snapshots.get(group.getId());
      assertThat(snapshot.entityType()).isEqualTo("Group");
      assertThat(snapshot.operation()).isEqualTo("update");
      assertThat(snapshot.before()).isNotNull();
      assertThat(snapshot.after()).isNotNull();
      assertThat(snapshot.before().get("group_name")).isEqualTo("OriginalName");
      assertThat(snapshot.after().get("group_name")).isEqualTo("UpdatedName");
    }

    @Test
    @DisplayName("Given group description change, should capture only changed field")
    void given_groupDescriptionChange_should_captureOnlyChangedField() {
      // Arrange
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("Unchanged"))
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();

      // Act
      Group loaded = entityManager.find(Group.class, group.getId());
      loaded.setDescription("New description");
      entityManager.flush();

      // Assert
      Map<String, AuditLogContext.EntitySnapshot> snapshots = AuditLogContext.consumeAllSnapshots();
      AuditLogContext.EntitySnapshot snapshot = snapshots.get(group.getId());
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.after().get("group_description")).isEqualTo("New description");
      // Name should NOT differ since it didn't change
      assertThat(snapshot.before().get("group_name")).isEqualTo(snapshot.after().get("group_name"));
    }
  }

  @Nested
  @DisplayName("Role update diffs")
  class RoleUpdateDiffs {

    @Test
    @DisplayName("Given role name change, should capture diff")
    void given_roleNameChange_should_captureDiff() {
      // Arrange
      Role role =
          platformRoleComposer
              .forPlatformRole(
                  PlatformRoleFixture.getPlatformRole(
                      "OldRoleName", Set.of(Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES)))
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();

      // Act: a platform role is renamed in a platform-write transaction, as the platform role
      // endpoint opens it
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
      Role loaded = entityManager.find(Role.class, role.getId());
      loaded.setName("NewRoleName");
      entityManager.flush();

      // Assert
      Map<String, AuditLogContext.EntitySnapshot> snapshots = AuditLogContext.consumeAllSnapshots();
      assertThat(snapshots).containsKey(role.getId());

      AuditLogContext.EntitySnapshot snapshot = snapshots.get(role.getId());
      assertThat(snapshot.entityType()).isEqualTo("Role");
      assertThat(snapshot.operation()).isEqualTo("update");
      assertThat(snapshot.before().get("role_name")).isEqualTo("OldRoleName");
      assertThat(snapshot.after().get("role_name")).isEqualTo("NewRoleName");
    }
  }

  @Nested
  @DisplayName("No-op updates (no diff)")
  class NoOpUpdates {

    @Test
    @DisplayName("Given no actual change, should produce no diff")
    void given_noChange_should_produceNoDiff() {
      // Arrange
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("Same"))
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      AuditLogContext.clear(); // discard the "create" diff from persist

      // Act — reload but don't change anything
      Group loaded = entityManager.find(Group.class, group.getId());
      loaded.setName("Same"); // same value
      entityManager.flush();

      // Assert
      Map<String, AuditLogContext.EntitySnapshot> snapshots = AuditLogContext.consumeAllSnapshots();
      assertThat(snapshots).doesNotContainKey(group.getId());
    }
  }

  @Nested
  @DisplayName("Group members (LAZY, read by the update snapshot only when they change)")
  class GroupMembers {

    private record GroupWithMembers(String groupId, List<String> memberIds) {}

    private GroupWithMembers groupWithMembers(String name, int members) {
      PlatformGroupComposer.Composer groupComposer =
          platformGroupComposer.forPlatformGroup(PlatformGroupFixture.getPlatformGroup(name));
      String groupId = groupComposer.persist().get().getId();
      List<String> memberIds = new ArrayList<>();
      for (int i = 0; i < members; i++) {
        memberIds.add(
            userComposer
                .forUser(
                    UserFixture.getUser("Member", "Audit", UUID.randomUUID() + "@test.invalid"))
                .withGroup(groupComposer)
                .persist()
                .get()
                .getId());
      }
      entityManager.flush();
      entityManager.clear();
      AuditLogContext.clear();
      return new GroupWithMembers(groupId, memberIds);
    }

    @Test
    @DisplayName("given_userSharingAGroup_should_loadOnlyThatUserWithAuditEnabled")
    void given_userSharingAGroup_should_loadOnlyThatUserWithAuditEnabled() {
      // Arrange
      String userId = groupWithMembers("Shared audited group", 5).memberIds().getFirst();
      Statistics stats = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
      boolean statisticsWereEnabled = stats.isStatisticsEnabled();
      stats.setStatisticsEnabled(true);
      stats.clear();

      // Act — the @PostLoad snapshot of each of its groups must not load the group's members
      long loadedUsers;
      try {
        User user = entityManager.find(User.class, userId);
        assertThat(user.getUnscopedGroups()).isNotEmpty();
        loadedUsers = stats.getEntityStatistics(User.class.getName()).getLoadCount();
      } finally {
        stats.setStatisticsEnabled(statisticsWereEnabled);
      }

      // Assert
      assertThat(loadedUsers).as("loading a user must not load its group peers").isEqualTo(1);
    }

    @Test
    @DisplayName("given_groupDeleted_should_listItsMembersInTheDeleteSnapshot")
    void given_groupDeleted_should_listItsMembersInTheDeleteSnapshot() {
      // Arrange
      GroupWithMembers group = groupWithMembers("Deleted audited group", 2);

      // Act — through the service, which unlinks the members before the delete
      platformGroupService.delete(group.groupId());
      entityManager.flush();

      // Assert
      AuditLogContext.EntitySnapshot snapshot =
          AuditLogContext.consumeAllSnapshots().get(group.groupId());
      assertThat(snapshot.operation()).isEqualTo("delete");
      assertThat(snapshot.before().get("group_users"))
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .containsExactlyInAnyOrderElementsOf(group.memberIds());
      assertThat(
              ObjectDiffUtils.computeFieldChanges(snapshot.before(), snapshot.after()).stream()
                  .map(ObjectDiffUtils.FieldChange::field))
          .contains("group_users");
    }

    @Test
    @DisplayName("given_membersReadDuringAnUpdate_should_notReportAMembersChange")
    void given_membersReadDuringAnUpdate_should_notReportAMembersChange() {
      // Arrange
      GroupWithMembers group = groupWithMembers("Updated audited group", 2);

      // Act — the members are loaded after the before-snapshot, then a field changes
      Group loaded = entityManager.find(Group.class, group.groupId());
      assertThat(loaded.getUsers()).hasSize(2);
      loaded.setName("Renamed audited group");
      entityManager.flush();

      // Assert
      AuditLogContext.EntitySnapshot snapshot =
          AuditLogContext.consumeAllSnapshots().get(group.groupId());
      assertThat(snapshot.before().get("group_users"))
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .containsExactlyInAnyOrderElementsOf(group.memberIds());
      assertThat(changedFields(snapshot)).containsExactly("group_name");
    }

    @Test
    @DisplayName("given_membersUntouchedDuringAnUpdate_should_leaveThemOutOfTheDiff")
    void given_membersUntouchedDuringAnUpdate_should_leaveThemOutOfTheDiff() {
      // Arrange
      String groupId = groupWithMembers("Untouched audited group", 2).groupId();

      // Act
      Group loaded = entityManager.find(Group.class, groupId);
      loaded.setName("Renamed untouched group");
      entityManager.flush();

      // Assert
      AuditLogContext.EntitySnapshot snapshot = AuditLogContext.consumeAllSnapshots().get(groupId);
      assertThat(snapshot.before()).doesNotContainKey("group_users");
      assertThat(snapshot.after()).doesNotContainKey("group_users");
      assertThat(changedFields(snapshot)).containsExactly("group_name");
    }

    @Test
    @DisplayName("given_membersReplacedWithoutBeingRead_should_diffAgainstTheMembersAtLoad")
    void given_membersReplacedWithoutBeingRead_should_diffAgainstTheMembersAtLoad() {
      // Arrange
      GroupWithMembers group = groupWithMembers("Replaced audited group", 2);

      // Act — the list is replaced without being read first: the update snapshot reads the old one
      Group loaded = entityManager.find(Group.class, group.groupId());
      loaded.setUsers(new ArrayList<>());
      entityManager.flush();

      // Assert
      AuditLogContext.EntitySnapshot snapshot =
          AuditLogContext.consumeAllSnapshots().get(group.groupId());
      assertMembersChange(snapshot, group.memberIds(), List.of());
    }

    private List<String> changedFields(AuditLogContext.EntitySnapshot snapshot) {
      return ObjectDiffUtils.computeFieldChanges(snapshot.before(), snapshot.after()).stream()
          .map(ObjectDiffUtils.FieldChange::field)
          .toList();
    }

    private void assertMembersChange(
        AuditLogContext.EntitySnapshot snapshot, List<String> before, List<String> after) {
      assertThat(snapshot.operation()).isEqualTo("update");
      assertThat(changedFields(snapshot)).containsExactly("group_users");
      assertThat(snapshot.before().get("group_users"))
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .containsExactlyInAnyOrderElementsOf(before);
      assertThat(snapshot.after().get("group_users"))
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .containsExactlyInAnyOrderElementsOf(after);
    }
  }
}
