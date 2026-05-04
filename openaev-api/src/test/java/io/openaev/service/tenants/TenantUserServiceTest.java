package io.openaev.service.tenants;

import static io.openaev.utils.fixtures.UserFixture.getUser;
import static io.openaev.utils.fixtures.UserFixture.getUserInput;
import static io.openaev.utils.fixtures.tenants.TenantFixture.getTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.api.users.dto.UserOutput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawUser;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@WithMockUser
class TenantUserServiceTest extends IntegrationTest {

  @Autowired private TenantUserService tenantUserService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private EntityManager entityManager;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantComposer.forTenant(getTenant()).persist().get();
    TenantContext.setCurrentTenant(tenant.getId());
  }

  // -- HELPERS --

  private User persistedUserInTenant(String firstName, String lastName, String email) {
    User user = userComposer.forUser(getUser(firstName, lastName, email)).persist().get();
    tenantRepository.addUserToTenant(user.getId(), tenant.getId());
    entityManager.flush();
    return user;
  }

  private User persistedUserNotInTenant(String firstName, String lastName, String email) {
    User user = userComposer.forUser(getUser(firstName, lastName, email)).persist().get();
    entityManager.flush();
    return user;
  }

  @Nested
  @DisplayName("Create — createOrAttach")
  class CreateOrAttach {

    @Test
    @DisplayName("Given a new user should create and attach to tenant")
    void given_newUser_should_createAndAttachToTenant() {
      // -- ARRANGE --
      UserInput input = getUserInput("newuser@test.invalid", "Elliot", "Alderson");

      // -- ACT --
      UserOutput result = tenantUserService.createOrAttach(input);

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.id()).isNotNull();
      assertThat(result.email()).isEqualToIgnoringCase("newuser@test.invalid");
      assertThat(result.firstname()).isEqualTo("Elliot");
      // Verify the user is attached to the current tenant
      UserOutput found = tenantUserService.user(result.id());
      assertThat(found.id()).isEqualTo(result.id());
    }

    @Test
    @DisplayName("Given an existing user should attach to tenant without creating a new one")
    void given_existingUser_should_attachToTenant() {
      // -- ARRANGE --
      User existingUser =
          userComposer
              .forUser(getUser("Darlene", "Alderson", "existing@test.invalid"))
              .persist()
              .get();
      String existingUserId = existingUser.getId();
      entityManager.flush();

      UserInput input = getUserInput("existing@test.invalid", "Darlene", "Alderson");

      // -- ACT --
      UserOutput result = tenantUserService.createOrAttach(input);

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(existingUserId);
      assertThat(result.email()).isEqualToIgnoringCase("existing@test.invalid");
      // Verify the user is attached to the current tenant
      UserOutput found = tenantUserService.user(existingUserId);
      assertThat(found.id()).isEqualTo(existingUserId);
    }
  }

  @Nested
  @DisplayName("Read — user, find, users")
  class Read {

    @Test
    @DisplayName("Given a user in the tenant should find by ID")
    void given_userInTenant_should_findById() {
      // -- ARRANGE --
      User user = persistedUserInTenant("Tyrell", "Wellick", "charlie@test.invalid");

      // -- ACT --
      UserOutput result = tenantUserService.user(user.getId());

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(user.getId());
      assertThat(result.email()).isEqualToIgnoringCase("charlie@test.invalid");
    }

    @Test
    @DisplayName("Given a user not in the tenant should throw ElementNotFoundException")
    void given_userNotInTenant_should_throwElementNotFound() {
      // -- ARRANGE --
      User user = persistedUserNotInTenant("Angela", "Moss", "diana@test.invalid");

      // -- ACT & ASSERT --
      assertThatThrownBy(() -> tenantUserService.user(user.getId()))
          .isInstanceOf(ElementNotFoundException.class)
          .hasMessageContaining(user.getId());
    }

    @Test
    @DisplayName("Given user IDs should find users in tenant")
    void given_userIds_should_findInTenant() {
      // -- ARRANGE --
      User user1 = persistedUserInTenant("Dominique", "DiPierro", "eve@test.invalid");
      User user2 = persistedUserInTenant("Irving", "Borstein", "frank@test.invalid");
      User outsideUser = persistedUserNotInTenant("Phillip", "Price", "ghost@test.invalid");

      // -- ACT --
      List<UserOutput> result =
          tenantUserService.find(List.of(user1.getId(), user2.getId(), outsideUser.getId()));

      // -- ASSERT --
      assertThat(result).hasSize(2);
      assertThat(result)
          .extracting(UserOutput::email)
          .containsExactlyInAnyOrder("eve@test.invalid", "frank@test.invalid");
    }

    @Test
    @DisplayName("Given an empty IDs list should return empty list")
    void given_emptyIdsList_should_returnEmptyList() {
      // -- ACT --
      List<UserOutput> result = tenantUserService.find(List.of());

      // -- ASSERT --
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given users in tenant should return all raw users")
    void given_usersInTenant_should_returnAllRawUsers() {
      // -- ARRANGE --
      persistedUserInTenant("Leon", "Basset", "grace@test.invalid");
      persistedUserInTenant("Joanna", "Wellick", "hank@test.invalid");
      persistedUserNotInTenant("Terry", "Colby", "ivan@test.invalid");

      // -- ACT --
      List<RawUser> result = tenantUserService.users();

      // -- ASSERT --
      assertThat(result).hasSizeGreaterThanOrEqualTo(2);
      assertThat(result)
          .extracting(RawUser::getUser_email)
          .contains("grace@test.invalid", "hank@test.invalid")
          .doesNotContain("ivan@test.invalid");
    }
  }

  @Nested
  @DisplayName("Search — paginated search")
  class Search {

    @Test
    @DisplayName("Given users in tenant should search with pagination")
    void given_usersInTenant_should_searchWithPagination() {
      // -- ARRANGE --
      persistedUserInTenant("Fernando", "Vera", "julia@test.invalid");
      persistedUserInTenant("Janice", "Ironside", "kevin@test.invalid");
      persistedUserNotInTenant("Scott", "Knowles", "laura@test.invalid");

      SearchPaginationInput searchInput = PaginationFixture.getDefault().size(10).build();

      // -- ACT --
      Page<UserOutput> result = tenantUserService.search(searchInput);

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(2);
      assertThat(result.getContent())
          .extracting(UserOutput::email)
          .contains("julia@test.invalid", "kevin@test.invalid")
          .doesNotContain("laura@test.invalid");
    }
  }

  @Nested
  @DisplayName("Delete — detach")
  class Detach {

    @Test
    @DisplayName("Given a user in tenant should detach from tenant")
    void given_userInTenant_should_detachFromTenant() {
      // -- ARRANGE --
      User user = persistedUserInTenant("Edward", "Alderson", "max@test.invalid");

      // -- ACT --
      tenantUserService.detach(user.getId());
      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      assertThatThrownBy(() -> tenantUserService.user(user.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Auto-assign — default tenant groups")
  class AutoAssign {

    @Test
    @DisplayName("Given a tenant group with default assign, should auto-assign new user")
    void given_defaultAssignGroup_should_autoAssignNewUser() {
      // -- ARRANGE --
      Group autoGroup = TenantGroupFixture.getGroup("AutoAssignTenant");
      autoGroup.setDefaultUserAssignation(true);
      tenantGroupComposer.forGroup(autoGroup).persist();
      entityManager.flush();

      UserInput input = getUserInput("tenant-auto@test.invalid", "TenantAuto", "Assign");

      // -- ACT --
      UserOutput result = tenantUserService.createOrAttach(input);

      // -- ASSERT --
      entityManager.flush();
      entityManager.clear();
      Group reloaded = groupRepository.findById(autoGroup.getId()).orElseThrow();
      assertThat(reloaded.getUsers()).extracting(User::getId).contains(result.id());
    }

    @Test
    @DisplayName("Given a tenant group without default assign, should not auto-assign")
    void given_noDefaultAssignGroup_should_notAutoAssign() {
      // -- ARRANGE --
      Group noAutoGroup = TenantGroupFixture.getGroup("NoAutoTenant");
      noAutoGroup.setDefaultUserAssignation(false);
      tenantGroupComposer.forGroup(noAutoGroup).persist();
      entityManager.flush();

      UserInput input = getUserInput("tenant-noauto@test.invalid", "NoAuto", "Tenant");

      // -- ACT --
      UserOutput result = tenantUserService.createOrAttach(input);

      // -- ASSERT --
      entityManager.flush();
      entityManager.clear();
      Group reloaded = groupRepository.findById(noAutoGroup.getId()).orElseThrow();
      assertThat(reloaded.getUsers()).extracting(User::getId).doesNotContain(result.id());
    }

    @Test
    @DisplayName("Given an existing user attached to tenant, should auto-assign to default groups")
    void given_existingUser_should_autoAssignOnAttach() {
      // -- ARRANGE --
      Group autoGroup = TenantGroupFixture.getGroup("AutoAssignExisting");
      autoGroup.setDefaultUserAssignation(true);
      tenantGroupComposer.forGroup(autoGroup).persist();

      User existingUser =
          userComposer
              .forUser(getUser("Existing", "User", "existing-auto@test.invalid"))
              .persist()
              .get();
      entityManager.flush();

      UserInput input = getUserInput("existing-auto@test.invalid", "Existing", "User");

      // -- ACT --
      tenantUserService.createOrAttach(input);

      // -- ASSERT --
      entityManager.flush();
      entityManager.clear();
      Group reloaded = groupRepository.findById(autoGroup.getId()).orElseThrow();
      assertThat(reloaded.getUsers()).extracting(User::getId).contains(existingUser.getId());
    }
  }
}
