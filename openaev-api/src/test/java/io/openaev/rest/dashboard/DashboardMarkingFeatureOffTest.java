package io.openaev.rest.dashboard;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.security.EngineReadScope;
import io.openaev.service.engine.EngineReadScopeResolver;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * With the {@code MARKING} preview feature off, dashboards behave exactly as before the reader
 * filter: the tenant filter only. A read-only user holding {@code ACCESS_DASHBOARDS} alone still
 * finds an endpoint through the dashboard search, as {@code DashboardSearchApiTest} expected before
 * the filter.
 */
@Transactional
@WithMockUser(isAdmin = false)
@TestPropertySource(properties = "openaev.enabled-dev-features=")
@DisplayName("dashboards with the MARKING preview feature off")
class DashboardMarkingFeatureOffTest extends IntegrationTest {

  private static final String SEARCH_URI = TENANT_PREFIX + "/dashboards/search/{search}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private EngineReadScopeResolver scopeResolver;

  private String tenantId;
  private String endpointId;

  @BeforeEach
  void seedOneEndpoint() throws Exception {
    endpointComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
    tenantId =
        tenantHelper
            .createTenantWithCapabilities(
                "dashboard-flag-off", Set.of(Capability.ACCESS_DASHBOARDS))
            .getId();
    Endpoint endpoint = EndpointFixture.createEndpoint("flagoff-server");
    endpoint.setTenant(new Tenant(tenantId));
    endpointId = endpointComposer.forEndpoint(endpoint).persist().get().getId();

    entityManager.flush();
    entityManager.clear();
    engineService.bulkProcessing(engineContext.getModels().stream());
    // ES processes indexing asynchronously — give it time
    Thread.sleep(1_000);
  }

  @Test
  @DisplayName("a non-admin reader gets an unrestricted scope")
  void given_markingFeatureOff_should_resolveUnrestrictedScope() {
    // -- ACT --
    EngineReadScope scope = scopeResolver.resolve(TxCtx.forTenant(tenantId));

    // -- ASSERT --
    assertThat(scope).isEqualTo(EngineReadScope.unrestricted(null));
  }

  @Test
  @DisplayName("a user with ACCESS_DASHBOARDS only still finds an endpoint through the search")
  void given_markingFeatureOff_should_findEndpointWithoutAccessAssets() throws Exception {
    // -- ACT & ASSERT --
    mvc.perform(get(SEARCH_URI, tenantId, "flagoff"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].base_id").value(endpointId));
  }
}
