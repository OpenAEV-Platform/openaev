package io.openaev.rest.role;

import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_ROLE_ID;
import static io.openaev.service.account.Constants.SERVICE_ROLE_ID;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.service.TenantRoleService;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.mockUser.WithMockUser;
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
@DisplayName("Tenant Role API — reserved keys (by id)")
public class TenantRoleReservedKeyApiTest extends IntegrationTest {

  private static final String TENANT_ROLE_URI = "/api/tenants/{tenantId}/roles";

  @Autowired private MockMvc mvc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private TenantRoleService tenantRoleService;

  /** Computes the tenant-scoped reserved id used by the service-account role in this tenant. */
  private String reservedServiceRoleId() {
    return AbstractPrivilegeService.getUUIDFromName(
        SERVICE_ROLE_ID, TenantContext.getCurrentTenant());
  }

  /** Computes the tenant-scoped reserved id used by the STIX-processor role in this tenant. */
  private String reservedStixRoleId() {
    return AbstractPrivilegeService.getUUIDFromName(
        PROCESS_STIX_ROLE_ID, TenantContext.getCurrentTenant());
  }

  /** Persists a role having an explicitly assigned (reserved) id. */
  private Role persistRoleWithId(String id, String name) {
    Role role = TenantRoleFixture.getRole(name, Set.of(Capability.ACCESS_ASSETS));
    role.setId(id);
    return tenantRoleComposer.forRole(role).persist().get();
  }

  // --------------------------------------------------------------------------
  // CREATE — nothing to guard: the public endpoint generates the id itself, and
  // createRoleInternal deliberately accepts reserved ids since that is how the
  // well-known roles are seeded. Reserved ids are enforced on update and delete.
  // --------------------------------------------------------------------------

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS, Capability.ACCESS_ASSETS})
    @DisplayName("Given a non-reserved id, public POST should succeed")
    void given_nonReservedId_should_succeed_onCreate() throws Exception {
      // -------- Arrange --------
      RoleInput input =
          RoleInput.builder()
              .name("NonReservedRole-" + UUID.randomUUID())
              .description("desc")
              .capabilities(Set.of(Capability.ACCESS_ASSETS))
              .build();

      // -------- Act & Assert --------
      mvc.perform(
              post(tenantUri(TENANT_ROLE_URI))
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }
  }

  // --------------------------------------------------------------------------
  // UPDATE — the role must be rejected based on its existing reserved id,
  // regardless of the requested target name.
  // --------------------------------------------------------------------------

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given a role whose id is reserved (SERVICE), should return 400")
    void given_reservedServiceRoleId_should_returnBadRequest_onUpdate() throws Exception {
      // -------- Arrange --------
      Role reserved = persistRoleWithId(reservedServiceRoleId(), "ReservedByIdService");
      RoleInput input =
          RoleInput.builder()
              .name("NotReservedAnymore")
              .description("desc")
              .capabilities(Set.of(Capability.ACCESS_ASSETS))
              .build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_ROLE_URI) + "/" + reserved.getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given a role whose id is reserved (PROCESS_STIX), should return 400")
    void given_reservedStixRoleId_should_returnBadRequest_onUpdate() throws Exception {
      // -------- Arrange --------
      Role reserved = persistRoleWithId(reservedStixRoleId(), "ReservedByIdStix");
      RoleInput input =
          RoleInput.builder()
              .name("NotReservedAnymore")
              .description("desc")
              .capabilities(Set.of(Capability.ACCESS_ASSETS))
              .build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_ROLE_URI) + "/" + reserved.getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  // --------------------------------------------------------------------------
  // DELETE
  // --------------------------------------------------------------------------

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_SETTINGS})
    @DisplayName("Given a role whose id is reserved (PROCESS_STIX), should return 400")
    void given_reservedStixRoleId_should_returnBadRequest_onDelete() throws Exception {
      // -------- Arrange --------
      Role reserved = persistRoleWithId(reservedStixRoleId(), "ReservedByIdStix");

      // -------- Act --------
      int status =
          mvc.perform(
                  delete(tenantUri(TENANT_ROLE_URI) + "/" + reserved.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertEquals(400, status, "Expected 400 but got " + status);
      // The reserved role must still exist
      assertTrue(roleRepository.findById(reserved.getId()).isPresent());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_SETTINGS})
    @DisplayName("Given a role whose id is reserved (SERVICE), should return 400")
    void given_reservedServiceRoleId_should_returnBadRequest_onDelete() throws Exception {
      // -------- Arrange --------
      Role reserved = persistRoleWithId(reservedServiceRoleId(), "ReservedByIdService");

      // -------- Act --------
      int status =
          mvc.perform(
                  delete(tenantUri(TENANT_ROLE_URI) + "/" + reserved.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertEquals(400, status, "Expected 400 but got " + status);
      assertTrue(roleRepository.findById(reserved.getId()).isPresent());
    }
  }

  // --------------------------------------------------------------------------
  // INTERNAL SERVICE PATH — must bypass the reserved-id guard
  // --------------------------------------------------------------------------

  @Nested
  @DisplayName("Internal service path (system-managed roles)")
  class InternalServicePath {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("given a role with a reserved id, updateRoleInternal should succeed")
    void given_reservedRoleId_should_allowUpdate_viaInternal() {
      // -------- Arrange --------
      Role reserved = persistRoleWithId(reservedServiceRoleId(), "ReservedByIdService");

      // -------- Act --------
      Role updated =
          tenantRoleService.updateRoleInternal(
              reserved.getId(),
              "re-converged name",
              "re-converged description",
              Set.of(Capability.AGENT_RUNTIME_ACCESS),
              TenantContext.getCurrentTenant());

      // -------- Assert --------
      assertThat(updated.getId()).isEqualTo(reserved.getId());
      assertThat(updated.getName()).isEqualTo("re-converged name");
      assertThat(updated.getDescription()).isEqualTo("re-converged description");
      assertThat(updated.getCapabilities()).contains(Capability.AGENT_RUNTIME_ACCESS);
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("given an unknown role id, should throw ElementNotFoundException")
    void given_unknownRoleId_should_throw_ElementNotFoundException() {
      // -------- Arrange --------
      String unknownId = UUID.randomUUID().toString();

      // -------- Act & Assert --------
      assertThatThrownBy(
              () ->
                  tenantRoleService.updateRoleInternal(
                      unknownId,
                      "any-name",
                      "any-desc",
                      Set.of(Capability.AGENT_RUNTIME_ACCESS),
                      TenantContext.getCurrentTenant()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }
}
