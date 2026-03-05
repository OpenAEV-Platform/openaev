package io.openaev.service.platform.groups;

import static io.openaev.utils.fixtures.platform.PlatformGroupFixture.*;
import static io.openaev.utils.fixtures.platform.PlatformRoleFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.PlatformGroup;
import io.openaev.database.model.PlatformRole;
import io.openaev.database.repository.PlatformGroupRepository;
import io.openaev.service.platform.roles.PlatformRoleService;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
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
  @Autowired private PlatformRoleService platformRoleService;
  @Autowired private PlatformGroupRepository platformGroupRepository;
  @Autowired protected EntityManager entityManager;

  // -- CREATE --

  @Test
  void should_create_platform_group() {
    // -- ACT --
    PlatformGroup created =
        platformGroupService.createPlatformGroup(
            PLATFORM_GROUP_NAME, PLATFORM_GROUP_DESCRIPTION);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo(PLATFORM_GROUP_NAME);
    assertThat(created.getDescription()).isEqualTo(PLATFORM_GROUP_DESCRIPTION);
  }

  @Test
  void should_create_platform_group_with_roles() {
    // -- ARRANGE --
    PlatformRole role =
        platformRoleService.createPlatformRole(
            "Test Role", "desc", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));
    entityManager.flush();

    // -- ACT --
    PlatformGroup created =
        platformGroupService.createPlatformGroup("Group with roles", "desc");
    platformGroupService.updatePlatformGroupRoles(created.getId(), List.of(role.getId()));
    entityManager.flush();

    // -- ASSERT --
    Set<String> roleIds = platformGroupRepository.findPlatformRoleIdsByGroupId(created.getId());
    assertThat(roleIds).containsExactly(role.getId());
  }

  @Test
  void should_create_platform_group_with_users() {
    // -- ARRANGE --
    String userId = testUserHolder.get().getId();

    // -- ACT --
    PlatformGroup created =
        platformGroupService.createPlatformGroup("Group with users", "desc");
    platformGroupService.updatePlatformGroupUsers(created.getId(), List.of(userId));
    entityManager.flush();

    // -- ASSERT --
    List<String> userIds = platformGroupRepository.findUserIdsByGroupId(created.getId());
    assertThat(userIds).containsExactly(userId);
  }

  @Test
  void should_fail_when_creating_group_with_duplicate_name() {
    // -- ARRANGE --
    PlatformGroup existing = getPlatformGroup("Duplicate Group");
    platformGroupComposer.forPlatformGroup(existing).persist();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () -> {
              platformGroupService.createPlatformGroup("Duplicate Group", "desc");
              entityManager.flush();
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void should_fail_when_updating_roles_with_unknown_role() {
    // -- ARRANGE --
    PlatformGroup group = getPlatformGroup("Bad Group");
    platformGroupComposer.forPlatformGroup(group).persist();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () ->
                platformGroupService.updatePlatformGroupRoles(
                    group.getId(), List.of("unknown-role-id")))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("PlatformRole");
  }

  @Test
  void should_fail_when_updating_users_with_unknown_user() {
    // -- ARRANGE --
    PlatformGroup group = getPlatformGroup("Bad Group");
    platformGroupComposer.forPlatformGroup(group).persist();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () ->
                platformGroupService.updatePlatformGroupUsers(
                    group.getId(), List.of("unknown-user-id")))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("User");
  }

  // -- READ --

  @Test
  void should_find_platform_group_by_id() {
    // -- ARRANGE --
    PlatformGroup group = getPlatformGroup();
    platformGroupComposer.forPlatformGroup(group).persist();

    // -- ACT --
    PlatformGroup found = platformGroupService.findById(group.getId());

    // -- ASSERT --
    assertThat(found.getName()).isEqualTo(PLATFORM_GROUP_NAME);
  }

  @Test
  void should_fail_when_group_does_not_exist() {
    assertThatThrownBy(() -> platformGroupService.findById("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void should_search_platform_groups() {
    // -- ARRANGE --
    platformGroupComposer.forPlatformGroup(getPlatformGroup("Group A")).persist();
    platformGroupComposer.forPlatformGroup(getPlatformGroup("Group B")).persist();

    SearchPaginationInput searchInput = new SearchPaginationInput();
    searchInput.setPage(0);
    searchInput.setSize(10);

    // -- ACT --
    Page<PlatformGroup> result = platformGroupService.search(searchInput);

    // -- ASSERT --
    assertThat(result.getContent())
        .extracting(PlatformGroup::getName)
        .contains("Group A", "Group B");
  }

  // -- UPDATE --

  @Test
  void should_update_platform_group() {
    // -- ARRANGE --
    PlatformGroup existing = getPlatformGroup("Initial Group");
    platformGroupComposer.forPlatformGroup(existing).persist();

    // -- ACT --
    PlatformGroup updated =
        platformGroupService.updatePlatformGroup(
            existing.getId(), "Updated Group", "Updated description");

    // -- ASSERT --
    assertThat(updated.getName()).isEqualTo("Updated Group");
    assertThat(updated.getDescription()).isEqualTo("Updated description");
  }

  @Test
  void should_update_platform_group_roles() {
    // -- ARRANGE --
    PlatformGroup existing = getPlatformGroup("Group to update roles");
    platformGroupComposer.forPlatformGroup(existing).persist();

    PlatformRole role =
        platformRoleService.createPlatformRole(
            "Role for update", "desc", Set.of(Capability.ACCESS_TENANTS));
    entityManager.flush();

    // -- ACT --
    platformGroupService.updatePlatformGroupRoles(existing.getId(), List.of(role.getId()));
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    Set<String> roleIds = platformGroupRepository.findPlatformRoleIdsByGroupId(existing.getId());
    assertThat(roleIds).containsExactly(role.getId());
  }

  @Test
  void should_fail_when_updating_non_existent_group() {
    assertThatThrownBy(
            () ->
                platformGroupService.updatePlatformGroup("unknown", "name", "desc"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  // -- DELETE --

  @Test
  void should_delete_platform_group() {
    // -- ARRANGE --
    PlatformGroup created =
        platformGroupService.createPlatformGroup("To delete", "desc");
    entityManager.flush();

    // -- ACT --
    platformGroupService.deletePlatformGroup(created.getId());
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    assertThatThrownBy(() -> platformGroupService.findById(created.getId()))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void should_fail_when_deleting_non_existent_group() {
    assertThatThrownBy(() -> platformGroupService.deletePlatformGroup("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }
}
