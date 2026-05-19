package io.openaev.rest.group;

import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.GroupRepository;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tenant Group API")
public class TenantGroupApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should create a tenant group")
    void given_manageTenantSettings_should_createGroup() throws Exception {
      // -------- Arrange --------
      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("New Group");
      input.setDescription("Description");

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri(TENANT_GROUP_URI))
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertNotNull(JsonPath.read(response, "$.group_id"));
      assertEquals("New Group", JsonPath.read(response, "$.group_name"));
      assertEquals("Description", JsonPath.read(response, "$.group_description"));
      assertEquals(false, JsonPath.read(response, "$.group_default_user_assign"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS with default assign true, should create with flag")
    void given_manageTenantSettings_should_createGroupWithDefaultAssign() throws Exception {
      // -------- Arrange --------
      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("Auto Assign Group");
      input.setDescription("Auto");
      input.setDefaultUserAssignation(true);

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri(TENANT_GROUP_URI))
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertNotNull(JsonPath.read(response, "$.group_id"));
      assertEquals(true, JsonPath.read(response, "$.group_default_user_assign"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS only, should be forbidden to create")
    void given_accessTenantSettings_should_forbidCreate() throws Exception {
      // -------- Arrange --------
      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("Forbidden");
      input.setDescription("Description");

      // -------- Act & Assert --------
      mvc.perform(
              post(tenantUri(TENANT_GROUP_URI))
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
    @DisplayName("Given ACCESS_TENANT_SETTINGS, should find a tenant group by ID")
    void given_accessTenantSettings_should_findGroupById() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("FindMe")).persist().get();

      // -------- Act --------
      String response =
          mvc.perform(
                  get(tenantUri(TENANT_GROUP_URI) + "/" + group.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(group.getId(), JsonPath.read(response, "$.group_id"));
      assertEquals("FindMe", JsonPath.read(response, "$.group_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS, should return 404 for nonexistent group")
    void given_accessTenantSettings_should_return404() throws Exception {
      mvc.perform(
              get(tenantUri(TENANT_GROUP_URI) + "/nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS, should search tenant groups with pagination")
    void given_accessTenantSettings_should_searchGroups() throws Exception {
      // -------- Arrange --------
      tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("SearchG1")).persist();
      tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("SearchG2")).persist();
      tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("SearchG3")).persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(2);
      input.setPage(0);

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri(TENANT_GROUP_URI) + "/search")
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
              get(tenantUri(TENANT_GROUP_URI) + "/nonexistent")
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
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should update group information")
    void given_manageTenantSettings_should_updateGroupInformation() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("OldName")).persist().get();

      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("NewName");
      input.setDescription("NewDesc");

      // -------- Act --------
      String response =
          mvc.perform(
                  put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/information")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals("NewName", JsonPath.read(response, "$.group_name"));
      assertEquals("NewDesc", JsonPath.read(response, "$.group_description"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should toggle default assign on update")
    void given_manageTenantSettings_should_toggleDefaultAssignOnUpdate() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("ToggleTenant")).persist().get();

      TenantGroupCreateInput inputEnable = new TenantGroupCreateInput();
      inputEnable.setName("ToggleTenant");
      inputEnable.setDefaultUserAssignation(true);

      // -------- Act — enable --------
      String responseEnabled =
          mvc.perform(
                  put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/information")
                      .content(asJsonString(inputEnable))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert — enabled --------
      assertEquals(true, JsonPath.read(responseEnabled, "$.group_default_user_assign"));

      // -------- Act — disable --------
      TenantGroupCreateInput inputDisable = new TenantGroupCreateInput();
      inputDisable.setName("ToggleTenant");
      inputDisable.setDefaultUserAssignation(false);

      String responseDisabled =
          mvc.perform(
                  put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/information")
                      .content(asJsonString(inputDisable))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert — disabled --------
      assertEquals(false, JsonPath.read(responseDisabled, "$.group_default_user_assign"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should update group roles")
    void given_manageTenantSettings_should_updateGroupRoles() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("RoleGroup")).persist().get();
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("RoleForGroup", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

      GroupUpdateRolesInput input =
          GroupUpdateRolesInput.builder().roleIds(List.of(role.getId())).build();

      // -------- Act --------
      String response =
          mvc.perform(
                  put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/roles")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> roles = JsonPath.read(response, "$.group_roles");
      assertEquals(1, roles.size());
      assertEquals(role.getId(), roles.getFirst());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS, should update group users")
    void given_manageTenantSettings_should_updateGroupUsers() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("UserGroup")).persist().get();
      String userId = testUserHolder.get().getId();

      GroupUpdateUsersInput input = new GroupUpdateUsersInput();
      input.setUserIds(List.of(userId));

      // -------- Act --------
      String response =
          mvc.perform(
                  put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/users")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> users = JsonPath.read(response, "$.group_users");
      assertTrue(users.contains(userId));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Given ACCESS_TENANT_SETTINGS only, should be forbidden to update")
    void given_accessTenantSettings_should_forbidUpdate() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("NotUpdatable")).persist().get();

      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("Forbidden");
      input.setDescription("Forbidden");

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/information")
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
    @DisplayName("Given DELETE_TENANT_SETTINGS, should delete a tenant group")
    void given_deleteTenantSettings_should_deleteGroup() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("ToDelete")).persist().get();
      entityManager.flush();

      // -------- Act --------
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/" + group.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -------- Assert --------
      entityManager.flush();
      entityManager.clear();
      assertFalse(groupRepository.findById(group.getId()).isPresent());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_SETTINGS})
    @DisplayName("Given DELETE_TENANT_SETTINGS, should return 404 when deleting nonexistent group")
    void given_deleteTenantSettings_should_return404() throws Exception {
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given MANAGE_TENANT_SETTINGS only, should be forbidden to delete")
    void given_manageTenantSettings_should_forbidDelete() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("NotDeletable")).persist().get();

      // -------- Act & Assert --------
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/" + group.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  class TenantIsolation {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
    @DisplayName("Platform groups should not appear in tenant search")
    void given_platformGroupExists_should_notAppearInTenantSearch() throws Exception {
      // -------- Arrange --------
      platformGroupComposer
          .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("PlatformOnlyGroup"))
          .persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(100);
      input.setPage(0);
      input.setTextSearch("PlatformOnlyGroup");

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri(TENANT_GROUP_URI) + "/search")
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

    @Test
    @WithMockUser
    @DisplayName("Tenant group created in tenant X should NOT be readable from tenant Y")
    void given_groupInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TENANT_SETTINGS, Capability.ACCESS_TENANT_SETTINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_TENANT_SETTINGS));

      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("RLS Isolated Group");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/groups")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String groupId = JsonPath.read(createResponse, "$.group_id");

      // -------- Act — read from tenant Y (expect 403 or 404 — both mean isolation works) --------
      int status =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/groups/" + groupId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      assertTrue(
          status == 403 || status == 404,
          "Expected 403 or 404 but got " + status + " — cross-tenant group read was NOT blocked");
    }
  }
}
