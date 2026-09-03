package io.openaev.rest.scenario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The frontend rewrites every /api/ call to /api/tenants/{tenantId}/..., so an endpoint that only
 * declares the legacy path answers 404 and the screen calling it never loads.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("ScenarioChallengesApi serves the player and observer reads on the tenant path")
class ScenarioChallengesApiTenantPathTest extends IntegrationTest {

  private static final String OBSERVER_CHALLENGES =
      "/api/tenants/{tenantId}/observer/scenarios/{scenarioId}/challenges";
  private static final String PLAYER_DOCUMENTS =
      "/api/tenants/{tenantId}/player/scenarios/{scenarioId}/documents";

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantId;
  private Scenario scenario;

  @BeforeEach
  void seedTenantAndScenario() throws Exception {
    scenarioComposer.reset();
    tenantId = tenantHelper.createTenantWithCurrentUser("scenario-challenges-tenant").getId();
    tenantHelper.switchToTenantNoFlush(tenantId);
    scenario = scenarioComposer.forScenario(ScenarioFixture.getScenario()).persist().get();
  }

  @ParameterizedTest
  @ValueSource(strings = {OBSERVER_CHALLENGES, PLAYER_DOCUMENTS})
  @DisplayName("the scenario challenge reads answer under the tenant path")
  void given_theTenantPath_then_readTheScenarioChallengeEndpoints(String uri) throws Exception {
    // -- ACT --
    ResultActions response = mvc.perform(get(uri, tenantId, scenario.getId()));

    // -- ASSERT --
    response.andExpect(status().is2xxSuccessful());
  }
}
