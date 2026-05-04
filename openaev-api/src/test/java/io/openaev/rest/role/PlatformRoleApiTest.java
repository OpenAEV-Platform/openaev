package io.openaev.rest.role;

import static io.openaev.api.platform.roles.PlatformRoleApi.PLATFORM_ROLES_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.platform.roles.PlatformRoleInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Platform Role API")
public class PlatformRoleApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PlatformRoleComposer platformRoleComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES, should create a platform role")
    void given_managePlatform_should_createRole() throws Exception {
      // -------- Arrange --------
      PlatformRoleInput input =
          new PlatformRoleInput(
              "NewPlatformRole", "A description", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_ROLES_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertNotNull(JsonPath.read(response, "$.platform_role_id"));
      assertEquals("NewPlatformRole", JsonPath.read(response, "$.platform_role_name"));
      assertEquals("A description", JsonPath.read(response, "$.platform_role_description"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES only, should be forbidden to create")
    void given_accessPlatform_should_forbidCreate() throws Exception {
      // -------- Arrange --------
      PlatformRoleInput input =
          new PlatformRoleInput("Forbidden", "desc", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));

      // -------- Act & Assert --------
      mvc.perform(
              post(PLATFORM_ROLES_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Read")
  class Read {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should find platform role by ID")
    void given_accessPlatform_should_findRoleById() throws Exception {
      // -------- Arrange --------
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("FindMeRole"))
              .persist()
              .get();

      // -------- Act --------
      String response =
          mvc.perform(
                  get(PLATFORM_ROLES_URI + "/" + role.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(role.getId(), JsonPath.read(response, "$.platform_role_id"));
      assertEquals("FindMeRole", JsonPath.read(response, "$.platform_role_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should return capabilities")
    void given_accessPlatform_should_returnCapabilities() throws Exception {
      // -------- Arrange --------
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("CapaRole"))
              .persist()
              .get();

      // -------- Act --------
      String response =
          mvc.perform(
                  get(PLATFORM_ROLES_URI + "/" + role.getId() + "/capabilities")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> caps = JsonPath.read(response, "$");
      assertTrue(caps.contains(Capability.ACCESS_PLATFORM_SETTINGS.name()));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should search platform roles with pagination")
    void given_accessPlatform_should_searchRoles() throws Exception {
      // -------- Arrange --------
      platformRoleComposer
          .forPlatformRole(PlatformRoleFixture.getPlatformRole("PSearch1"))
          .persist();
      platformRoleComposer
          .forPlatformRole(PlatformRoleFixture.getPlatformRole("PSearch2"))
          .persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(10);
      input.setPage(0);

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_ROLES_URI + "/search")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      int total = JsonPath.read(response, "$.totalElements");
      assertTrue(total >= 2);
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should find platform roles by IDs")
    void given_accessPlatform_should_findByIds() throws Exception {
      // -------- Arrange --------
      Role role1 =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("FindById1"))
              .persist()
              .get();
      Role role2 =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("FindById2"))
              .persist()
              .get();

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_ROLES_URI + "/find")
                      .content(asJsonString(List.of(role1.getId(), role2.getId())))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<Map<String, Object>> roles = JsonPath.read(response, "$");
      assertEquals(2, roles.size());
    }

    @Test
    @WithMockUser
    @DisplayName("Given no capabilities, should be forbidden to read")
    void given_noCapabilities_should_forbidRead() throws Exception {
      // -------- Arrange --------
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("NotReadable"))
              .persist()
              .get();

      // -------- Act & Assert --------
      mvc.perform(
              get(PLATFORM_ROLES_URI + "/" + role.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES, should update a platform role")
    void given_managePlatform_should_updateRole() throws Exception {
      // -------- Arrange --------
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("OldRoleName"))
              .persist()
              .get();

      PlatformRoleInput input =
          new PlatformRoleInput(
              "UpdatedRoleName", "Updated desc", Set.of(Capability.MANAGE_PLATFORM_SETTINGS));

      // -------- Act --------
      String response =
          mvc.perform(
                  put(PLATFORM_ROLES_URI + "/" + role.getId())
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals("UpdatedRoleName", JsonPath.read(response, "$.platform_role_name"));
      assertEquals("Updated desc", JsonPath.read(response, "$.platform_role_description"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES only, should be forbidden to update")
    void given_accessPlatform_should_forbidUpdate() throws Exception {
      // -------- Arrange --------
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("NotUpdatable"))
              .persist()
              .get();

      PlatformRoleInput input =
          new PlatformRoleInput(
              "Forbidden", "forbidden", Set.of(Capability.ACCESS_PLATFORM_SETTINGS));

      // -------- Act & Assert --------
      mvc.perform(
              put(PLATFORM_ROLES_URI + "/" + role.getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given DELETE_PLATFORM_USERS_GROUPS_AND_ROLES, should delete a platform role")
    void given_deletePlatform_should_deleteRole() throws Exception {
      // -------- Arrange --------
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("ToDeleteRole"))
              .persist()
              .get();
      entityManager.flush();

      // -------- Act --------
      mvc.perform(
              delete(PLATFORM_ROLES_URI + "/" + role.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNoContent());

      // -------- Assert --------
      entityManager.clear();
      assertFalse(
          roleRepository.findById(role.getId()).filter(r -> r.getTenant() == null).isPresent());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given DELETE_PLATFORM_USERS_GROUPS_AND_ROLES, should return 404 when deleting nonexistent")
    void given_deletePlatform_should_return404() throws Exception {
      mvc.perform(
              delete(PLATFORM_ROLES_URI + "/nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES only, should be forbidden to delete")
    void given_managePlatform_should_forbidDelete() throws Exception {
      // -------- Arrange --------
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("NotDeletable"))
              .persist()
              .get();

      // -------- Act & Assert --------
      mvc.perform(
              delete(PLATFORM_ROLES_URI + "/" + role.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Isolation")
  @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
  class Isolation {

    @Test
    @DisplayName("Tenant roles should not appear in platform role search")
    void given_tenantRoleExists_should_notAppearInPlatformSearch() throws Exception {
      // -------- Arrange --------
      tenantRoleComposer.forRole(TenantRoleFixture.getRole("TenantOnlyRole")).persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(100);
      input.setPage(0);
      input.setTextSearch("TenantOnlyRole");

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_ROLES_URI + "/search")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(Integer.valueOf(0), JsonPath.read(response, "$.totalElements"));
    }
  }
}
