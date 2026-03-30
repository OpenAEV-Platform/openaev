package io.openaev.service.platform.groups;

import static io.openaev.utils.fixtures.platform.PlatformGroupFixture.*;
import static io.openaev.utils.fixtures.platform.PlatformRoleFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.api.platform.groups.PlatformGroupOutput;
import io.openaev.database.model.PlatformGroup;
import io.openaev.database.model.PlatformRole;
import io.openaev.database.repository.PlatformGroupRepository;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformRoleComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformGroupServiceTest extends IntegrationTest {

  @Autowired private PlatformGroupService platformGroupService;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private PlatformRoleComposer platformRoleComposer;
  @Autowired private PlatformGroupRepository platformGroupRepository;
  @Autowired protected EntityManager entityManager;

  // -- CREATE --

  @Test
  void given_validInput_should_createPlatformGroup() {
    // -- ACT --
    PlatformGroup created =
        platformGroupService.createPlatformGroup(PLATFORM_GROUP_NAME, PLATFORM_GROUP_DESCRIPTION);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo(PLATFORM_GROUP_NAME);
    assertThat(created.getDescription()).isEqualTo(PLATFORM_GROUP_DESCRIPTION);
  }

  @Test
  void given_duplicateName_should_failToCreatePlatformGroup() {
    // -- ARRANGE --
    platformGroupComposer.forPlatformGroup(getPlatformGroup("Duplicate Group")).persist();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () -> {
              platformGroupService.createPlatformGroup("Duplicate Group", "desc");
              entityManager.flush();
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  // -- READ --

  @Test
  void given_existingGroup_should_findPlatformGroupById() {
    // -- ARRANGE --
    PlatformGroup group =
        platformGroupComposer.forPlatformGroup(getPlatformGroup()).persist().get();

    // -- ACT --
    PlatformGroup found = platformGroupService.findById(group.getId());

    // -- ASSERT --
    assertThat(found.getName()).isEqualTo(PLATFORM_GROUP_NAME);
  }

  @Test
  void given_unknownId_should_failToFindPlatformGroup() {
    assertThatThrownBy(() -> platformGroupService.findById("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  // -- SEARCH --

  @Test
  void given_multipleGroups_should_searchPlatformGroups() {
    // -- ARRANGE --
    platformGroupComposer.forPlatformGroup(getPlatformGroup("Group A")).persist();
    platformGroupComposer.forPlatformGroup(getPlatformGroup("Group B")).persist();

    SearchPaginationInput searchInput = new SearchPaginationInput();
    searchInput.setPage(0);
    searchInput.setSize(10);

    // -- ACT --
    Page<PlatformGroupOutput> result = platformGroupService.search(searchInput);

    // -- ASSERT --
    assertThat(result.getContent())
        .extracting(PlatformGroupOutput::name)
        .contains("Group A", "Group B");
  }

  // -- UPDATE --

  @Test
  void given_existingGroup_should_updatePlatformGroup() {
    // -- ARRANGE --
    PlatformGroup existing =
        platformGroupComposer.forPlatformGroup(getPlatformGroup("Initial Group")).persist().get();

    // -- ACT --
    PlatformGroup updated =
        platformGroupService.updatePlatformGroup(
            existing.getId(), "Updated Group", "Updated description");

    // -- ASSERT --
    assertThat(updated.getName()).isEqualTo("Updated Group");
    assertThat(updated.getDescription()).isEqualTo("Updated description");
  }

  @Test
  void given_existingGroupAndRole_should_updatePlatformGroupRoles() {
    // -- ARRANGE --
    PlatformGroup group =
        platformGroupComposer.forPlatformGroup(getPlatformGroup("Group for roles")).persist().get();
    PlatformRole role =
        platformRoleComposer.forPlatformRole(getPlatformRole("Role for group")).persist().get();
    entityManager.flush();

    // -- ACT --
    platformGroupService.updatePlatformGroupRoles(group.getId(), List.of(role.getId()));
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    Set<String> roleIds = platformGroupRepository.findPlatformRoleIdsByGroupId(group.getId());
    assertThat(roleIds).containsExactly(role.getId());
  }

  @Test
  @WithMockUser
  void given_existingGroupAndUser_should_updatePlatformGroupUsers() {
    // -- ARRANGE --
    PlatformGroup group =
        platformGroupComposer.forPlatformGroup(getPlatformGroup("Group for users")).persist().get();
    String userId = testUserHolder.get().getId();

    // -- ACT --
    platformGroupService.updatePlatformGroupUsers(group.getId(), List.of(userId));
    entityManager.flush();

    // -- ASSERT --
    List<String> userIds = platformGroupRepository.findUserIdsByGroupId(group.getId());
    assertThat(userIds).containsExactly(userId);
  }

  @Test
  void given_unknownId_should_failToUpdatePlatformGroup() {
    assertThatThrownBy(() -> platformGroupService.updatePlatformGroup("unknown", "name", "desc"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void given_unknownRoleId_should_failToUpdatePlatformGroupRoles() {
    // -- ARRANGE --
    PlatformGroup group =
        platformGroupComposer.forPlatformGroup(getPlatformGroup("Bad Group")).persist().get();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () ->
                platformGroupService.updatePlatformGroupRoles(
                    group.getId(), List.of("unknown-role-id")))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("PlatformRole");
  }

  @Test
  void given_unknownUserId_should_failToUpdatePlatformGroupUsers() {
    // -- ARRANGE --
    PlatformGroup group =
        platformGroupComposer.forPlatformGroup(getPlatformGroup("Bad Group")).persist().get();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () ->
                platformGroupService.updatePlatformGroupUsers(
                    group.getId(), List.of("unknown-user-id")))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("User");
  }

  // -- DELETE --

  @Test
  void given_existingGroup_should_deletePlatformGroup() {
    // -- ARRANGE --
    PlatformGroup group =
        platformGroupComposer.forPlatformGroup(getPlatformGroup("To delete")).persist().get();
    entityManager.flush();

    // -- ACT --
    platformGroupService.deletePlatformGroup(group.getId());
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    assertThatThrownBy(() -> platformGroupService.findById(group.getId()))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void given_unknownId_should_failToDeletePlatformGroup() {
    assertThatThrownBy(() -> platformGroupService.deletePlatformGroup("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }
}
