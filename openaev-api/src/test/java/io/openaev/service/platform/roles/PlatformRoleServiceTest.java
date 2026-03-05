package io.openaev.service.platform.roles;

import static io.openaev.utils.fixtures.platform.PlatformRoleFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.PlatformRole;
import io.openaev.utils.fixtures.platform.PlatformRoleComposer;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformRoleServiceTest extends IntegrationTest {

  @Autowired private PlatformRoleService platformRoleService;
  @Autowired private PlatformRoleComposer platformRoleComposer;
  @Autowired protected EntityManager entityManager;

  @Test
  void should_create_and_find_platform_role() {
    // -- ARRANGE --
    PlatformRole role = getPlatformRole();

    // -- ACT --
    PlatformRole created =
        platformRoleService.createPlatformRole(
            role.getName(), role.getDescription(), role.getCapabilities());

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo(PLATFORM_ROLE_NAME);
    assertThat(created.getCapabilities()).contains(Capability.ACCESS_PLATFORM_SETTINGS);

    PlatformRole found = platformRoleService.findById(created.getId());
    assertThat(found.getName()).isEqualTo(PLATFORM_ROLE_NAME);
  }

  @Test
  void should_search_platform_roles() {
    // -- ARRANGE --
    PlatformRole roleA = getPlatformRole("Role A");
    PlatformRole roleB = getPlatformRole("Role B");
    platformRoleComposer.forPlatformRole(roleA).persist();
    platformRoleComposer.forPlatformRole(roleB).persist();

    SearchPaginationInput searchInput = new SearchPaginationInput();
    searchInput.setPage(0);
    searchInput.setSize(10);

    // -- ACT --
    Page<PlatformRole> result = platformRoleService.search(searchInput);

    // -- ASSERT --
    assertThat(result.getContent()).extracting(PlatformRole::getName).contains("Role A", "Role B");
  }

  @Test
  void should_update_platform_role() {
    // -- ARRANGE --
    PlatformRole existing = getPlatformRole("Initial Role");
    platformRoleComposer.forPlatformRole(existing).persist();

    // -- ACT --
    PlatformRole updated =
        platformRoleService.updatePlatformRole(
            existing.getId(),
            "Updated Role",
            "Updated description",
            Set.of(Capability.MANAGE_PLATFORM_SETTINGS));

    // -- ASSERT --
    assertThat(updated.getName()).isEqualTo("Updated Role");
    assertThat(updated.getDescription()).isEqualTo("Updated description");
    assertThat(updated.getCapabilities()).contains(Capability.MANAGE_PLATFORM_SETTINGS);
    // MANAGE inherits ACCESS
    assertThat(updated.getCapabilities()).contains(Capability.ACCESS_PLATFORM_SETTINGS);
  }

  @Test
  void should_delete_platform_role() {
    // -- ARRANGE --
    PlatformRole created =
        platformRoleService.createPlatformRole(
            "Role to delete", "desc", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));
    entityManager.flush();

    // -- ACT --
    platformRoleService.deletePlatformRole(created.getId());
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    assertThatThrownBy(() -> platformRoleService.findById(created.getId()))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void should_fail_when_platform_role_does_not_exist() {
    assertThatThrownBy(() -> platformRoleService.findById("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void should_fail_when_deleting_non_existent_platform_role() {
    assertThatThrownBy(() -> platformRoleService.deletePlatformRole("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void should_fail_when_creating_platform_role_with_existing_name() {
    // -- ARRANGE --
    PlatformRole role1 = getPlatformRole("Duplicate Role");
    platformRoleComposer.forPlatformRole(role1).persist();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () -> {
              platformRoleService.createPlatformRole(
                  "Duplicate Role", "desc", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));
              entityManager.flush();
            })
        .isInstanceOf(ConstraintViolationException.class);
  }
}
