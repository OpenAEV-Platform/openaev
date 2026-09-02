package io.openaev.service;

import static io.openaev.utils.fixtures.UserFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest extends IntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TokenRepository tokenRepository;
  @Autowired private TestUserHolder testUserHolder;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  @Test
  void given_validInput_should_createUser() {
    // -- ACT --
    UserInput input =
        getUserInputWithPasswordAndPhone(
            "create@test.invalid", "John", "Doe", "secureP@ss1", "+33612345678");
    User created = userService.createUser(input, UserCreationScope.PLATFORM);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getEmail()).isEqualTo("create@test.invalid");
    assertThat(created.getFirstname()).isEqualTo("John");
    assertThat(created.getLastname()).isEqualTo("Doe");
    assertThat(created.getPassword()).isNotBlank();
    assertThat(created.getPhone()).isEqualTo("+33612345678");
  }

  @Test
  void given_inputWithoutPassword_should_createUserWithGeneratedPassword() {
    // -- ACT --
    UserInput input =
        new UserInput(
            "no-password@test.invalid",
            "No",
            "Password",
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    User created = userService.createUser(input, UserCreationScope.PLATFORM);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getEmail()).isEqualTo("no-password@test.invalid");
    assertThat(created.getPassword()).isNotBlank();
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
    User created = userService.createUser(input, UserCreationScope.PLATFORM);

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
                List.of(tenant.getId())),
            UserCreationScope.PLATFORM);

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(created.getId());
    assertThat(reloaded.getUnscopedGroups())
        .extracting(Group::getId)
        .contains(platformGroup.getId(), tenantGroup.getId());
  }

  @Test
  @DisplayName("given_internalUserCreation_should_notAssignPlatformAutoAssignGroups")
  void given_internalUserCreation_should_notAssignPlatformAutoAssignGroups() {
    // -- ARRANGE --
    // Internal accounts (SSO, connectors, service accounts) always land in a tenant attached by
    // the caller: they must never inherit the platform-wide auto-assign groups.
    Group platformGroup = autoAssignGroup("platform-auto-assign-internal", null);

    // -- ACT --
    User created =
        userService.createInternalUser(
            "internal-auto-assign@test.invalid",
            "Internal",
            "Account",
            false,
            UUID.randomUUID().toString());

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(created.getId());
    assertThat(reloaded.getUnscopedGroups())
        .extracting(Group::getId)
        .doesNotContain(platformGroup.getId());
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
    User created = userService.createUser(input, UserCreationScope.PLATFORM);
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

  @Test
  @DisplayName("given_alreadyAttachedTenant_should_notAssignAutoAssignGroupsOnUpdate")
  void given_alreadyAttachedTenant_should_notAssignAutoAssignGroupsOnUpdate() {
    // -- ARRANGE --
    Tenant tenant =
        tenantComposer.forTenant(TenantFixture.getTenant("auto-assign-unchanged")).persist().get();
    UserInput input =
        getUserInputWithTenants(
            "auto-assign-unchanged@test.invalid",
            "Auto",
            "Unchanged",
            "secureP@ss1",
            List.of(tenant.getId()));
    User created = userService.createUser(input, UserCreationScope.PLATFORM);
    entityManager.flush();
    entityManager.clear();
    // The auto-assign group only appears after the user already belongs to the tenant.
    Group lateGroup = autoAssignGroup("tenant-auto-assign-late", tenant);

    // -- ACT --
    // Updating the user without changing his tenants must not trigger any auto-assignment.
    userService.updateUser(created.getId(), input);

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(created.getId());
    assertThat(reloaded.getUnscopedGroups())
        .extracting(Group::getId)
        .doesNotContain(lateGroup.getId());
  }

  @Test
  @DisplayName("given_platformUpdateDetachingTenant_should_revokeThatTenantGroups")
  void given_platformUpdateDetachingTenant_should_revokeTenantGroups() {
    // -- ARRANGE --
    Tenant kept = tenantComposer.forTenant(TenantFixture.getTenant("detach-kept")).persist().get();
    Tenant left = tenantComposer.forTenant(TenantFixture.getTenant("detach-left")).persist().get();
    Group keptGroup = autoAssignGroup("tenant-auto-assign-kept", kept);
    Group leftGroup = autoAssignGroup("tenant-auto-assign-left", left);
    Group platformGroup = autoAssignGroup("platform-auto-assign-detach", null);
    User created =
        userService.createUser(
            getUserInputWithTenants(
                "detach-tenant@test.invalid",
                "Detach",
                "Tenant",
                "secureP@ss1",
                List.of(kept.getId(), left.getId())),
            UserCreationScope.PLATFORM);
    entityManager.flush();
    entityManager.clear();

    // -- ACT --
    // The platform screen drops one of the two tenants.
    userService.updateUser(
        created.getId(),
        getUserInputWithTenants(
            "detach-tenant@test.invalid",
            "Detach",
            "Tenant",
            "secureP@ss1",
            List.of(kept.getId())));

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(created.getId());
    assertThat(reloaded.getUnscopedGroups())
        .extracting(Group::getId)
        .doesNotContain(leftGroup.getId())
        .contains(keptGroup.getId(), platformGroup.getId());
  }

  @Test
  @DisplayName("given_detachedTenant_should_keepGroupsOfTheOtherScopes")
  void given_revokeTenantGroups_should_onlyDropTheGivenTenantGroups() {
    // -- ARRANGE --
    Tenant tenant =
        tenantComposer.forTenant(TenantFixture.getTenant("revoke-scoped")).persist().get();
    Group tenantGroup = autoAssignGroup("tenant-auto-assign-revoke", tenant);
    Group platformGroup = autoAssignGroup("platform-auto-assign-revoke", null);
    User created =
        userService.createUser(
            getUserInputWithTenants(
                "revoke-scoped@test.invalid",
                "Revoke",
                "Scoped",
                "secureP@ss1",
                List.of(tenant.getId())),
            UserCreationScope.PLATFORM);
    entityManager.flush();
    entityManager.clear();

    // -- ACT --
    userService.revokeTenantGroups(created.getId(), List.of(tenant.getId()));

    // -- ASSERT --
    entityManager.flush();
    entityManager.clear();
    User reloaded = userService.user(created.getId());
    assertThat(reloaded.getUnscopedGroups())
        .extracting(Group::getId)
        .doesNotContain(tenantGroup.getId())
        .contains(platformGroup.getId());
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

  // -- TOKENS --

  @Test
  @WithMockUser
  @DisplayName("given_ownToken_should_hardDeleteItAndIssueANewOne")
  void given_ownToken_should_hardDeleteItAndIssueANewOne() {
    // -- ARRANGE --
    User currentUser = testUserHolder.get();
    Token previousToken = userService.createUserToken(currentUser, UUID.randomUUID().toString());
    String previousTokenId = previousToken.getId();
    String previousTokenValue = previousToken.getValue();

    // -- ACT --
    Token renewedToken = userService.renewUserToken(previousTokenId);
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    assertThat(renewedToken.getId()).isNotNull().isNotEqualTo(previousTokenId);
    assertThat(renewedToken.getValue()).isNotBlank().isNotEqualTo(previousTokenValue);
    assertThat(renewedToken.getUser().getId()).isEqualTo(currentUser.getId());

    // The row is deleted, not updated: neither the old id nor the old value resolve anymore.
    assertThat(tokenRepository.findById(previousTokenId)).isEmpty();
    assertThat(tokenRepository.findByValue(previousTokenValue)).isEmpty();
    assertThat(tokenRepository.findByValue(renewedToken.getValue())).isPresent();
  }

  @Test
  @WithMockUser
  @DisplayName("given_tokenOwnedByAnotherUser_should_throwAccessDenied")
  void given_tokenOwnedByAnotherUser_should_throwAccessDenied() {
    // -- ARRANGE --
    User otherUser =
        userComposer
            .forUser(getUser("Other", "Owner", UUID.randomUUID() + "@test.invalid"))
            .persist()
            .get();
    Token foreignToken = userService.createUserToken(otherUser, UUID.randomUUID().toString());

    // -- ACT & ASSERT --
    assertThatThrownBy(() -> userService.renewUserToken(foreignToken.getId()))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(tokenRepository.findById(foreignToken.getId())).isPresent();
  }

  @Test
  @WithMockUser
  @DisplayName("given_unknownTokenId_should_throwElementNotFound")
  void given_unknownTokenId_should_throwElementNotFound() {
    // -- ACT & ASSERT --
    assertThatThrownBy(() -> userService.renewUserToken(UUID.randomUUID().toString()))
        .isInstanceOf(ElementNotFoundException.class);
  }
}
