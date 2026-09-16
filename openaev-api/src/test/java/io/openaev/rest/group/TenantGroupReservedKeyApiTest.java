package io.openaev.rest.group;

import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_GROUP_ID;
import static io.openaev.service.account.Constants.SERVICE_GROUP_ID;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tenant Group API — reserved keys (by id)")
public class TenantGroupReservedKeyApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;

  private static final String SERVICE_EMAIL_PATTERN =
      io.openaev.service.account.ServiceAccountPrivilegeService.SERVICE_EMAIL_PATTERN;

  /** Computes the tenant-scoped reserved id used by the service-account group in this tenant. */
  private String reservedServiceGroupId() {
    return AbstractPrivilegeService.getUUIDFromName(
        SERVICE_GROUP_ID, TenantContext.getCurrentTenant());
  }

  /** Computes the tenant-scoped reserved id used by the STIX-processor group in this tenant. */
  private String reservedStixGroupId() {
    return AbstractPrivilegeService.getUUIDFromName(
        PROCESS_STIX_GROUP_ID, TenantContext.getCurrentTenant());
  }

  /** Persists a group with an explicitly assigned (reserved) id. */
  private Group persistGroupWithId(String id, String name) {
    Group group = TenantGroupFixture.getGroup(name);
    group.setId(id);
    return tenantGroupComposer.forGroup(group).persist().get();
  }

  // --------------------------------------------------------------------------
  // SERVICE-ACCOUNT GROUP (reserved id) — every mutation is forbidden
  // --------------------------------------------------------------------------

  @Nested
  @DisplayName("Service-account group (reserved id)")
  class ServiceAccountGroup {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("editing information should return 400")
    void given_reservedGroupId_should_returnBadRequest_onUpdateInformation() throws Exception {
      // -------- Arrange --------
      Group reserved = persistGroupWithId(reservedServiceGroupId(), "ReservedByIdService");
      TenantGroupCreateInput input = new TenantGroupCreateInput();
      input.setName("RenamedToNonReserved");
      input.setDescription("desc");

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + reserved.getId() + "/information")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("adding a grant should return 400")
    void given_reservedGroupId_should_returnBadRequest_onAddGrant() throws Exception {
      // -------- Arrange --------
      Group reserved = persistGroupWithId(reservedServiceGroupId(), "ReservedByIdService");
      GroupGrantInput input = new GroupGrantInput();
      input.setName(Grant.GRANT_TYPE.OBSERVER);
      input.setResourceId("any-resource-id");
      input.setResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);

      // -------- Act & Assert --------
      mvc.perform(
              post(tenantUri(TENANT_GROUP_URI) + "/" + reserved.getId() + "/grants")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("removing a grant should return 400")
    void given_reservedGroupId_should_returnBadRequest_onRemoveGrant() throws Exception {
      // -------- Arrange --------
      Group reserved = persistGroupWithId(reservedServiceGroupId(), "ReservedByIdService");

      // -------- Act & Assert --------
      // Reserved-id guard fires before any grant lookup, so any grantId is fine.
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/" + reserved.getId() + "/grants/any-grant-id")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("updating users should return 400")
    void given_reservedGroupId_should_returnBadRequest_onUpdateUsers() throws Exception {
      // -------- Arrange --------
      Group reserved = persistGroupWithId(reservedServiceGroupId(), "ReservedByIdService");
      GroupUpdateUsersInput input = new GroupUpdateUsersInput();
      input.setUserIds(List.of());

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + reserved.getId() + "/users")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("updating roles should return 400")
    void given_reservedGroupId_should_returnBadRequest_onUpdateRoles() throws Exception {
      // -------- Arrange --------
      Group reserved = persistGroupWithId(reservedServiceGroupId(), "ReservedByIdService");
      GroupUpdateRolesInput input = GroupUpdateRolesInput.builder().roleIds(List.of()).build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + reserved.getId() + "/roles")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("deleting (SERVICE) should return 400")
    void given_reservedServiceGroupId_should_returnBadRequest_onDelete() throws Exception {
      // -------- Arrange --------
      Group reserved = persistGroupWithId(reservedServiceGroupId(), "ReservedByIdService");

      // -------- Act & Assert --------
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/" + reserved.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("deleting (PROCESS_STIX) should return 400")
    void given_reservedStixGroupId_should_returnBadRequest_onDelete() throws Exception {
      // -------- Arrange --------
      Group reserved = persistGroupWithId(reservedStixGroupId(), "ReservedByIdStix");

      // -------- Act & Assert --------
      mvc.perform(
              delete(tenantUri(TENANT_GROUP_URI) + "/" + reserved.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  // --------------------------------------------------------------------------
  // NORMAL GROUP — must reject reserved users / roles in payload
  // --------------------------------------------------------------------------

  @Nested
  @DisplayName("Normal group — reject reserved users/roles in payload")
  class NormalGroupRejectReserved {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("should reject adding a user with a reserved service-account email")
    void given_reservedEmailUserInPayload_should_returnBadRequest_onUpdateUsers() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("NormalGroupForUsers"))
              .persist()
              .get();
      // Seed a user with a reserved (service-*) email and attach to the current tenant
      // so it is found by `findAllByIdInAndTenantId`.
      User reservedUser =
          UserFixture.getUser(
              "Service", "Account", SERVICE_EMAIL_PATTERN.formatted(UUID.randomUUID().toString()));
      reservedUser = userRepository.save(reservedUser);
      tenantRepository.addUserToTenant(reservedUser.getId(), TenantContext.getCurrentTenant());
      entityManager.flush();

      GroupUpdateUsersInput input = new GroupUpdateUsersInput();
      input.setUserIds(List.of(reservedUser.getId()));

      // -------- Act --------
      int status =
          mvc.perform(
                  put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/users")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertEquals(400, status, "Expected 400 but got " + status);
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES})
    @DisplayName("should reject adding a reserved-id role into a normal group")
    void given_reservedRoleIdInPayload_should_returnBadRequest_onUpdateRoles() throws Exception {
      // -------- Arrange --------
      Group group =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup("NormalGroupForRoles"))
              .persist()
              .get();
      // Seed a role with a reserved id in the current tenant.
      Role roleWithReservedId =
          TenantRoleFixture.getRole("ReservedRoleById", Set.of(Capability.ACCESS_ASSETS));
      roleWithReservedId.setId(
          AbstractPrivilegeService.getUUIDFromName(
              io.openaev.service.account.Constants.SERVICE_ROLE_ID,
              TenantContext.getCurrentTenant()));
      Role reservedRole = tenantRoleComposer.forRole(roleWithReservedId).persist().get();
      GroupUpdateRolesInput input =
          GroupUpdateRolesInput.builder().roleIds(List.of(reservedRole.getId())).build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_GROUP_URI) + "/" + group.getId() + "/roles")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }
}
