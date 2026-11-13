package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.fixtures.DocumentFixture.getDocumentJpeg;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Document;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Not {@code @Transactional}: a test transaction would keep the tenant scope alive while the
 * response is written, and hide the failure.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Launching a scenario whose inject has a document attached")
class ScenarioLaunchWithInjectDocumentTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private DocumentComposer documentComposer;

  private String tenant;

  @BeforeEach
  void setUp() throws Exception {
    scenarioComposer.reset();
    injectComposer.reset();
    documentComposer.reset();
    tenant = tenantHelper.createTenantWithCurrentUser("launch-with-document").getId();
  }

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(tenant);
  }

  @Test
  @DisplayName("given an inject with a document attached, should answer the new simulation")
  void given_injectWithAttachedDocument_should_answerTheLaunchedSimulation() throws Exception {
    // Arrange
    Scenario scenario = seedScenarioWithInjectDocument();

    // Act & Assert
    mvc.perform(
            post(TENANT_SCENARIO_URI + "/{scenarioId}/exercise/running", tenant, scenario.getId())
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exercise_injects.length()").value(1));
  }

  private Scenario seedScenarioWithInjectDocument() {
    Document document = getDocumentJpeg();
    document.setTenant(new Tenant(tenant));
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTenant(new Tenant(tenant));
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(tenant));
    return tenantTx.execute(
        TxCtx.forTenant(tenant),
        () ->
            scenarioComposer
                .forScenario(scenario)
                .withInject(
                    injectComposer
                        .forInject(inject)
                        .withDocument(documentComposer.forDocument(document)))
                .persist()
                .get());
  }
}
