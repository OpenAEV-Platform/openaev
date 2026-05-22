package io.openaev.rest.role;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.repository.RoleRepository;
import io.openaev.opencti.connectors.Constants;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.service.RoleService;
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
@DisplayName("Tenant Role API — reserved names")
public class TenantRoleReservedNameApiTest extends IntegrationTest {

  private static final String TENANT_ROLE_URI = "/api/tenants/{tenantId}/roles";

  @Autowired private MockMvc mvc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private RoleService roleService;

  private static final String SERVICE_ROLE_NAME =
      io.openaev.service.account.Constants.SERVICE_ROLE_NAME;
  private static final String PROCESS_STIX_ROLE_NAME = Constants.PROCESS_STIX_ROLE_NAME;

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given SERVICE_ROLE_NAME, should return 400")
    void given_serviceRoleName_should_returnBadRequest_onCreate() throws Exception {
      // -------- Arrange --------
      RoleInput input =
          RoleInput.builder()
              .name(SERVICE_ROLE_NAME)
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
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given PROCESS_STIX_ROLE_NAME, should return 400")
    void given_processStixRoleName_should_returnBadRequest_onCreate() throws Exception {
      // -------- Arrange --------
      RoleInput input =
          RoleInput.builder()
              .name(PROCESS_STIX_ROLE_NAME)
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
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given a non-reserved name, should succeed")
    void given_validName_should_succeed_onCreate() throws Exception {
      // -------- Arrange --------
      RoleInput input =
          RoleInput.builder()
              .name("Analyst")
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
          .andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given a non-reserved role and a reserved target name, should return 400")
    void given_reservedTargetName_should_returnBadRequest_onUpdate() throws Exception {
      // -------- Arrange --------
      Role role =
          tenantRoleComposer
              .forRole(TenantRoleFixture.getRole("BeforeUpdate", Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();
      RoleInput input =
          RoleInput.builder()
              .name(SERVICE_ROLE_NAME)
              .description("desc")
              .capabilities(Set.of(Capability.ACCESS_ASSETS))
              .build();

      // -------- Act & Assert --------
      mvc.perform(
              put(tenantUri(TENANT_ROLE_URI) + "/" + role.getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("Given an existing reserved-name role, should return 400 even when renaming")
    void given_existingReservedRole_should_returnBadRequest_onUpdate() throws Exception {
      // -------- Arrange --------
      // Pre-seed a reserved-name role directly via the repository to bypass the API guard.
      Role reserved =
          tenantRoleComposer
              .forRole(
                  TenantRoleFixture.getRole(SERVICE_ROLE_NAME, Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();
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

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TENANT_SETTINGS})
    @DisplayName("Given an existing reserved-name role, should return 400")
    void given_existingReservedRole_should_returnBadRequest_onDelete() throws Exception {
      // -------- Arrange --------
      Role reserved =
          tenantRoleComposer
              .forRole(
                  TenantRoleFixture.getRole(
                      PROCESS_STIX_ROLE_NAME, Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

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
      assertTrue(status == 400, "Expected 400 but got " + status);
      // The reserved role must still exist
      assertTrue(roleRepository.findById(reserved.getId()).isPresent());
    }
  }

  @Nested
  @DisplayName("Internal service path (system-managed roles)")
  class InternalServicePath {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("given_existing_role_has_reserved_name_should_allow_update via updateRoleInternal")
    void given_existingReservedRole_should_allowUpdate_viaInternal() {
      // -------- Arrange --------
      Role reserved =
          tenantRoleComposer
              .forRole(
                  TenantRoleFixture.getRole(SERVICE_ROLE_NAME, Set.of(Capability.ACCESS_ASSETS)))
              .persist()
              .get();

      // -------- Act --------
      Role updated =
          roleService.updateRoleInternal(
              reserved.getId(),
              SERVICE_ROLE_NAME,
              "re-converged description",
              Set.of(Capability.AGENT_RUNTIME_ACCESS));

      // -------- Assert --------
      assertThat(updated.getName()).isEqualTo(SERVICE_ROLE_NAME);
      assertThat(updated.getDescription()).isEqualTo("re-converged description");
      assertThat(updated.getCapabilities()).contains(Capability.AGENT_RUNTIME_ACCESS);
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("given_unknown_role_id_should_throw_ElementNotFoundException")
    void given_unknownRoleId_should_throw_ElementNotFoundException() {
      // -------- Arrange --------
      String unknownId = UUID.randomUUID().toString();

      // -------- Act & Assert --------
      assertThatThrownBy(
              () ->
                  roleService.updateRoleInternal(
                      unknownId, "any-name", "any-desc", Set.of(Capability.AGENT_RUNTIME_ACCESS)))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }
}
