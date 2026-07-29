package io.openaev.service.endpoint;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.utils.fixtures.EndpointRegisterInputFixture.DEFAULT_ENDPOINT_AGENT_VERSION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointRegisterInputFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
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
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private AssetAgentJobRepository assetAgentJobRepository;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  @Autowired private EndpointRepository endpointRepository;

  @BeforeEach
  void setUp() {
    executorComposer.reset();
    tenantComposer.reset();
    ensureServiceAccount(TenantContext.getCurrentTenant());
  }

  /**
   * Bootstraps the service-account user + token for the given tenant. Required as soon as a test
   * triggers the agent upgrade-job branch, because {@code EndpointService.generateUpgradeCommand}
   * looks up the service-account token
   */
  private void ensureServiceAccount(String tenantId) {
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantId);
    entityManager.flush();
    entityManager.clear();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Endpoint registration")
  class EndpointRegistration {
    @Nested
    @DisplayName("Upgrade jobs tests")
    class UpgradeJobsTests {

      @Nested
      @DisplayName(
          "When auto-update is enabled (default) and agent version does not match server version")
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
        @DisplayName(
            "Registering same endpoint on different tenants create a single upgrade job in each tenant")
        void registeringSameEndpointOnDifferentTenants() throws Exception {
          TenantComposer.Composer tenantWrapper =
              tenantComposer
                  .forTenant(
                      tenantIsolationTestHelper.createTenantWithCurrentUser("additional_tenant_1"))
                  .persist();
          tenantIsolationTestHelper.switchToTenant(tenantWrapper.get().getId(), entityManager);
          // executor in default tenant
          executorComposer.forExecutor(executorFixture.createOAEVExecutor()).persist();
          entityManager.flush();
          entityManager.clear();
          ensureServiceAccount(tenantWrapper.get().getId());
          TenantComposer.Composer tenantWrapper2 =
              tenantComposer
                  .forTenant(
                      tenantIsolationTestHelper.createTenantWithCurrentUser("additional_tenant_2"))
                  .persist();
          tenantIsolationTestHelper.switchToTenant(tenantWrapper2.get().getId(), entityManager);
          // executor in other tenant
          executorComposer.forExecutor(executorFixture.createOAEVExecutor()).persist();
          entityManager.flush();
          entityManager.clear();

          ensureServiceAccount(tenantWrapper2.get().getId());
          EndpointRegisterInput input =
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
          input.setAgentVersion(agentVersion);

          mockMvc
              .perform(
                  post("/api/tenants/" + tenantWrapper.get().getId() + "/endpoints/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn();
          entityManager.flush();
          entityManager.clear();

          mockMvc
              .perform(
                  post("/api/tenants/" + tenantWrapper2.get().getId() + "/endpoints/register")
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn();
          entityManager.flush();
          entityManager.clear();

          tenantIsolationTestHelper.switchToTenant(tenantWrapper.get().getId(), entityManager);
          List<AssetAgentJob> jobTenant1 = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobTenant1)
              .satisfiesOnlyOnce(job -> assertThat(job.getInject()).isNull())
              .satisfiesOnlyOnce(
                  job ->
                      assertThat(job.getTenant().getId()).isEqualTo(tenantWrapper.get().getId()));

          tenantIsolationTestHelper.switchToTenant(tenantWrapper2.get().getId(), entityManager);
          List<AssetAgentJob> jobTenant2 = fromIterable(assetAgentJobRepository.findAll());

          assertThat(jobTenant2)
              .satisfiesOnlyOnce(job -> assertThat(job.getInject()).isNull())
              .satisfiesOnlyOnce(
                  job ->
                      assertThat(job.getTenant().getId()).isEqualTo(tenantWrapper2.get().getId()));
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
      @DisplayName("When auto-update is enabled (default) and agent version matches server version")
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

    @Nested
    @DisplayName("Input sanitisation")
    class InputSanitisation {
      @Test
      @DisplayName("When registering an endpoint, bad hostname is rejected")
      void whenRegisteringAnEndpoint_badHostnameIsRejected() throws Exception {
        String maliciousStringInput = "1; \"evil command\".dot-com";
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointRegisterInput input =
            EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
        input.setHostname(maliciousStringInput);

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(
                () ->
                    mockMvc.perform(
                        post(ENDPOINT_URI + "/register")
                            .content(mapper.writeValueAsString(input))
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(csrf())))
            .hasCauseInstanceOf(ConstraintViolationException.class);
      }

      @Test
      @DisplayName("When creating an agentless endpoint, bad hostname is rejected")
      void whenCreatingAgentlessEndpoint_hostnameIsNormalised() throws Exception {
        String maliciousStringInput = "1; \"evil command\".dot-com";
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointInput input = EndpointRegisterInputFixture.getDefaultEndpointInput();
        input.setHostname(maliciousStringInput);

        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(
            post(ENDPOINT_URI + "/agentless")
                .content(mapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()));
        assertThatThrownBy(() -> entityManager.flush())
            .isInstanceOf(ConstraintViolationException.class);
      }

      @Test
      @DisplayName("When upserting an agentless endpoint, bad hostname is rejected")
      void whenUpsertingAgentlessEndpoint_hostnameIsNormalised() throws Exception {
        String maliciousStringInput = "1; \"evil command\".dot-com";
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointInput input = EndpointRegisterInputFixture.getDefaultEndpointInput();
        input.setHostname(maliciousStringInput);

        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(
            post(ENDPOINT_URI + "/agentless/upsert")
                .content(mapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()));

        assertThatThrownBy(() -> entityManager.flush())
            .isInstanceOf(ConstraintViolationException.class);
      }

      @Test
      @DisplayName("When setting IP addresses, invalid format is rejected")
      void invalidIpFormatIsRejected() throws Exception {
        String badIp = "bad IP address";
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointRegisterInput input =
            EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
        input.setIps(List.of(badIp).toArray(new String[0]));

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(
                () ->
                    mockMvc.perform(
                        post(ENDPOINT_URI + "/register")
                            .content(mapper.writeValueAsString(input))
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(csrf())))
            .hasCauseInstanceOf(ConstraintViolationException.class);
      }

      @Test
      @DisplayName("When setting seen IP address, input is overwritten")
      void invalidSeenIpInputOverwritten() throws Exception {
        String badIp = "bad IP address";
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointRegisterInput input =
            EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
        input.setSeenIp(badIp);

        entityManager.flush();
        entityManager.clear();

        mockMvc
            .perform(
                post(ENDPOINT_URI + "/register")
                    .content(mapper.writeValueAsString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk());

        List<Endpoint> endpoints = fromIterable(endpointRepository.findAll());

        assertThat(endpoints)
            .satisfiesOnlyOnce(ep -> assertThat(ep.getSeenIp()).isEqualTo("127.0.0.1"));
      }
    }
  }
}
