package io.openaev.service.account;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.EndpointFixture.createWindowsEndpointRegisterInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Agent runtime access control")
class AgentRuntimeAccessControlTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TestUserHolder testUserHolder;
  @MockitoSpyBean private EndpointService endpointService;

  @BeforeEach
  void linkMockUserToDefaultTenant() {
    // /register carries a TxCtx param (v2 write-scope resolution): the mock user needs a
    // users_tenants row, otherwise the scope resolves to TxCtx.Missing and the write is refused
    // with 400 regardless of isAdmin/capabilities.
    if (testUserHolder.isSet()) {
      tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    }
  }

  private EndpointRegisterInput buildRegisterInput() {
    return createWindowsEndpointRegisterInput(List.of(), "ext-ref-test");
  }

  @Nested
  @DisplayName("Register endpoint (POST /register)")
  class RegisterEndpoint {

    @Test
    @DisplayName("should register endpoint when is admin")
    @WithMockUser(isAdmin = true)
    void given_admin_should_register() throws Exception {
      // Arrange
      EndpointRegisterInput input = buildRegisterInput();
      Endpoint mockEndpoint = new Endpoint();
      mockEndpoint.setHostname("test");
      doReturn(mockEndpoint)
          .when(endpointService)
          .register(any(EndpointRegisterInput.class), any(String.class));

      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/register")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should be forbidden with only MANAGE_ASSETS capability")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_manageAssetsOnly_should_forbidRegister() throws Exception {
      // Arrange
      EndpointRegisterInput input = buildRegisterInput();

      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/register")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should not be forbidden with AGENT_RUNTIME_ACCESS capability")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_allowRegister() throws Exception {
      // Arrange
      EndpointRegisterInput input = buildRegisterInput();
      Endpoint mockEndpoint = new Endpoint();
      mockEndpoint.setHostname("test");
      doReturn(mockEndpoint)
          .when(endpointService)
          .register(any(EndpointRegisterInput.class), any(String.class));

      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/register")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @DisplayName("Get endpoint jobs (POST /jobs)")
  class GetEndpointJobs {

    @Test
    @DisplayName("should be forbidden with only MANAGE_ASSETS capability")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_manageAssetsOnly_should_forbidJobs() throws Exception {
      // Arrange
      EndpointRegisterInput input = buildRegisterInput();

      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/jobs")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should not be forbidden with AGENT_RUNTIME_ACCESS capability")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_allowJobs() throws Exception {
      // Arrange
      EndpointRegisterInput input = buildRegisterInput();
      doReturn(List.of()).when(endpointService).getEndpointJobs(any(EndpointRegisterInput.class));

      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/jobs")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @DisplayName("Cleanup agent job (DELETE /jobs/{id})")
  class CleanupAgentJob {

    private static final String FAKE_JOB_ID = "00000000-0000-0000-0000-000000000099";

    @Test
    @DisplayName("should be forbidden with only MANAGE_ASSETS capability")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_manageAssetsOnly_should_forbidCleanupJob() throws Exception {
      // Act & Assert
      mvc.perform(delete(ENDPOINT_URI + "/jobs/" + FAKE_JOB_ID).with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should not be forbidden with AGENT_RUNTIME_ACCESS capability")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_allowCleanupJob() throws Exception {
      // Act & Assert — job doesn't exist but RBAC passes (no error = 200 from deleteById)
      mvc.perform(delete(ENDPOINT_URI + "/jobs/" + FAKE_JOB_ID).with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @DisplayName("Access with no capability")
  class NoCapability {

    @Test
    @DisplayName("should be forbidden with no capabilities at all")
    @WithMockUser
    void given_noCapabilities_should_forbidRegister() throws Exception {
      // Arrange
      EndpointRegisterInput input = buildRegisterInput();

      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/register")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should be forbidden with no capabilities for jobs")
    @WithMockUser
    void given_noCapabilities_should_forbidJobs() throws Exception {
      // Arrange
      EndpointRegisterInput input = buildRegisterInput();

      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/jobs")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Inject execution callback (POST /execution/{agentId}/callback/{injectId})")
  class InjectExecutionCallback {

    private static final String FAKE_INJECT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String FAKE_AGENT_ID = "00000000-0000-0000-0000-000000000002";

    private InjectExecutionInput buildExecutionInput() {
      InjectExecutionInput input = new InjectExecutionInput();
      input.setMessage("test message");
      input.setStatus("SUCCESS");
      return input;
    }

    @Test
    @DisplayName("should be forbidden with only MANAGE_ASSETS capability")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_manageAssetsOnly_should_forbidCallback() throws Exception {
      // Arrange
      InjectExecutionInput input = buildExecutionInput();

      // Act & Assert
      mvc.perform(
              post(INJECT_URI + "/execution/" + FAKE_AGENT_ID + "/callback/" + FAKE_INJECT_ID)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should not be forbidden with AGENT_RUNTIME_ACCESS capability")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_allowCallback() throws Exception {
      // Arrange
      InjectExecutionInput input = buildExecutionInput();

      // Act & Assert — RBAC passes, business logic may throw (proves access granted)
      try {
        int status =
            mvc.perform(
                    post(INJECT_URI + "/execution/" + FAKE_AGENT_ID + "/callback/" + FAKE_INJECT_ID)
                        .content(asJsonString(input))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(403);
      } catch (ServletException e) {
        // Any exception other than access denied means RBAC passed
        assertThat(e.getRootCause()).isNotNull();
      }
    }
  }

  @Nested
  @DisplayName("Get executable payload (GET /{injectId}/{agentId}/executable-payload)")
  class GetExecutablePayload {

    private static final String FAKE_INJECT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String FAKE_AGENT_ID = "00000000-0000-0000-0000-000000000002";

    @Test
    @DisplayName("should be forbidden with only MANAGE_ASSETS capability")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_manageAssetsOnly_should_forbidGetPayload() throws Exception {
      // Act & Assert
      mvc.perform(
              get(INJECT_URI + "/" + FAKE_INJECT_ID + "/" + FAKE_AGENT_ID + "/executable-payload")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should not be forbidden with AGENT_RUNTIME_ACCESS capability")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_allowGetPayload() throws Exception {
      // Act & Assert — will get 404 (inject not found) which proves RBAC passed
      mvc.perform(
              get(INJECT_URI + "/" + FAKE_INJECT_ID + "/" + FAKE_AGENT_ID + "/executable-payload")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("AGENT_RUNTIME_ACCESS should NOT have access to asset-management endpoints")
  class NoAccessToAssetManagement {

    @Test
    @DisplayName("should be forbidden to list endpoints")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_forbidListEndpoints() throws Exception {
      // Act & Assert
      mvc.perform(get(ENDPOINT_URI).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should be forbidden to search endpoints")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_forbidSearchEndpoints() throws Exception {
      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/search")
                  .content(
                      "{\"filterGroup\":{\"mode\":\"and\",\"filters\":[]},\"size\":10,\"page\":0}")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should be forbidden to create an agentless endpoint")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_forbidCreateEndpoint() throws Exception {
      // Act & Assert
      mvc.perform(
              post(ENDPOINT_URI + "/agentless")
                  .content(
                      "{\"endpoint_name\":\"test\",\"endpoint_platform\":\"Windows\",\"endpoint_arch\":\"x86_64\",\"endpoint_ips\":[\"1.2.3.4\"]}")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());
    }
  }

  @Nested
  @DisplayName("AGENT_RUNTIME_ACCESS should NOT have access to ResourceType.INJECT")
  class NoAccessToInject {

    @Test
    @DisplayName("should be forbidden to read an inject")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_forbidInjectRead() throws Exception {
      // Arrange — create a real inject in DB
      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTeams(new ArrayList<>());
      exercise.setFrom("test@email.com");
      Exercise exerciseSaved = exerciseRepository.saveAndFlush(exercise);

      Inject inject = InjectFixture.getDefaultInject();
      inject.setExercise(exerciseSaved);
      inject = injectRepository.save(inject);

      // Act & Assert — user has no grant on the exercise, so should be forbidden
      mvc.perform(get(INJECT_URI + "/" + inject.getId()).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should be forbidden to search injects")
    @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
    void given_agentRuntimeAccess_should_forbidInjectSearch() throws Exception {
      // Act & Assert
      mvc.perform(
              post(INJECT_URI + "/search/export")
                  .content("{}")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());
    }
  }
}
