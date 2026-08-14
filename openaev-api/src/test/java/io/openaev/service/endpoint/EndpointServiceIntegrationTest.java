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
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.fixtures.EndpointRegisterInputFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.mockUser.TestUserHolder;
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
  @Autowired private AssetAgentJobRepository assetAgentJobRepository;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TestUserHolder testUserHolder;

  private String tenantRegisterUri() {
    return ENDPOINT_URI + "/register";
  }

  @Autowired private EndpointRepository endpointRepository;

  @BeforeEach
  void setUp() {
    executorComposer.reset();
    tenantComposer.reset();
    // Link mock user to the default tenant so TxCtxArgumentResolver resolves a valid scope
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();
    ensureServiceAccount(Tenant.DEFAULT_TENANT_UUID);
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
                  post(tenantRegisterUri())
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
                  post(tenantRegisterUri())
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn();

          mockMvc
              .perform(
                  post(tenantRegisterUri())
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
                  post(tenantRegisterUri())
                      .content(mapper.writeValueAsString(input1))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());

          mockMvc
              .perform(
                  post(tenantRegisterUri())
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
                  post(tenantRegisterUri())
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
                  post(tenantRegisterUri())
                      .content(mapper.writeValueAsString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk());
          mockMvc
              .perform(
                  post(tenantRegisterUri())
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
      void whenCreatingAgentlessEndpoint_badHostnameIsRejected() throws Exception {
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
      void whenUpsertingAgentlessEndpoint_hostnameIsRejected() throws Exception {
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

    @Nested
    @DisplayName("Tunnel pseudo-interface MAC addresses")
    class TunnelPseudoInterfaceMacAddresses {

      /** Reported by every Windows host, so it must never take part in endpoint identification. */
      private static final String TEREDO_MAC = "00:00:00:00:00:00:00:E0";

      private EndpointRegisterInput hostInput(String hostname, String physicalMac) {
        EndpointRegisterInput input =
            EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
        input.setAgentVersion(DEFAULT_ENDPOINT_AGENT_VERSION);
        input.setExternalReference(UUID.randomUUID().toString());
        input.setName(hostname);
        input.setHostname(hostname);
        input.setMacAddresses(new String[] {physicalMac, TEREDO_MAC});
        return input;
      }

      private String register(EndpointRegisterInput input) throws Exception {
        String response =
            mockMvc
                .perform(
                    post(tenantRegisterUri())
                        .content(mapper.writeValueAsString(input))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.asset_id");
      }

      @Test
      @DisplayName("Two hosts sharing only a Teredo MAC register as two distinct endpoints")
      void given_twoHostsSharingOnlyTheTeredoMac_should_registerTwoDistinctEndpoints()
          throws Exception {
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointRegisterInput firstHost = hostInput("host-one", "00:ab:ad:c0:ff:e1");
        EndpointRegisterInput secondHost = hostInput("host-two", "00:ab:ad:c0:ff:e2");

        entityManager.flush();
        entityManager.clear();

        String firstAssetId = register(firstHost);
        String secondAssetId = register(secondHost);

        assertThat(secondAssetId).isNotEqualTo(firstAssetId);
      }

      @Test
      @DisplayName("Each host keeps a single agent carrying its own external reference")
      void given_twoHostsSharingOnlyTheTeredoMac_should_keepOneAgentPerHost() throws Exception {
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointRegisterInput firstHost = hostInput("host-one", "00:ab:ad:c0:ff:e1");
        EndpointRegisterInput secondHost = hostInput("host-two", "00:ab:ad:c0:ff:e2");

        entityManager.flush();
        entityManager.clear();

        String firstAssetId = register(firstHost);
        String secondAssetId = register(secondHost);
        entityManager.flush();
        entityManager.clear();

        Endpoint firstEndpoint = endpointRepository.findById(firstAssetId).orElseThrow();
        Endpoint secondEndpoint = endpointRepository.findById(secondAssetId).orElseThrow();

        assertThat(firstEndpoint.getAgents())
            .singleElement()
            .satisfies(
                agent ->
                    assertThat(agent.getExternalReference())
                        .isEqualTo(firstHost.getExternalReference()));
        assertThat(secondEndpoint.getAgents())
            .singleElement()
            .satisfies(
                agent ->
                    assertThat(agent.getExternalReference())
                        .isEqualTo(secondHost.getExternalReference()));
      }

      @Test
      @DisplayName("The Teredo MAC is not persisted on the endpoint")
      void given_aHostReportingTheTeredoMac_should_notPersistIt() throws Exception {
        executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
        EndpointRegisterInput host = hostInput("host-one", "00:ab:ad:c0:ff:e1");

        entityManager.flush();
        entityManager.clear();

        String assetId = register(host);
        entityManager.flush();
        entityManager.clear();

        Endpoint endpoint = endpointRepository.findById(assetId).orElseThrow();

        assertThat(endpoint.getMacAddresses()).containsExactly("00abadc0ffe1");
      }
    }
  }
}
