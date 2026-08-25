package io.openaev.service;

import static io.openaev.utils.fixtures.UserFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest extends IntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private GroupRepository groupRepository;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  @Test
  void given_validInput_should_createUser() {
    // -- ACT --
    UserInput input =
        getUserInputWithPasswordAndPhone(
            "create@test.invalid", "John", "Doe", "secureP@ss1", "+33612345678");
    User created = userService.createUser(input);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getEmail()).isEqualTo("create@test.invalid");
    assertThat(created.getFirstname()).isEqualTo("John");
    assertThat(created.getLastname()).isEqualTo("Doe");
    assertThat(created.getPassword()).isNotBlank();
    assertThat(created.getPhone()).isEqualTo("+33612345678");
  }

  @Test
  @DisplayName("given_inputWithTenantIds_should_createUserAndEvictMembershipCacheWithoutThrowing")
  void given_inputWithTenantIds_should_createUserAndEvictMembershipCache() {
    // -- ARRANGE --
    // Regression test for a NullPointerException: creating a user with tenantIds evicted the
    // tenant-membership cache using the not-yet-persisted user's id (null before save), which
    // Caffeine's evict() rejects (ConcurrentHashMap forbids null keys).
    Tenant tenant =
        tenantComposer.forTenant(TenantFixture.getTenant("create-with-tenants")).persist().get();
    UserInput input =
        getUserInputWithTenants(
            "create-with-tenants@test.invalid",
            "Jane",
            "Doe",
            "secureP@ss1",
            List.of(tenant.getId()));

    // -- ACT --
    User created = userService.createUser(input);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getTenants()).extracting(Tenant::getId).containsExactly(tenant.getId());
  }

  @Test
  @DisplayName("given_platformCreationWithTenants_should_assignPlatformAndTenantAutoAssignGroups")
  void given_platformCreationWithTenants_should_assignAutoAssignGroups() {
    // -- ARRANGE --
    Tenant tenant =
        tenantComposer.forTenant(TenantFixture.getTenant("auto-assign-create")).persist().get();
    Group platformGroup = autoAssignGroup("platform-auto-assign-create", null);
    Group tenantGroup = autoAssignGroup("tenant-auto-assign-create", tenant);

    // -- ACT --
    User created =
        userService.createUser(
            getUserInputWithTenants(
                "auto-assign-create@test.invalid",
                "Auto",
                "Assign",
                "secureP@ss1",
                List.of(tenant.getId())));

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(created.getId());
    assertThat(reloaded.getUnscopedGroups())
        .extracting(Group::getId)
        .contains(platformGroup.getId(), tenantGroup.getId());
  }

  @Test
  @DisplayName("given_platformUpdateAttachingTenant_should_assignTenantAutoAssignGroups")
  void given_platformUpdateAttachingTenant_should_assignAutoAssignGroups() {
    // -- ARRANGE --
    Tenant tenant =
        tenantComposer.forTenant(TenantFixture.getTenant("auto-assign-update")).persist().get();
    Group tenantGroup = autoAssignGroup("tenant-auto-assign-update", tenant);
    User persisted =
        userComposer
            .forUser(getUser("Auto", "Update", "auto-assign-update@test.invalid"))
            .persist()
            .get();

    // -- ACT --
    userService.updateUser(
        persisted.getId(),
        getUserInputWithTenants(
            "auto-assign-update@test.invalid",
            "Auto",
            "Update",
            "secureP@ss1",
            List.of(tenant.getId())));

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(persisted.getId());
    assertThat(reloaded.getUnscopedGroups()).extracting(Group::getId).contains(tenantGroup.getId());
  }

  @Test
  @DisplayName("given_groupRemovedInTenant_should_notReassignItOnPlatformUpdate")
  void given_groupRemovedInTenant_should_notReassignItOnPlatformUpdate() {
    // -- ARRANGE --
    Tenant tenant =
        tenantComposer.forTenant(TenantFixture.getTenant("auto-assign-removed")).persist().get();
    Group tenantGroup = autoAssignGroup("tenant-auto-assign-removed", tenant);
    UserInput input =
        getUserInputWithTenants(
            "auto-assign-removed@test.invalid",
            "Auto",
            "Removed",
            "secureP@ss1",
            List.of(tenant.getId()));
    User created = userService.createUser(input);
    // The tenant admin removes the auto-assign group from the user, from within the tenant.
    created.getUnscopedGroups().remove(tenantGroup);
    userService.saveUser(created);
    entityManager.flush();
    entityManager.clear();

    // -- ACT --
    // A later update from the platform screen keeps the very same tenants attached.
    userService.updateUser(created.getId(), input);

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(created.getId());
    assertThat(reloaded.getUnscopedGroups())
        .extracting(Group::getId)
        .doesNotContain(tenantGroup.getId());
  }

  private Group autoAssignGroup(String name, Tenant tenant) {
    Group group = new Group();
    group.setName(name);
    group.setDefaultUserAssignation(true);
    group.setTenant(tenant);
    return groupRepository.save(group);
  }

  // -- READ --

  @Test
  void given_existingUser_should_findUserById() {
    // -- ARRANGE --
    User persisted =
        userComposer.forUser(getUser("Read", "Test", "read@test.invalid")).persist().get();

    // -- ACT --
    User found = userService.user(persisted.getId());

    // -- ASSERT --
    assertThat(found.getEmail()).isEqualTo("read@test.invalid");
    assertThat(found.getFirstname()).isEqualTo("Read");
    assertThat(found.getLastname()).isEqualTo("Test");
  }

  // -- UPDATE --

  @Test
  void given_existingUser_should_updateUserButKeepItsAddress() {
    // -- ARRANGE --
    User persisted =
        userComposer.forUser(getUser("Original", "Name", "update@test.invalid")).persist().get();

    // -- ACT --
    UserInput input =
        getUserInputWithPgpKey("updated@test.invalid", "Updated", "Lastname", "pgp-key-123");
    User updated = userService.updateUser(persisted.getId(), input);

    // -- ASSERT --
    assertThat(updated.getEmail()).isEqualTo("update@test.invalid");
    assertThat(updated.getFirstname()).isEqualTo("Updated");
    assertThat(updated.getLastname()).isEqualTo("Lastname");
    assertThat(updated.getPgpKey()).isEqualTo("pgp-key-123");
  }

  // -- DELETE --

  @Test
  void given_existingUser_should_deleteUser() {
    // -- ARRANGE --
    User persisted =
        userComposer.forUser(getUser("Delete", "Me", "delete@test.invalid")).persist().get();

    // -- ACT --
    userService.delete(persisted.getId());

    // -- ASSERT --
    assertThatThrownBy(() -> userService.user(persisted.getId()))
        .isInstanceOf(ElementNotFoundException.class);
  }
}
