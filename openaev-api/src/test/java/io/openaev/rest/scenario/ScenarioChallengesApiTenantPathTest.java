package io.openaev.rest.scenario;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The frontend rewrites every /api/ call to /api/tenants/{tenantId}/..., so an endpoint that only
 * declares the legacy path answers 404 and the screen calling it never loads.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("ScenarioChallengesApi serves the player and observer reads on the tenant path")
class ScenarioChallengesApiTenantPathTest extends IntegrationTest {

  private static final String OBSERVER_CHALLENGES =
      "/api/tenants/{tenantId}/observer/scenarios/{scenarioId}/challenges";
  private static final String PLAYER_DOCUMENTS =
      "/api/tenants/{tenantId}/player/scenarios/{scenarioId}/documents";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @MockitoBean private ScenarioRepository scenarioRepository;

  private String tenantId;

  @BeforeEach
  void seedTenantAndScenario() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("scenario-challenges-tenant").getId();
    when(scenarioRepository.findByIdAndTenantId(anyString(), anyString()))
        .thenReturn(Optional.of(new Scenario()));
  }

  @Test
  @DisplayName("the observer challenges of a scenario are readable under the tenant path")
  void given_theTenantPath_then_readTheObserverChallenges() throws Exception {
    // -- ARRANGE --
    String scenarioId = UUID.randomUUID().toString();

    // -- ACT --
    ResultActions response = mvc.perform(get(OBSERVER_CHALLENGES, tenantId, scenarioId));

    // -- ASSERT --
    response.andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("the player documents of a scenario are readable under the tenant path")
  void given_theTenantPath_then_readThePlayerDocuments() throws Exception {
    // -- ARRANGE --
    String scenarioId = UUID.randomUUID().toString();

    // -- ACT --
    ResultActions response = mvc.perform(get(PLAYER_DOCUMENTS, tenantId, scenarioId));

    // -- ASSERT --
    response.andExpect(status().is2xxSuccessful());
  }
}
