package io.openaev.rest.group;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.groups.dto.GroupUpdateMarkingsInput;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.MarkingDefinitionFixture;
import io.openaev.utils.fixtures.PlatformRoleFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tenant Group API")
public class TenantGroupApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private PlatformRoleComposer platformRoleComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager markingClearanceCacheManager;
  @MockitoBean private PreviewFeatureService previewFeatureService;

  @BeforeEach
  void enablePreviewFeatures() {
    when(previewFeatureService.isFeatureEnabled(any())).thenReturn(true);
  }

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should create a tenant group")
    void given_manageTenantUsersGroupsAndRoles_should_createGroup() throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES with default assign true, should create with flag")
    void given_manageTenantUsersGroupsAndRoles_should_createGroupWithDefaultAssign()
        throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_TENANT_USERS_GROUPS_AND_ROLES only, should be forbidden to create")
    void given_accessTenantUsersGroupsAndRoles_should_forbidCreate() throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_TENANT_USERS_GROUPS_AND_ROLES, should find a tenant group by ID")
    void given_accessTenantUsersGroupsAndRoles_should_findGroupById() throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given ACCESS_TENANT_USERS_GROUPS_AND_ROLES, should return 404 for nonexistent group")
    void given_accessTenantUsersGroupsAndRoles_should_return404() throws Exception {
      mvc.perform(
              get(tenantUri(TENANT_GROUP_URI) + "/nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given ACCESS_TENANT_USERS_GROUPS_AND_ROLES, should search tenant groups with pagination")
    void given_accessTenantUsersGroupsAndRoles_should_searchGroups() throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should update group information")
    void given_manageTenantUsersGroupsAndRoles_should_updateGroupInformation() throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should forbid making a group with unowned role capabilities the default one")
    void
        given_manageTenantUsersGroupsAndRoles_should_forbidDefaultAssignOnGroupWithUnownedCapabilities()
            throws Exception {
      // -------- Arrange --------
      // Enrolling every future user into that group would hand out ACCESS_ASSETS indirectly.
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("DefaultAssignForbidden"))
              .withRole(
                  tenantRoleComposer.forRole(
                      TenantRoleFixture.getRole(
                          "RoleForDefaultAssign", Set.of(Capability.ACCESS_ASSETS))))
              .persist()
              .get();

      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("DefaultAssignForbidden");
      input.setDefaultUserAssignation(true);

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/information")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should still rename a default group carrying unowned capabilities")
    void given_manageTenantUsersGroupsAndRoles_should_allowRenamingAlreadyDefaultGroup()
        throws Exception {
      // -------- Arrange --------
      // Only the transition to default is an escalation; a group already flagged stays editable.
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("AlreadyDefault"))
              .withRole(
                  tenantRoleComposer.forRole(
                      TenantRoleFixture.getRole(
                          "RoleForAlreadyDefault", Set.of(Capability.ACCESS_ASSETS))))
              .persist()
              .get();
      group.setDefaultUserAssignation(true);
      groupRepository.save(group);

      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("AlreadyDefaultRenamed");
      input.setDefaultUserAssignation(true);

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/information")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should toggle default assign on update")
    void given_manageTenantUsersGroupsAndRoles_should_toggleDefaultAssignOnUpdate()
        throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should update group roles")
    void given_manageTenantUsersGroupsAndRoles_should_updateGroupRoles() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("RoleGroup")).persist().get();
      Role role =
          tenantRoleComposer
              .forRole(
                  TenantRoleFixture.getRole(
                      "RoleForGroup", Set.of(Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES)))
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
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should forbid assigning role with unowned capabilities")
    void
        given_manageTenantUsersGroupsAndRoles_should_forbidUpdateGroupRolesWithUnownedCapabilities()
            throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("RoleGroupForbidden"))
              .persist()
              .get();
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("RoleForGroup", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();
      GroupUpdateRolesInput input =
          GroupUpdateRolesInput.builder().roleIds(List.of(role.getId())).build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/roles")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given a platform role, should answer not found and link nothing")
    void given_platformRole_should_answerNotFoundOnGroupRoles() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("GroupWithPlatformRole"))
              .persist()
              .get();
      // No capability: the capability check has nothing to refuse, only the scope of the role does
      Role platformRole =
          platformRoleComposer
              .forPlatformRole(
                  PlatformRoleFixture.getPlatformRole("PlatformRoleOnTenantGroup", Set.of()))
              .persist()
              .get();

      // -------- Act & Assert --------
      assertGroupRolesRefused(group.getId(), platformRole.getId());
    }

    @Test
    @WithMockUser
    @DisplayName("Given a role of another tenant, should answer not found and link nothing")
    void given_roleOfAnotherTenant_should_answerNotFoundOnGroupRoles() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("GroupWithOtherTenantRole"))
              .persist()
              .get();
      String defaultTenantId = TenantContext.getCurrentTenant();
      tenantIsolationHelper.grantCapabilitiesInTenant(
          defaultTenantId, Set.of(Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES));
      // The caller stays a member of the default tenant only
      String otherTenantId = tenantIsolationHelper.createTenant("group-roles-other").getId();
      // createTenant enrols the creating user in the new tenant
      tenantIsolationHelper.detachCurrentUserFromTenant(otherTenantId);
      tenantIsolationHelper.switchToTenant(otherTenantId, entityManager);
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("RoleOfAnotherTenant", Set.of()))
              .persist()
              .get();
      tenantIsolationHelper.switchToTenant(defaultTenantId, entityManager);

      // -------- Act & Assert --------
      assertEquals(
          Set.of(defaultTenantId),
          testUserHolder.get().getTenants().stream().map(Tenant::getId).collect(Collectors.toSet()),
          "the caller is a member of the default tenant only");
      assertTrue(
          tenantUri(TENANT_GROUP_URI).contains(defaultTenantId),
          "the request targets the group's tenant");
      assertGroupRolesRefused(group.getId(), role.getId());
    }

    private void assertGroupRolesRefused(String groupId, String roleId) throws Exception {
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + groupId + "/roles")
                  .content(
                      asJsonString(
                          GroupUpdateRolesInput.builder().roleIds(List.of(roleId)).build()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
      entityManager.flush();
      assertEquals(
          0,
          ((Number)
                  entityManager
                      .createNativeQuery("SELECT count(*) FROM groups_roles WHERE group_id = :id")
                      .setParameter("id", groupId)
                      .getSingleResult())
              .intValue());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should update group users")
    void given_manageTenantUsersGroupsAndRoles_should_updateGroupUsers() throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should forbid assigning users to group with unowned role capabilities")
    void
        given_manageTenantUsersGroupsAndRoles_should_forbidUpdateGroupUsersWithUnownedRoleCapabilities()
            throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("UserGroupForbidden"))
              .persist()
              .get();
      Role role =
          tenantRoleComposer
              .forRole(
                  TenantRoleFixture.getRole("RoleForUserUpdate", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();
      group.setRoles(new ArrayList<>(List.of(role)));
      groupRepository.save(group);

      GroupUpdateUsersInput input = new GroupUpdateUsersInput();
      input.setUserIds(List.of(testUserHolder.get().getId()));

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/users")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should forbid making a group with unowned markings a default group")
    void given_manageTenantUsersGroupsAndRoles_should_forbidDefaultAssignWithUnownedMarkings()
        throws Exception {
      // -------- Arrange --------
      Group group = groupWithMarking("DefaultGroupMarked", persistedMarking());
      markingClearanceCacheManager.evictForUser(testUserHolder.get().getId());
      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("DefaultGroupMarked");
      input.setDefaultUserAssignation(true);

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/information")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should forbid assigning users to group with unowned markings")
    void given_manageTenantUsersGroupsAndRoles_should_forbidUpdateGroupUsersWithUnownedMarkings()
        throws Exception {
      // -------- Arrange --------
      Group group = groupWithMarking("UserGroupMarked", persistedMarking());
      markingClearanceCacheManager.evictForUser(testUserHolder.get().getId());

      // -------- Act & Assert --------
      updateUsers(tenantUri(TENANT_GROUP_URI), group.getId()).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given the MARKING feature off, should assign users to a group with unowned markings")
    void given_markingFeatureOff_should_updateGroupUsersWithUnownedMarkings() throws Exception {
      // -------- Arrange --------
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(false);
      Group group = groupWithMarking("UserGroupMarkedFlagOff", persistedMarking());
      markingClearanceCacheManager.evictForUser(testUserHolder.get().getId());

      // -------- Act & Assert --------
      updateUsers(tenantUri(TENANT_GROUP_URI), group.getId()).andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES, should remove users from a group with unowned markings")
    void given_manageTenantUsersGroupsAndRoles_should_removeUsersFromGroupWithUnownedMarkings()
        throws Exception {
      // -------- Arrange --------
      Group group = groupWithMarking("UserGroupMarkedRemoval", persistedMarking());
      User member =
          userComposer
              .forUser(UserFixture.getUser("Charlie", "Member", "charlie-removal@openaev.io"))
              .persist()
              .get();
      group.setUsers(new ArrayList<>(List.of(member)));
      groupRepository.save(group);
      markingClearanceCacheManager.evictForUser(testUserHolder.get().getId());
      GroupUpdateUsersInput input = new GroupUpdateUsersInput();
      input.setUserIds(List.of());

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/users")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "Given a user of another tenant only, should update group users without tenant in URL")
    void given_userOfAnotherTenantOnly_should_updateGroupUsersWithoutTenantInUrl()
        throws Exception {
      // -------- Arrange --------
      // On /api/groups the thread-local tenant is the default one, not the group's.
      String otherTenantId =
          tenantIsolationHelper.createTenantWithCurrentUser("users-without-tenant").getId();
      Group group = TenantGroupFixture.getGroup("UserGroupOtherTenant");
      group.setTenant(entityManager.getReference(Tenant.class, otherTenantId));
      groupRepository.save(group);

      // -------- Act & Assert --------
      updateUsers(GROUP_URI, group.getId()).andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser
    @DisplayName(
        "Given BYPASS in the default tenant only, should forbid giving a role it lacks in the group's tenant")
    void given_bypassInDefaultTenantOnly_should_forbidUpdateGroupRolesInOtherTenant()
        throws Exception {
      // -------- Arrange --------
      String defaultTenantId = TenantContext.getCurrentTenant();
      tenantIsolationHelper.grantCapabilitiesInTenant(defaultTenantId, Set.of(Capability.BYPASS));
      String otherTenantId =
          tenantIsolationHelper
              .createTenantWithCapabilities(
                  "roles-other-tenant", Set.of(Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES))
              .getId();
      tenantIsolationHelper.switchToTenant(otherTenantId, entityManager);
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("RoleGroupOtherTenant"))
              .persist()
              .get();
      Role role =
          tenantRoleComposer
              .forRole(
                  TenantRoleFixture.getRole("RoleOtherTenant", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();
      tenantIsolationHelper.switchToTenant(defaultTenantId, entityManager);
      GroupUpdateRolesInput input =
          GroupUpdateRolesInput.builder().roleIds(List.of(role.getId())).build();

      // -------- Act & Assert --------
      mvc.perform(
              put(GROUP_URI + "/" + group.getId() + "/roles")
                  .header("X-Tenant-Ids", otherTenantId)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given ACCESS_TENANT_USERS_GROUPS_AND_ROLES only, should be forbidden to update")
    void given_accessTenantUsersGroupsAndRoles_should_forbidUpdate() throws Exception {
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

    private ResultActions updateUsers(String groupsUri, String groupId) throws Exception {
      GroupUpdateUsersInput input = new GroupUpdateUsersInput();
      input.setUserIds(List.of(testUserHolder.get().getId()));
      return mvc.perform(
          put(groupsUri + "/" + groupId + "/users")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()));
    }

    private Group groupWithMarking(String name, MarkingDefinition marking) {
      Group group = tenantGroupComposer.forGroup(TenantGroupFixture.getGroup(name)).persist().get();
      group.setMarkings(new HashSet<>(Set.of(marking)));
      return groupRepository.save(group);
    }

    private MarkingDefinition persistedMarking() {
      return markingDefinitionComposer
          .forMarkingDefinition(MarkingDefinitionFixture.createDefaultMarkingDefinition())
          .withTenantId(TenantContext.getCurrentTenant())
          .persist()
          .get();
    }
  }

  @Nested
  @DisplayName("Grants")
  class Grants {

    /**
     * Attaches a grant to the tenant group {@code @WithMockUser} built for the caller, so the
     * caller genuinely holds it. Flushing and clearing matters: the guard re-reads the user through
     * {@code UserService#currentUser}.
     */
    private void grantToCaller(Scenario scenario, Grant.GRANT_TYPE type) {
      Group callerGroup =
          testUserHolder.get().getUnscopedGroups().stream()
              .filter(group -> group.getTenant() != null)
              .findFirst()
              .orElseThrow();
      Grant grant =
          grantRepository.save(
              Grant.of(type, callerGroup, scenario.getId(), Grant.GRANT_RESOURCE_TYPE.SCENARIO));
      callerGroup.getGrants().add(grant);
      groupRepository.save(callerGroup);
      entityManager.flush();
      entityManager.clear();
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES and the grant itself, should add a grant it holds")
    void given_manageTenantUsersGroupsAndRolesHoldingGrant_should_addGrant() throws Exception {
      // -------- Arrange --------
      // The caller holds PLANNER, which outranks the OBSERVER it hands out.
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      grantToCaller(scenario, Grant.GRANT_TYPE.PLANNER);
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("GrantGroupAllowed"))
              .persist()
              .get();

      GroupGrantInput input = new GroupGrantInput();
      input.setName(Grant.GRANT_TYPE.OBSERVER);
      input.setResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      input.setResourceId(scenario.getId());

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/grants")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<Map<String, Object>> grants = JsonPath.read(response, "$.group_grants");
      assertFalse(grants.isEmpty());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES and the grant itself, should remove a grant it holds")
    void given_manageTenantUsersGroupsAndRolesHoldingGrant_should_removeGrant() throws Exception {
      // -------- Arrange --------
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      grantToCaller(scenario, Grant.GRANT_TYPE.OBSERVER);
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("GrantGroupRemoveAllowed"))
              .persist()
              .get();
      Grant grant =
          grantRepository.save(
              Grant.of(
                  Grant.GRANT_TYPE.OBSERVER,
                  group,
                  scenario.getId(),
                  Grant.GRANT_RESOURCE_TYPE.SCENARIO));
      group.getGrants().add(grant);
      groupRepository.save(group);

      // -------- Act --------
      String response =
          mvc.perform(
                  delete(
                          tenantUri(TENANT_GROUP_URI)
                              + "/"
                              + group.getId()
                              + "/grants/"
                              + grant.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<Map<String, Object>> grants = JsonPath.read(response, "$.group_grants");
      assertTrue(grants.isEmpty());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("Given admin user, should add a grant to a tenant group")
    void given_admin_should_addGrant() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("GrantGroup")).persist().get();
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();

      GroupGrantInput input = new GroupGrantInput();
      input.setName(Grant.GRANT_TYPE.OBSERVER);
      input.setResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      input.setResourceId(scenario.getId());

      // -------- Act --------
      String response =
          mvc.perform(
                  post(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/grants")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<Map<String, Object>> grants = JsonPath.read(response, "$.group_grants");
      assertFalse(grants.isEmpty());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES only, should be forbidden to add unowned grants")
    void given_manageTenantUsersGroupsAndRoles_should_forbidAddGrant() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("GrantGroupForbidden"))
              .persist()
              .get();
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();

      GroupGrantInput input = new GroupGrantInput();
      input.setName(Grant.GRANT_TYPE.OBSERVER);
      input.setResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      input.setResourceId(scenario.getId());

      // -------- Act & Assert --------
      mvc.perform(
              post(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/grants")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES only, should be forbidden to remove unowned grants")
    void given_manageTenantUsersGroupsAndRoles_should_forbidRemoveGrant() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("GrantGroupRemoveForbidden"))
              .persist()
              .get();
      Scenario scenario =
          scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
      Grant grant =
          grantRepository.save(
              Grant.of(
                  Grant.GRANT_TYPE.OBSERVER,
                  group,
                  scenario.getId(),
                  Grant.GRANT_RESOURCE_TYPE.SCENARIO));
      group.getGrants().add(grant);
      groupRepository.save(group);

      // -------- Act & Assert --------
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/grants/" + grant.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Auto assign new markings")
  class AutoNewMarking {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given no ASSIGN_MARKING, should forbid turning it on")
    void given_noAssignMarking_should_forbidTurningItOn() throws Exception {
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("AutoOn")).persist().get();

      updateMarkings(group.getId(), true).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName(
        "Given ASSIGN_MARKING in the default tenant only, should forbid turning it on in another tenant")
    void given_assignMarkingInDefaultTenantOnly_should_forbidTurningItOnInOtherTenant()
        throws Exception {
      // -------- Arrange --------
      String defaultTenantId = TenantContext.getCurrentTenant();
      tenantIsolationHelper.grantCapabilitiesInTenant(
          defaultTenantId, Set.of(Capability.ASSIGN_MARKING));
      String otherTenantId =
          tenantIsolationHelper
              .createTenantWithCapabilities(
                  "auto-marking-other-tenant",
                  Set.of(Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES))
              .getId();
      tenantIsolationHelper.switchToTenant(otherTenantId, entityManager);
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("AutoOnOtherTenant"))
              .persist()
              .get();
      tenantIsolationHelper.switchToTenant(defaultTenantId, entityManager);

      // -------- Act & Assert --------
      mvc.perform(
              put(GROUP_URI + "/" + group.getId() + "/markings")
                  .header("X-Tenant-Ids", otherTenantId)
                  .content(asJsonString(new GroupUpdateMarkingsInput(List.of(), true)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(
        withCapabilities = {
          Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
          Capability.ASSIGN_MARKING
        })
    @DisplayName("Given ASSIGN_MARKING but not every marking, should forbid turning it on")
    void given_notEveryMarkingHeld_should_forbidTurningItOn() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("AutoOn")).persist().get();
      markingDefinitionComposer
          .forMarkingDefinition(MarkingDefinitionFixture.createDefaultMarkingDefinition())
          .withTenantId(TenantContext.getCurrentTenant())
          .persist();
      markingClearanceCacheManager.evictForUser(testUserHolder.get().getId());

      // -------- Act & Assert --------
      updateMarkings(group.getId(), true).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Should keep it when not sent, and turn it off without ASSIGN_MARKING")
    void given_notSentThenOff_should_keepThenTurnItOff() throws Exception {
      // -------- Arrange --------
      Group autoGroup = TenantGroupFixture.getGroup("AutoOff");
      autoGroup.setAutoNewMarking(true);
      Group group = tenantGroupComposer.forGroup(autoGroup).persist().get();

      // -------- Act & Assert — not sent --------
      updateMarkings(group.getId(), null).andExpect(status().is2xxSuccessful());
      assertTrue(groupRepository.findById(group.getId()).orElseThrow().isAutoNewMarking());

      // -------- Act & Assert — off --------
      updateMarkings(group.getId(), false)
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.group_auto_new_marking").value(false));
      assertFalse(groupRepository.findById(group.getId()).orElseThrow().isAutoNewMarking());
    }

    private ResultActions updateMarkings(String groupId, Boolean autoNewMarking) throws Exception {
      return mvc.perform(
          put(tenantUri(TENANT_GROUP_URI) + "/" + groupId + "/markings")
              .content(asJsonString(new GroupUpdateMarkingsInput(List.of(), autoNewMarking)))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()));
    }
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @WithMockUser(
        withCapabilities = {
          Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES,
          Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES
        })
    @DisplayName(
        "Given DELETE_TENANT_USERS_GROUPS_AND_ROLES, should delete a tenant group with users")
    void given_deleteTenantUsersGroupsAndRoles_should_deleteGroup() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer.forGroup(TenantGroupFixture.getGroup("ToDelete")).persist().get();
      entityManager.flush();

      // Assign the current user to the group (bidirectional User↔Group relationship)
      String userId = testUserHolder.get().getId();
      GroupUpdateUsersInput usersInput = new GroupUpdateUsersInput();
      usersInput.setUserIds(List.of(userId));

      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/users")
                  .content(asJsonString(usersInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      entityManager.flush();
      entityManager.clear();

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
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName(
        "Given DELETE_TENANT_USERS_GROUPS_AND_ROLES, should return 404 when deleting nonexistent group")
    void given_deleteTenantUsersGroupsAndRoles_should_return404() throws Exception {
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/nonexistent")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("Given MANAGE_TENANT_USERS_GROUPS_AND_ROLES only, should be forbidden to delete")
    void given_manageTenantUsersGroupsAndRoles_should_forbidDelete() throws Exception {
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
    @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES})
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
              Set.of(
                  Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES,
                  Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));

      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("Isolated Group");

      // Seeded directly (native insert), not through the create endpoint: creating under tenant
      // X's path would set the tenant scope (TxCtx) to X on this test's wrapping transaction, and
      // the read call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Seeding bypasses that entirely.
      String groupId = UUID.randomUUID().toString();
      entityManager
          .createNativeQuery(
              "INSERT INTO groups (group_id, group_name, tenant_id)"
                  + " VALUES (CAST(:id AS uuid), :name, CAST(:tenant AS uuid))")
          .setParameter("id", groupId)
          .setParameter("name", input.getName())
          .setParameter("tenant", tenantX.getId())
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();

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
