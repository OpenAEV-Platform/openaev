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
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.fixtures.EndpointRegisterInputFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "executor.openaev.agent.auto-update-enabled=false")
class EndpointServiceAutoUpdateDisabledIntegrationTest extends IntegrationTest {

  private static final String MISMATCHED_AGENT_VERSION = "NOMATCH";

  @Autowired private MockMvc mockMvc;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectMapper mapper;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private AssetAgentJobRepository assetAgentJobRepository;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;

  @BeforeEach
  void setUp() {
    executorComposer.reset();
    ensureServiceAccount(TenantContext.getCurrentTenant());
  }

  private void ensureServiceAccount(String tenantId) {
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantId);
    entityManager.flush();
    entityManager.clear();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("When auto-update is disabled")
  class WhenAutoUpdateDisabled {

    @Test
    @DisplayName("Registering once with a mismatched version should not create an upgrade job")
    void given_versionMismatch_when_registeringOnce_should_notCreateUpgradeJob() throws Exception {
      executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
      EndpointRegisterInput input = EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
      input.setAgentVersion(MISMATCHED_AGENT_VERSION);

      entityManager.flush();
      entityManager.clear();

      mvcRegister(input);

      List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());
      assertThat(jobs).isEmpty();
    }

    @Test
    @DisplayName("Registering twice with a mismatched version should not create any upgrade job")
    void given_versionMismatch_when_registeringTwice_should_notCreateUpgradeJob() throws Exception {
      executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
      EndpointRegisterInput input = EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
      input.setAgentVersion(MISMATCHED_AGENT_VERSION);

      entityManager.flush();
      entityManager.clear();

      mvcRegister(input);
      mvcRegister(input);

      List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());
      assertThat(jobs).isEmpty();
    }

    @Test
    @DisplayName("Registering with matching version should not create an upgrade job")
    void given_versionMatch_when_registering_should_notCreateUpgradeJob() throws Exception {
      executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
      EndpointRegisterInput input = EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
      input.setAgentVersion(DEFAULT_ENDPOINT_AGENT_VERSION);

      entityManager.flush();
      entityManager.clear();

      mvcRegister(input);

      List<AssetAgentJob> jobs = fromIterable(assetAgentJobRepository.findAll());
      assertThat(jobs).isEmpty();
    }

    private void mvcRegister(EndpointRegisterInput input) throws Exception {
      mockMvc
          .perform(
              post(ENDPOINT_URI + "/register")
                  .content(mapper.writeValueAsString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }
  }
}
