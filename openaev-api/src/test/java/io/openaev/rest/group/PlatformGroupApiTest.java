package io.openaev.rest.group;

import static io.openaev.api.groups.PlatformGroupApi.PLATFORM_GROUPS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.groups.PlatformGroupUpdateRolesInput;
import io.openaev.api.groups.PlatformGroupUpdateUsersInput;
import io.openaev.api.groups.dto.PlatformGroupInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.repository.GroupRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
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
@DisplayName("Platform Group API")
public class PlatformGroupApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private GroupRepository groupRepository;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private PlatformRoleComposer platformRoleComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES, should create a platform group")
    void given_managePlatform_should_createGroup() throws Exception {
      // -------- Arrange --------
      PlatformGroupInput input = new PlatformGroupInput("PlatformNewGroup", "A description", false);

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_GROUPS_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertNotNull(JsonPath.read(response, "$.platform_group_id"));
      assertEquals("PlatformNewGroup", JsonPath.read(response, "$.platform_group_name"));
      assertEquals("A description", JsonPath.read(response, "$.platform_group_description"));
      assertEquals(false, JsonPath.read(response, "$.group_default_user_assign"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES with default assign true, should create with flag")
    void given_managePlatform_should_createGroupWithDefaultAssign() throws Exception {
      // -------- Arrange --------
      PlatformGroupInput input =
          new PlatformGroupInput("AutoAssignGroup", "Auto assign desc", true);

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_GROUPS_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isCreated())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertNotNull(JsonPath.read(response, "$.platform_group_id"));
      assertEquals("AutoAssignGroup", JsonPath.read(response, "$.platform_group_name"));
      assertEquals(true, JsonPath.read(response, "$.group_default_user_assign"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES only, should be forbidden to create")
    void given_accessPlatform_should_forbidCreate() throws Exception {
      // -------- Arrange --------
      PlatformGroupInput input = new PlatformGroupInput("Forbidden", "desc", false);

      // -------- Act & Assert --------
      mvc.perform(
              post(PLATFORM_GROUPS_URI)
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
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should find platform group by ID")
    void given_accessPlatform_should_findGroupById() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("FindMe"))
              .persist()
              .get();

      // -------- Act --------
      String response =
          mvc.perform(
                  get(PLATFORM_GROUPS_URI + "/" + group.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(group.getId(), JsonPath.read(response, "$.platform_group_id"));
      assertEquals("FindMe", JsonPath.read(response, "$.platform_group_name"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should search platform groups with pagination")
    void given_accessPlatform_should_searchGroups() throws Exception {
      // -------- Arrange --------
      platformGroupComposer
          .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("PSearch1"))
          .persist();
      platformGroupComposer
          .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("PSearch2"))
          .persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(10);
      input.setPage(0);

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_GROUPS_URI + "/search")
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
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should return user IDs")
    void given_accessPlatform_should_returnUserIds() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("UsersGroup"))
              .persist()
              .get();

      // -------- Act & Assert --------
      mvc.perform(
              get(PLATFORM_GROUPS_URI + "/" + group.getId() + "/users")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES, should return platform role IDs")
    void given_accessPlatform_should_returnPlatformRoleIds() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("RolesGroup"))
              .persist()
              .get();

      // -------- Act & Assert --------
      mvc.perform(
              get(PLATFORM_GROUPS_URI + "/" + group.getId() + "/platform-roles")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Given no capabilities, should be forbidden to read")
    void given_noCapabilities_should_forbidRead() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("NotReadable"))
              .persist()
              .get();

      // -------- Act & Assert --------
      mvc.perform(
              get(PLATFORM_GROUPS_URI + "/" + group.getId())
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
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES, should update a platform group")
    void given_managePlatform_should_updateGroup() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("OldName"))
              .persist()
              .get();

      PlatformGroupInput input = new PlatformGroupInput("NewName", "New desc", false);

      // -------- Act --------
      String response =
          mvc.perform(
                  put(PLATFORM_GROUPS_URI + "/" + group.getId())
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals("NewName", JsonPath.read(response, "$.platform_group_name"));
      assertEquals("New desc", JsonPath.read(response, "$.platform_group_description"));
      assertEquals(false, JsonPath.read(response, "$.group_default_user_assign"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES, should toggle default assign on update")
    void given_managePlatform_should_toggleDefaultAssignOnUpdate() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("ToggleGroup"))
              .persist()
              .get();

      PlatformGroupInput inputEnable = new PlatformGroupInput("ToggleGroup", "desc", true);

      // -------- Act — enable --------
      String responseEnabled =
          mvc.perform(
                  put(PLATFORM_GROUPS_URI + "/" + group.getId())
                      .content(asJsonString(inputEnable))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert — enabled --------
      assertEquals(true, JsonPath.read(responseEnabled, "$.group_default_user_assign"));

      // -------- Act — disable --------
      PlatformGroupInput inputDisable = new PlatformGroupInput("ToggleGroup", "desc", false);

      String responseDisabled =
          mvc.perform(
                  put(PLATFORM_GROUPS_URI + "/" + group.getId())
                      .content(asJsonString(inputDisable))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert — disabled --------
      assertEquals(false, JsonPath.read(responseDisabled, "$.group_default_user_assign"));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES, should update platform group users")
    void given_managePlatform_should_updateGroupUsers() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("UserUpdateGroup"))
              .persist()
              .get();
      String userId = testUserHolder.get().getId();

      PlatformGroupUpdateUsersInput input = new PlatformGroupUpdateUsersInput(List.of(userId));

      // -------- Act --------
      String response =
          mvc.perform(
                  put(PLATFORM_GROUPS_URI + "/" + group.getId() + "/users")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> userIds = JsonPath.read(response, "$");
      assertTrue(userIds.contains(userId));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES, should update platform group roles")
    void given_managePlatform_should_updateGroupRoles() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("RoleUpdateGroup"))
              .persist()
              .get();
      Role role =
          platformRoleComposer
              .forPlatformRole(PlatformRoleFixture.getPlatformRole("AssignRole"))
              .persist()
              .get();

      PlatformGroupUpdateRolesInput input =
          new PlatformGroupUpdateRolesInput(List.of(role.getId()));

      // -------- Act --------
      String response =
          mvc.perform(
                  put(PLATFORM_GROUPS_URI + "/" + group.getId() + "/platform-roles")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> roleIds = JsonPath.read(response, "$");
      assertTrue(roleIds.contains(role.getId()));
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_PLATFORM_USERS_GROUPS_AND_ROLES only, should be forbidden to update")
    void given_accessPlatform_should_forbidUpdate() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("NotUpdatable"))
              .persist()
              .get();

      PlatformGroupInput input = new PlatformGroupInput("Forbidden", "forbidden", false);

      // -------- Act & Assert --------
      mvc.perform(
              put(PLATFORM_GROUPS_URI + "/" + group.getId())
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
    @DisplayName("Given DELETE_PLATFORM_USERS_GROUPS_AND_ROLES, should delete a platform group")
    void given_deletePlatform_should_deleteGroup() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("ToDelete"))
              .persist()
              .get();
      entityManager.flush();

      // -------- Act --------
      mvc.perform(
              delete(PLATFORM_GROUPS_URI + "/" + group.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNoContent());

      // -------- Assert --------
      entityManager.clear();
      assertFalse(
          groupRepository.findById(group.getId()).filter(g -> g.getTenant() == null).isPresent());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given DELETE_PLATFORM_USERS_GROUPS_AND_ROLES, should return 404 when deleting nonexistent")
    void given_deletePlatform_should_return404() throws Exception {
      mvc.perform(
              delete(PLATFORM_GROUPS_URI + "/nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_PLATFORM_USERS_GROUPS_AND_ROLES only, should be forbidden to delete")
    void given_managePlatform_should_forbidDelete() throws Exception {
      // -------- Arrange --------
      Group group =
          platformGroupComposer
              .forPlatformGroup(PlatformGroupFixture.getPlatformGroup("NotDeletable"))
              .persist()
              .get();

      // -------- Act & Assert --------
      mvc.perform(
              delete(PLATFORM_GROUPS_URI + "/" + group.getId())
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
    @DisplayName("Tenant groups should not appear in platform group search")
    void given_tenantGroupExists_should_notAppearInPlatformSearch() throws Exception {
      // -------- Arrange --------
      tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("TenantOnlyGroup")).persist();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(100);
      input.setPage(0);
      input.setTextSearch("TenantOnlyGroup");

      // -------- Act --------
      String response =
          mvc.perform(
                  post(PLATFORM_GROUPS_URI + "/search")
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
