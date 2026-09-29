package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=tags")
@WithMockUser(isAdmin = true)
@DisplayName("ScenarioApi serves scenario details on the tenant path when tags are v2-active")
class ScenarioApiTenantPathTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  @AfterEach
  void afterEach() {
    scenarioComposer.reset();
  }

  @Nested
  @DisplayName("GET " + TENANT_SCENARIO_URI + "/{scenarioId}")
  class GetScenario {

    @Test
    @DisplayName("given_scenarioInTenant_should_returnScenarioDetails")
    void given_scenarioInTenant_should_returnScenarioDetails() throws Exception {
      // Arrange
      String tenantId =
          tenantIsolationHelper.createTenantWithCurrentUser("scenario-tenant-path").getId();
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .persist()
              .get();

      // Act / Assert
      mvc.perform(get(TENANT_SCENARIO_URI + "/{scenarioId}", tenantId, scenario.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenario_id").value(scenario.getId()))
          .andExpect(jsonPath("$.scenario_name").value(scenario.getName()));
    }
  }
}
