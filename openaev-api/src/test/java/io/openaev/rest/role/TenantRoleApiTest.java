package io.openaev.rest.role;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tenant Role API")
public class TenantRoleApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private PlatformRoleComposer platformRoleComposer;

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should create a tenant role")
    void given_manageTenantSettings_should_createRole() throws Exception {
      // -------- Arrange --------
      RoleInput input =
          RoleInput.builder()
              .name("Analyst")
              .capabilities(Set.of(Capability.ACCESS_ASSETS, Capability.ACCESS_CHALLENGES))
              .build();

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri("/api/tenants/{tenantId}/roles"))
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertNotNull(JsonPath.read(response, "$.role_id"));
      assertEquals("Analyst", JsonPath.read(response, "$.role_name"));
      List<String> caps = JsonPath.read(response, "$.role_capabilities");
      assertTrue(caps.contains(Capability.ACCESS_ASSETS.name()));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS only, should be forbidden to create")
    void given_accessTenantSettings_should_forbidCreate() throws Exception {
      // -------- Arrange --------
      RoleInput input =
          RoleInput.builder()
              .name("Forbidden")
              .capabilities(Set.of(Capability.ACCESS_ASSETS))
              .build();

      // -------- Act & Assert --------
      mvc.perform(
              post(tenantUri("/api/tenants/{tenantId}/roles"))
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
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS, should find a tenant role by ID")
    void given_accessTenantSettings_should_findRoleById() throws Exception {
      // -------- Arrange --------
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("Reader", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

      // -------- Act --------
      String response =
          mvc.perform(
                  get(tenantUri("/api/tenants/{tenantId}/roles/") + role.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(role.getId(), JsonPath.read(response, "$.role_id"));
      assertEquals("Reader", JsonPath.read(response, "$.role_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS, should return 404 for nonexistent role")
    void given_accessTenantSettings_should_return404() throws Exception {
      mvc.perform(
              get(tenantUri("/api/tenants/{tenantId}/roles/") + "nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS, should list all tenant roles")
    void given_accessTenantSettings_should_listRoles() throws Exception {
      // -------- Arrange --------
      tenantRoleComposer
          .forRole(TenantRoleFixture.getRole("RoleA", Set.of(Capability.ACCESS_ASSETS)))
          .persist();
      tenantRoleComposer
          .forRole(TenantRoleFixture.getRole("RoleB", Set.of(Capability.ACCESS_CHALLENGES)))
          .persist();

      // -------- Act --------
      String response =
          mvc.perform(
                  get(tenantUri("/api/tenants/{tenantId}/roles"))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<Map<String, Object>> roles = JsonPath.read(response, "$");
      assertTrue(roles.size() >= 2);
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS, should search tenant roles with pagination")
    void given_accessTenantSettings_should_searchRoles() throws Exception {
      // -------- Arrange --------
      tenantRoleComposer
          .forRole(TenantRoleFixture.getRole("Search1", Set.of(Capability.ACCESS_ASSETS)))
          .persist();
      tenantRoleComposer
          .forRole(TenantRoleFixture.getRole("Search2", Set.of(Capability.ACCESS_ASSETS)))
          .persist();
      tenantRoleComposer
          .forRole(TenantRoleFixture.getRole("Search3", Set.of(Capability.ACCESS_ASSETS)))
          .persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(2);
      input.setPage(0);

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri("/api/tenants/{tenantId}/roles/search"))
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(Integer.valueOf(2), JsonPath.read(response, "$.numberOfElements"));
    }

    @Test
    @WithMockUser
    @DisplayName("Given no capabilities, should be forbidden to read")
    void given_noCapabilities_should_forbidRead() throws Exception {
      mvc.perform(
              get(tenantUri("/api/tenants/{tenantId}/roles"))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should update a tenant role")
    void given_manageTenantSettings_should_updateRole() throws Exception {
      // -------- Arrange --------
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("OldName", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

      RoleInput input =
          RoleInput.builder()
              .name("NewName")
              .capabilities(Set.of(Capability.ACCESS_CHALLENGES))
              .build();

      // -------- Act --------
      String response =
          mvc.perform(
                  put(tenantUri("/api/tenants/{tenantId}/roles/") + role.getId())
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals("NewName", JsonPath.read(response, "$.role_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should return 404 when updating nonexistent role")
    void given_manageTenantSettings_should_return404() throws Exception {
      // -------- Arrange --------
      RoleInput input =
          RoleInput.builder().name("test").capabilities(Set.of(Capability.ACCESS_ASSETS)).build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri("/api/tenants/{tenantId}/roles/") + "nonexistent")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS only, should be forbidden to update")
    void given_accessTenantSettings_should_forbidUpdate() throws Exception {
      // -------- Arrange --------
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("NotUpdatable", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

      RoleInput input =
          RoleInput.builder()
              .name("Forbidden")
              .capabilities(Set.of(Capability.ACCESS_CHALLENGES))
              .build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri("/api/tenants/{tenantId}/roles/") + role.getId())
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
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_SETTINGS})
    @DisplayName("Given DELETE_TENANT_SETTINGS, should delete a tenant role")
    void given_deleteTenantSettings_should_deleteRole() throws Exception {
      // -------- Arrange --------
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("ToDelete", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

      // -------- Act --------
      mvc.perform(
              delete(tenantUri("/api/tenants/{tenantId}/roles/") + role.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -------- Assert --------
      assertFalse(roleRepository.existsById(role.getId()));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_SETTINGS})
    @DisplayName("Given DELETE_TENANT_SETTINGS, should return 404 when deleting nonexistent role")
    void given_deleteTenantSettings_should_return404() throws Exception {
      mvc.perform(
              delete(tenantUri("/api/tenants/{tenantId}/roles/") + "nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS only, should be forbidden to delete")
    void given_manageTenantSettings_should_forbidDelete() throws Exception {
      // -------- Arrange --------
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("NotDeletable", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

      // -------- Act & Assert --------
      mvc.perform(
              delete(tenantUri("/api/tenants/{tenantId}/roles/") + role.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
  class TenantIsolation {

    @Test
    @DisplayName("Platform roles should not appear in tenant role list")
    void given_platformRoleExists_should_notAppearInTenantList() throws Exception {
      // -------- Arrange --------
      platformRoleComposer
          .forPlatformRole(PlatformRoleFixture.getPlatformRole("PlatformOnly"))
          .persist();

      // -------- Act --------
      String response =
          mvc.perform(
                  get(tenantUri("/api/tenants/{tenantId}/roles"))
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<Map<String, Object>> roles = JsonPath.read(response, "$");
      Set<String> roleNames =
          roles.stream().map(r -> (String) r.get("role_name")).collect(Collectors.toSet());
      assertFalse(roleNames.contains("PlatformOnly"));
    }

    @Test
    @DisplayName("Tenant role search should not return platform roles")
    void given_platformRoleExists_should_notAppearInTenantSearch() throws Exception {
      // -------- Arrange --------
      platformRoleComposer
          .forPlatformRole(PlatformRoleFixture.getPlatformRole("SearchPlatform"))
          .persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(100);
      input.setPage(0);
      input.setTextSearch("SearchPlatform");

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri("/api/tenants/{tenantId}/roles/search"))
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(Integer.valueOf(0), JsonPath.read(response, "$.totalElements"));
    }
  }
}
