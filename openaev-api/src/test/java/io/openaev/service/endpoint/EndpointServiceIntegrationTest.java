package io.openaev.service.endpoint;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.utils.fixtures.EndpointRegisterInputFixture.DEFAULT_ENDPOINT_AGENT_VERSION;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.utils.fixtures.EndpointRegisterInputFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class EndpointServiceIntegrationTest extends IntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectMapper mapper;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private AssetAgentJobRepository assetAgentJobRepository;

  @BeforeEach
  void setUp() {
    executorComposer.reset();
    tenantComposer.reset();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Endpoint registration")
  class EndpointRegistration {
    @Nested
    @DisplayName("Upgrade jobs tests")
    class UpgradeJobsTests {
      @Nested
      @DisplayName("When agent version does not match server version")
      class UpgradeAgentVersionDoesNotMatchServerVersion {
        private final String agentVersion = "NOMATCH";

        @Test
        @DisplayName("Registering once creates an upgrade job")
        void registeringOnceCreatesAnUpgradeJob() throws Exception {
          executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
          EndpointRegisterInput input =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input.setAgentVersion(agentVersion);

          entityManager.flush();
          entityManager.clear();

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());

          List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobs).satisfiesOnlyOnce(job -> assertThat(job.getInject()).isNull());
        }

        @Test
        @DisplayName("Registering twice creates a single upgrade job")
        void registeringTwiceCreatesASingleUpgradeJob() throws Exception {
          executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
          EndpointRegisterInput input =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input.setAgentVersion(agentVersion);

          entityManager.flush();
          entityManager.clear();

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn();

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn();

          List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobs).satisfiesOnlyOnce(job -> assertThat(job.getInject()).isNull());
        }

        @Test
        @Disabled(
            "Multitenant registration does not work because builtin openaev executor can't exist in multiple tenants")
        @DisplayName(
            "Registering same endpoint on different tenants create a single upgrade job in each tenant")
        void registeringSameEndpointOnDifferentTenants() throws Exception {
          // executor in default tenant
          executorComposer.forExecutor(executorFixture.createOpenAEVExecutor()).persist();
          TenantComposer.Composer tenantWrapper =
              tenantComposer.forTenant(TenantFixture.getTenant("additional_tenant")).persist();
          entityManager.flush();
          TenantContext.setCurrentTenant(tenantWrapper.get().getId());
          // executor in other tenant
          executorComposer.forExecutor(executorFixture.createOpenAEVExecutor()).persist();
          entityManager.flush();
          entityManager.clear();

          TenantContext.clearCurrentTenant();

          EndpointRegisterInput input =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input.setAgentVersion(agentVersion);

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn();

          TenantContext.setCurrentTenant(tenantWrapper.get().getId());

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn();

          List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobs).satisfiesOnlyOnce(job -> assertThat(job.getInject()).isNull());
        }

        @Test
        @DisplayName("Registering different endpoints create an upgrade job for each")
        void registeringDifferentEndpointsCreateAnUpgradeJobForEach() throws Exception {
          executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
          EndpointRegisterInput input1 =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input1.setAgentVersion(agentVersion);
          input1.setExternalReference(UUID.randomUUID().toString());
          input1.setName(UUID.randomUUID().toString());

          EndpointRegisterInput input2 =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input2.setAgentVersion(agentVersion);
          input2.setExternalReference(UUID.randomUUID().toString());
          input2.setName(UUID.randomUUID().toString());
          input2.setMacAddresses(List.of("00:00:ab:ad:1d:ea").toArray(new String[0]));

          entityManager.flush();
          entityManager.clear();

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input1))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input2))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());

          List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobs)
              .satisfiesOnlyOnce(
                  job ->
                      assertThat(job)
                          .satisfies(j -> assertThat(j.getInject()).isNull())
                          .satisfies(
                              j ->
                                  assertThat(j.getAgent().getExternalReference())
                                      .isEqualTo(input1.getExternalReference())));
          assertThat(jobs)
              .satisfiesOnlyOnce(
                  job ->
                      assertThat(job)
                          .satisfies(j -> assertThat(j.getInject()).isNull())
                          .satisfies(
                              j ->
                                  assertThat(j.getAgent().getExternalReference())
                                      .isEqualTo(input2.getExternalReference())));
        }
      }

      @Nested
      @DisplayName("When agent version matches server version")
      class UpgradeAgentVersionMatchesServerVersion {
        private final String agentVersion = DEFAULT_ENDPOINT_AGENT_VERSION;

        @Test
        @DisplayName("Registering once creates zero upgrade job")
        void registeringOnceCreatesAnUpgradeJob() throws Exception {
          executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
          EndpointRegisterInput input =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input.setAgentVersion(agentVersion);

          entityManager.flush();
          entityManager.clear();

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());

          List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobs).isEmpty();
        }

        @Test
        @DisplayName("Registering twice creates zero upgrade job")
        void registeringTwiceCreatesASingleUpgradeJob() throws Exception {
          executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
          EndpointRegisterInput input =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input.setAgentVersion(agentVersion);

          entityManager.flush();
          entityManager.clear();

          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());
          mockMvc
              .perform(
                  post(ENDPOINT_URI + "/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());

          List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobs).isEmpty();
        }
      }
    }
  }
}
