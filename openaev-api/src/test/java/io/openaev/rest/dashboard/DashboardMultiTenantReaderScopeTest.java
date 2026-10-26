package io.openaev.rest.dashboard;

import static io.openaev.rest.dashboard.DashboardApi.DASHBOARD_URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.facade.EngineService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * One dashboard request can read several member tenants (the unprefixed routes take them from
 * {@code X-Tenant-Ids}), and the reader's capabilities differ per tenant. Each tenant's documents
 * must be filtered with the reader's rights in that tenant, not with those of the ambient (default)
 * tenant: a reader holding {@code ACCESS_ASSETS} and {@code ACCESS_ASSESSMENT} in the default
 * tenant only must not read tenant B's assets, nor tenant B's scenarios it holds no grant on.
 */
@Transactional
@WithMockUser(isAdmin = false)
@DisplayName("dashboard reads apply the reader's rights per tenant")
// es8 only until the reader filter is ported to es9 and OpenSearch (implementation-plan-dashboard
// steps 11-12): those engines ignore the scope, so the nightly would fail on them.
@DisabledIfEnvironmentVariable(named = "ENGINE_ENGINESELECTOR", matches = "elk9|opensearch")
class DashboardMultiTenantReaderScopeTest extends IntegrationTest {

  private static final String SEARCH_URI = DASHBOARD_URI + "/search/{search}";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;

  private String tenantB;
  private String defaultEndpointId;
  private String tenantBEndpointId;
  private String tenantBScenarioId;

  @BeforeEach
  void seedOneTenantWithRightsAndOneWithout() throws Exception {
    endpointComposer.reset();
    scenarioComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
    tenantHelper.grantCapabilitiesInTenant(
        Tenant.DEFAULT_TENANT_UUID,
        Set.of(
            Capability.ACCESS_DASHBOARDS, Capability.ACCESS_ASSETS, Capability.ACCESS_ASSESSMENT));
    tenantB =
        tenantHelper
            .createTenantWithCapabilities(
                "dashboard-reader-scope-b", Set.of(Capability.ACCESS_DASHBOARDS))
            .getId();

    defaultEndpointId = persistEndpoint(Tenant.DEFAULT_TENANT_UUID, "quokka-default");
    tenantBEndpointId = persistEndpoint(tenantB, "quokka-tenant-b");
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setName("quokka-scenario-b");
    scenario.setTenant(new Tenant(tenantB));
    tenantBScenarioId = scenarioComposer.forScenario(scenario).persist().get().getId();

    entityManager.flush();
    entityManager.clear();
    engineService.bulkProcessing(engineContext.getModels().stream());
    // ES processes indexing asynchronously — give it time
    Thread.sleep(1_000);
  }

  private String persistEndpoint(String tenantId, String name) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setTenant(new Tenant(tenantId));
    return endpointComposer.forEndpoint(endpoint).persist().get().getId();
  }

  @Test
  @DisplayName("control: the default tenant's asset is found with the default tenant's rights")
  void given_rightsInDefaultTenant_should_findItsAsset() throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(
            get(SEARCH_URI, "quokka")
                .header(TENANT_IDS_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.base_id=='" + defaultEndpointId + "')]").exists());
  }

  @Test
  @DisplayName("ACCESS_ASSETS in the default tenant does not reveal tenant B's assets")
  void given_accessAssetsInDefaultTenantOnly_should_notRevealTenantBAssets() throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(
            get(SEARCH_URI, "quokka")
                .header(TENANT_IDS_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.base_id=='" + tenantBEndpointId + "')]").doesNotExist());
  }

  @Test
  @DisplayName("ACCESS_ASSESSMENT in the default tenant does not bypass tenant B's grants")
  void given_accessAssessmentInDefaultTenantOnly_should_notRevealTenantBScenario()
      throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(
            get(SEARCH_URI, "quokka")
                .header(TENANT_IDS_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.base_id=='" + tenantBScenarioId + "')]").doesNotExist());
  }
}
