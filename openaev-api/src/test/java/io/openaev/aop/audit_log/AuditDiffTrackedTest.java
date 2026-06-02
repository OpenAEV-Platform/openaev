package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.audit.EntityDiffContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests verifying that {@code @AuditDiffTracked} entities produce field-level diffs in
 * {@link EntityDiffContext} when modified or deleted through the JPA lifecycle.
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

  @BeforeEach
  void setup() {
    EntityDiffContext.clear();
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
      Map<String, EntityDiffContext.EntityDiff> diffs = EntityDiffContext.consumeAll();
      assertThat(diffs).containsKey(group.getId());

      EntityDiffContext.EntityDiff diff = diffs.get(group.getId());
      assertThat(diff.entityType()).isEqualTo("Group");
      assertThat(diff.operation()).isEqualTo("update");
      assertThat(diff.changes()).isNotEmpty();

      boolean hasNameChange =
          diff.changes().stream()
              .anyMatch(
                  c ->
                      "group_name".equals(c.field())
                          && "OriginalName".equals(c.oldValue())
                          && "UpdatedName".equals(c.newValue()));
      assertThat(hasNameChange)
          .as("Expected change entry for group_name: OriginalName → UpdatedName")
          .isTrue();
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
      Map<String, EntityDiffContext.EntityDiff> diffs = EntityDiffContext.consumeAll();
      EntityDiffContext.EntityDiff diff = diffs.get(group.getId());
      assertThat(diff).isNotNull();
      assertThat(diff.changes()).anyMatch(c -> "group_description".equals(c.field()));
      // Name should NOT appear in changes since it didn't change
      assertThat(diff.changes()).noneMatch(c -> "group_name".equals(c.field()));
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

      // Act
      Role loaded = entityManager.find(Role.class, role.getId());
      loaded.setName("NewRoleName");
      entityManager.flush();

      // Assert
      Map<String, EntityDiffContext.EntityDiff> diffs = EntityDiffContext.consumeAll();
      assertThat(diffs).containsKey(role.getId());

      EntityDiffContext.EntityDiff diff = diffs.get(role.getId());
      assertThat(diff.entityType()).isEqualTo("Role");
      assertThat(diff.operation()).isEqualTo("update");
      assertThat(diff.changes())
          .anyMatch(
              c ->
                  "role_name".equals(c.field())
                      && "OldRoleName".equals(c.oldValue())
                      && "NewRoleName".equals(c.newValue()));
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
      EntityDiffContext.clear(); // discard the "create" diff from persist

      // Act — reload but don't change anything
      Group loaded = entityManager.find(Group.class, group.getId());
      loaded.setName("Same"); // same value
      entityManager.flush();

      // Assert
      Map<String, EntityDiffContext.EntityDiff> diffs = EntityDiffContext.consumeAll();
      assertThat(diffs).doesNotContainKey(group.getId());
    }
  }
}
