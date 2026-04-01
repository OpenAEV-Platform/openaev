package io.openaev.service.platform.roles;

import static io.openaev.utils.fixtures.platform.PlatformRoleFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.api.platform.roles.PlatformRoleOutput;
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

  // -- CREATE --

  @Test
  void given_validInput_should_createPlatformRole() {
    // -- ACT --
    PlatformRole created =
        platformRoleService.createPlatformRole(
            PLATFORM_ROLE_NAME, PLATFORM_ROLE_DESCRIPTION, PLATFORM_ROLE_CAPABILITIES);

    // -- ASSERT --
    assertThat(created.getId()).isNotNull();
    assertThat(created.getName()).isEqualTo(PLATFORM_ROLE_NAME);
    assertThat(created.getDescription()).isEqualTo(PLATFORM_ROLE_DESCRIPTION);
    assertThat(created.getCapabilities()).contains(Capability.ACCESS_PLATFORM_SETTINGS);
  }

  @Test
  void given_duplicateName_should_failToCreatePlatformRole() {
    // -- ARRANGE --
    platformRoleComposer.forPlatformRole(getPlatformRole("Duplicate Role")).persist();

    // -- ACT & ASSERT --
    assertThatThrownBy(
            () -> {
              platformRoleService.createPlatformRole(
                  "Duplicate Role", "desc", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));
              entityManager.flush();
            })
        .isInstanceOf(ConstraintViolationException.class);
  }

  // -- READ --

  @Test
  void given_existingRole_should_findPlatformRoleById() {
    // -- ARRANGE --
    PlatformRole role = platformRoleComposer.forPlatformRole(getPlatformRole()).persist().get();

    // -- ACT --
    PlatformRole found = platformRoleService.findById(role.getId());

    // -- ASSERT --
    assertThat(found.getName()).isEqualTo(PLATFORM_ROLE_NAME);
    assertThat(found.getCapabilities()).contains(Capability.ACCESS_PLATFORM_SETTINGS);
  }

  @Test
  void given_unknownId_should_failToFindPlatformRole() {
    assertThatThrownBy(() -> platformRoleService.findById("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  // -- SEARCH --

  @Test
  void given_multipleRoles_should_searchPlatformRoles() {
    // -- ARRANGE --
    platformRoleComposer.forPlatformRole(getPlatformRole("Role A")).persist();
    platformRoleComposer.forPlatformRole(getPlatformRole("Role B")).persist();

    SearchPaginationInput searchInput = new SearchPaginationInput();
    searchInput.setPage(0);
    searchInput.setSize(10);

    // -- ACT --
    Page<PlatformRoleOutput> result = platformRoleService.search(searchInput);

    // -- ASSERT --
    assertThat(result.getContent())
        .extracting(PlatformRoleOutput::name)
        .contains("Role A", "Role B");
  }

  // -- UPDATE --

  @Test
  void given_existingRole_should_updatePlatformRole() {
    // -- ARRANGE --
    PlatformRole existing =
        platformRoleComposer.forPlatformRole(getPlatformRole("Initial Role")).persist().get();

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

  // -- DELETE --

  @Test
  void given_existingRole_should_deletePlatformRole() {
    // -- ARRANGE --
    PlatformRole role =
        platformRoleComposer.forPlatformRole(getPlatformRole("Role to delete")).persist().get();
    entityManager.flush();

    // -- ACT --
    platformRoleService.deletePlatformRole(role.getId());
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    assertThatThrownBy(() -> platformRoleService.findById(role.getId()))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void given_unknownId_should_failToDeletePlatformRole() {
    assertThatThrownBy(() -> platformRoleService.deletePlatformRole("unknown"))
        .isInstanceOf(EntityNotFoundException.class);
  }
}
