package io.openaev.rest.role;

import static io.openaev.api.platform.roles.PlatformRoleApi.PLATFORM_ROLES_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.FlushModeType;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * With {@code roles} active, a platform role (no tenant) is writable only in a platform-write
 * transaction, and the three platform role endpoints are the ones that open it. The fixtures of the
 * other platform role tests mark the test transaction themselves, which would hide a missing marker
 * on an endpoint: this class clears it before each request, so the endpoint's own marker is the
 * only thing that lets the write through.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Platform role API with roles active")
class PlatformRoleApiPlatformWriteTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    // The production list with roles on it: the test holds before and after roles joins that list
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            String.join(
                ",",
                Stream.concat(ProductionActiveTables.names().stream(), Stream.of("roles"))
                    .distinct()
                    .toList()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PlatformRoleComposer platformRoleComposer;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  /** Persists a platform role as a fixture, then leaves the transaction unmarked again. */
  private Role platformRoleWithNoMarker(String name) {
    Role role =
        platformRoleComposer
            .forPlatformRole(PlatformRoleFixture.getPlatformRole(name))
            .persist()
            .get();
    entityManager.flush();
    clearPlatformWriteMarker();
    entityManager.clear();
    return role;
  }

  private void clearPlatformWriteMarker() {
    entityManager
        .createNativeQuery("SELECT set_config('app.platform_write', '', true)")
        .setFlushMode(FlushModeType.COMMIT)
        .getResultList();
  }

  private RoleInput input(String name) {
    return new RoleInput(name, "A description", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));
  }

  @Nested
  @DisplayName("platform role writes")
  class PlatformRoleWrites {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_anUnmarkedTransaction_should_createAPlatformRoleThroughTheEndpoint")
    void given_anUnmarkedTransaction_should_createAPlatformRoleThroughTheEndpoint()
        throws Exception {
      // Arrange
      clearPlatformWriteMarker();

      // Act
      mvc.perform(
              post(PLATFORM_ROLES_URI)
                  .content(asJsonString(input("CreatedPlatformRole")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isCreated());

      // Assert: the INSERT of a tenant_id IS NULL row reaches the database
      entityManager.flush();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_anUnmarkedTransaction_should_updateAPlatformRoleThroughTheEndpoint")
    void given_anUnmarkedTransaction_should_updateAPlatformRoleThroughTheEndpoint()
        throws Exception {
      // Arrange
      Role role = platformRoleWithNoMarker("RoleToUpdate");

      // Act
      mvc.perform(
              put(PLATFORM_ROLES_URI + "/" + role.getId())
                  .content(asJsonString(input("UpdatedPlatformRole")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert: the UPDATE of a tenant_id IS NULL row reaches the database
      entityManager.flush();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_anUnmarkedTransaction_should_deleteAPlatformRoleThroughTheEndpoint")
    void given_anUnmarkedTransaction_should_deleteAPlatformRoleThroughTheEndpoint()
        throws Exception {
      // Arrange
      Role role = platformRoleWithNoMarker("RoleToDelete");

      // Act
      mvc.perform(
              delete(PLATFORM_ROLES_URI + "/" + role.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNoContent());

      // Assert: the DELETE of a tenant_id IS NULL row reached the database
      entityManager.flush();
      entityManager.clear();
      assertThat(roleRepository.findById(role.getId())).isEmpty();
    }
  }
}
