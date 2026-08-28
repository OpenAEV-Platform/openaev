package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.audit.AuditLogContext;
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

      // Act
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
}
